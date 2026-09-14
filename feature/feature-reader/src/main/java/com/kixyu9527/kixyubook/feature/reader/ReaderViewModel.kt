package com.kixyu9527.kixyubook.feature.reader

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.operation.UserOperationController
import com.kixyu9527.kixyubook.core.common.operation.LatestOperationWriter
import com.kixyu9527.kixyubook.core.common.configuration.*
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.common.diagnostics.toDiagnosticFailure
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.*
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureLevel
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureListener
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureRegistry
import com.kixyu9527.kixyubook.core.reader.engine.ReaderChapter
import com.kixyu9527.kixyubook.core.reader.engine.ReaderPositionManager
import com.kixyu9527.kixyubook.core.reader.engine.contentParagraphs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import kotlin.math.abs

@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val books: BookRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val fonts: FontRepository,
    private val stats: ReadingStatsRepository,
    private val cloudSync: CloudSyncCoordinator,
    private val textCorrections: TextCorrectionRepository,
    private val annotations: ReaderAnnotationRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val bookSettings: BookSettingsRepository = NoBookSettings,
) : ViewModel(), MemoryPressureListener {
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): ReaderViewModel
    }

    private val _uiState = MutableStateFlow(ReaderUiState(sessionId = UUID.randomUUID().toString()))
    val uiState = _uiState.asStateFlow()
    private val _positionState = MutableStateFlow(ReaderPositionState())
    val positionState = _positionState.asStateFlow()
    internal val contentState = _uiState
        .map(ReaderUiState::toReaderContentState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderUiState().toReaderContentState())
    private val sessionFinished = AtomicBoolean(false)
    private val sessionTimer = ReadingSessionTimer(SystemClock::elapsedRealtime)
    private var lastPosition = 0
    private var lastCharOffset = 0
    private val positions = ReaderPositionManager()
    private val chapterLoads = mutableMapOf<Int, ChapterLoadRequest>()
    private var chapterNavigationJob: Job? = null
    private var initialRetryJob: Job? = null
    private var chapterPrefetchJob: Job? = null
    private var criticalNeighborPublishJob: Job? = null
    private val criticalNeighbourJobs = mutableMapOf<Int, Job>()
    private val session = ReaderSessionCoordinator()
    private var prefetchedAroundChapterIndex: Int? = null
    private var pageInteractionActive = false
    private var acceptedProgressUpdatedAt = Long.MIN_VALUE
    private var latestLocalProgressWriteAt = Long.MIN_VALUE
    private var prioritySyncReady = false
    private var openingChapterId: Long? = null
    private var openingPosition = 0
    private var openingCharOffset = 0
    private var userMovedBeforePrioritySync = false
    private var deferredLocalProgress: ReadingProgress? = null
    private val progressWriter = ReaderProgressWriter(
        save = { progress ->
            books.saveProgress(progress)
            // Sync remains best-effort and tied to the visible reader. A late local write must
            // not reactivate a reader which onCleared has already released.
            viewModelScope.launch { cloudSync.prioritizeBook(bookUuid) }
        },
        onFailure = { error ->
            DiagnosticLog.record(
                Category.READER, "progress_checkpoint", outcome = "failed",
                details = mapOf("bookUuid" to bookUuid, "error" to error.toDiagnosticFailure()),
            )
        },
    )
    private val locationHistory = ReaderLocationHistory()
    private val locationJourney = ReaderJourneyTracker(SystemClock::elapsedRealtime) { source, elapsed, outcome ->
        DiagnosticLog.record(Category.READER, "location_ready", elapsedMs = elapsed, outcome = outcome,
            details = mapOf("source" to source.name))
    }
    // Natural page turns never went through a location request, so a chapter-boundary rollback left
    // no trace in the journal. Track the accepted turn through the target's render callback.
    private data class PendingPageTurn(
        val direction: Int,
        val source: String,
        val fromChapter: Int,
        val toChapter: Int,
        val startedAt: Long,
    )
    private var pendingPageTurn: PendingPageTurn? = null
    val operations = UserOperationController(viewModelScope) { error ->
        val failure = error.toDiagnosticFailure()
        DiagnosticLog.record(Category.READER, "reader_operation_failed", outcome = failure.outcome, details = mapOf("reason" to failure.reason))
    }

    private val annotationActions = ReaderAnnotationActions(bookUuid, _uiState, annotations, textCorrections, operations)
    private val settingWrites = LatestOperationWriter(viewModelScope, operations)
    private val settingRequests = com.kixyu9527.kixyubook.core.common.configuration.ReaderSettingsRequests()
    private val effectiveSettings = combine(settingsRepository.settings, bookSettings.overrides) { global, overrides ->
        applySettingsPatch(global, overrides[bookUuid])
    }.distinctUntilChanged()
    private val searchController = ReaderSearchController(
        resultDirectory = java.io.File(context.cacheDir, "reader-search-results"),
        scope = viewModelScope,
        bookUuid = bookUuid,
        books = books,
        state = _uiState,
        recordHistory = settingsRepository::addSearchHistory,
        recordOrigin = ::recordNavigationOrigin,
        jumpToPosition = ::jumpToPositionRaw,
        returnToOrigin = ::navigateHistoryBack,
        failureMessage = { context.getString(R.string.reader_error_search) },
    )

    // Authoritative in-memory toggle. The DataStore flow only confirms it later, so edits right
    // after enabling per-book settings must not read a stale value and fall back to the global copy.
    private var bookSettingsEnabled = false

    init {
        viewModelScope.launch {
            bookSettings.overrides.collect { overrides ->
                bookSettingsEnabled = bookUuid in overrides
                _uiState.update { it.copy(bookSettingsEnabled = bookSettingsEnabled) }
            }
        }
        MemoryPressureRegistry.register(this)
        cloudSync.prioritizeBook(bookUuid)
        viewModelScope.launch {
            // Initial loading already performs the first chapter/progress queries. Starting the
            // long-lived observers at the same time duplicated those reads. Settings and fonts
            // are also folded into that first atomic state publication so the destination does
            // not rebuild once for settings and again for content during its enter transition.
            loadInitial()
            launchReaderObserver("presentation") {
                combine(effectiveSettings, fonts.observeFonts()) { settings, fontList ->
                    Triple(
                        settings,
                        fontList.firstOrNull { it.uuid == settings.fontUuid }?.filePath,
                        fontList,
                    )
                }.collect { (settings, path, available) ->
                    _uiState.update { current ->
                        if (
                            current.settings == settings && current.fontPath == path &&
                            current.availableFonts == available && current.settingsLoaded
                        ) current else current.copy(
                            settings = settings,
                            settingsLoaded = true,
                            fontPath = path,
                            availableFonts = available,
                        )
                    }
                }
            }
            launchReaderObserver("search_history") {
                settingsRepository.searchHistory.collect { history ->
                    _uiState.update { current ->
                        if (current.searchHistory == history) current else current.copy(searchHistory = history)
                    }
                }
            }
            launchReaderObserver("chapters") {
                books.observeChapters(bookUuid).collect { chapters ->
                    if (chapters.isEmpty()) return@collect
                    val previous = _uiState.value
                    val orderingChanged = previous.chapters.map(Chapter::id) != chapters.map(Chapter::id)
                    if (orderingChanged) {
                        chapterNavigationJob?.cancel()
                        session.clear()
                        chapterPrefetchJob?.cancel()
                        criticalNeighborPublishJob?.cancel()
                        criticalNeighbourJobs.values.forEach(Job::cancel)
                        criticalNeighbourJobs.clear()
                        chapterLoads.values.forEach { it.deferred.cancel() }
                        chapterLoads.clear()
                        prefetchedAroundChapterIndex = null
                    }
                    _uiState.update { current ->
                        if (current.chapters == chapters) current else current.withChapters(chapters)
                    }
                    if (orderingChanged) {
                        prioritizeAdjacentChapter(_uiState.value.chapterIndex, -1)
                        prioritizeAdjacentChapter(_uiState.value.chapterIndex, 1)
                    }
                }
            }
            launchReaderObserver("bookmarks") {
                books.observeBookmarks(bookUuid).collect { bookmarks ->
                    _uiState.update { it.copy(bookmarks = bookmarks) }
                }
            }
            launchReaderObserver("corrections") {
                textCorrections.observeBookCorrections(bookUuid).collectLatest { corrections ->
                    applyCorrectionSnapshot(corrections)
                }
            }
            launchReaderObserver("annotations") {
                annotations.observeBookAnnotations(bookUuid).collect { values ->
                    _uiState.update { it.copy(annotations = values) }
                }
            }
            launchReaderObserver("progress") {
                books.observeProgress(bookUuid).filterNotNull().collect(::applySyncedProgress)
            }
            launchReaderObserver("priority_sync") {
                cloudSync.priorityBookSync
                    .filter { it.bookUuid == bookUuid }
                    .collect { priority ->
                        when (priority.phase) {
                            PriorityBookSyncPhase.READY -> finishPriorityProgressGate(syncSucceeded = true)
                            PriorityBookSyncPhase.ERROR -> finishPriorityProgressGate(syncSucceeded = false)
                            else -> Unit
                        }
                    }
            }
        }
    }

    /** One failing optional data stream must never cancel the reader's other live features. */
    private fun CoroutineScope.launchReaderObserver(
        name: String,
        collect: suspend () -> Unit,
    ) = launch {
        while (currentCoroutineContext().isActive) {
            try {
                collect()
                return@launch
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val failure = error.toDiagnosticFailure()
                DiagnosticLog.record(
                    Category.READER,
                    "reader_observer_failed",
                    outcome = failure.outcome,
                    details = mapOf(
                        "book" to bookUuid.take(8),
                        "observer" to name,
                        "reason" to failure.reason,
                    ),
                )
                delay(READER_OBSERVER_RETRY_MILLIS)
            }
        }
    }

    private suspend fun loadInitial() = runCatching {
        val initialData = coroutineScope {
            val book = async { books.getBook(bookUuid) }
            val chapters = async { books.observeChapters(bookUuid).first { it.isNotEmpty() } }
            val progress = async { books.observeProgress(bookUuid).first() }
            val presentation = async {
                combine(effectiveSettings, fonts.observeFonts()) { settings, fontList ->
                    InitialReaderPresentation(
                        settings = settings,
                        fontPath = fontList.firstOrNull { it.uuid == settings.fontUuid }?.filePath,
                        fonts = fontList,
                    )
                }.first()
            }
            InitialReaderData(book.await(), chapters.await(), progress.await(), presentation.await())
        }
        val book = initialData.book ?: error(context.getString(R.string.reader_error_missing_book))
        val chapters = initialData.chapters
        require(chapters.isNotEmpty()) { context.getString(R.string.reader_error_no_chapters) }
        val progress = initialData.progress
        acceptedProgressUpdatedAt = progress?.updatedTime ?: Long.MIN_VALUE
        val index = progress?.let { readerProgressChapterIndex(chapters, it).takeIf { index -> index >= 0 } } ?: 0
        _uiState.update {
            it.copy(
                book = book,
                chapters = chapters,
                settings = initialData.presentation.settings,
                settingsLoaded = true,
                fontPath = initialData.presentation.fontPath,
                availableFonts = initialData.presentation.fonts,
                loading = true,
                loadStage = ReaderLoadStage.READING_CONTENT,
                error = null,
            )
        }
        val content = chapterLoad(index, chapters, ChapterLoadPriority.USER).await() ?: error(context.getString(R.string.reader_error_chapter))
        val restoredLocation = requireNotNull(ReaderLocationRequest(
            index, progress?.paragraphIndex ?: 0, progress?.charOffset ?: 0,
            ReaderLocationSource.RESTORE, isChapterPosition = true, rememberOrigin = false,
        ).resolve(chapters))
        lastPosition = restoredLocation.paragraphIndex
        lastCharOffset = restoredLocation.charOffset
        openingChapterId = chapters[index].id
        openingPosition = lastPosition
        openingCharOffset = lastCharOffset
        _positionState.value = ReaderPositionState(lastPosition, lastCharOffset)
        _uiState.update {
            it.copy(
                book = book,
                chapters = chapters,
                chapter = content,
                prefetchedChapters = mapOf(index to content),
                chapterIndex = index,
                restorePosition = lastPosition,
                restoreCharOffset = lastCharOffset,
                settings = initialData.presentation.settings,
                settingsLoaded = true,
                fontPath = initialData.presentation.fontPath,
                availableFonts = initialData.presentation.fonts,
                loading = false,
                loadStage = ReaderLoadStage.PAGINATING_FIRST_PAGE,
                error = null,
            )
        }
        // A reader session owns a real previous/current/next chapter window. Both neighbours start
        // loading with the first visible chapter so opening a book at a saved position can move in
        // either direction immediately; pagination promotes them after the first leaf is visible.
        prioritizeAdjacentChapter(index, -1)
        prioritizeAdjacentChapter(index, 1)
        viewModelScope.launch {
            try {
                val navigation = books.readEpubNavigation(bookUuid)
                _uiState.update { it.copy(epubNavigation = navigation) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Publisher navigation is optional. The complete spine directory stays usable.
            }
        }
    }.onFailure { error ->
        if (error is CancellationException) throw error
        // A throwable without a message must still produce a visible failure surface, never a blank
        // reader with no retry affordance.
        _uiState.update {
            it.copy(loading = false, error = error.message ?: context.getString(R.string.reader_error_chapter))
        }
    }

    /** Clears an error that has already been surfaced without replacing the visible chapter. */
    fun clearTransientError() {
        _uiState.update { if (it.chapter != null) it.copy(error = null) else it }
    }

    fun retryInitialLoad() {
        if (_uiState.value.loading || initialRetryJob?.isActive == true) return
        chapterLoads.values.forEach { request ->
            if (!request.deferred.isCompleted) request.deferred.cancel()
        }
        chapterLoads.clear()
        initialRetryJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    loadStage = ReaderLoadStage.OPENING_BOOK,
                    error = null,
                )
            }
            loadInitial()
        }
    }

    fun moveChapter(delta: Int, openAtEnd: Boolean = false) {
        locationJourney.finish("superseded")
        val state = _uiState.value
        val baseIndex = session.pendingIndex ?: state.chapterIndex
        val target = (baseIndex + delta).coerceIn(0, state.chapters.lastIndex)
        beginPageTurn(delta, "chapter_button", state.chapterIndex, target)
        navigateToChapter(
            index = target,
            position = if (openAtEnd) Int.MAX_VALUE else 0,
        )
    }

    /**
     * Accept the latest settled pager target even while an earlier boundary chapter is loading.
     * Rapid swipes can legitimately settle several chapters ahead; cancelling the stale load and
     * promoting the newest target prevents the old pending chapter from pulling the pager back.
     */
    fun moveChapterFromPage(sourceChapterIndex: Int, delta: Int, openAtEnd: Boolean = false) {
        val state = _uiState.value
        if (state.chapterIndex != sourceChapterIndex) return
        locationJourney.finish("superseded")
        val target = (sourceChapterIndex + delta).coerceIn(0, state.chapters.lastIndex)
        beginPageTurn(delta, "page_boundary", sourceChapterIndex, target)
        navigateToChapter(
            index = target,
            position = if (openAtEnd) Int.MAX_VALUE else 0,
        )
    }

    fun jumpToChapter(index: Int) {
        requestLocation(ReaderLocationRequest(index, source = ReaderLocationSource.DIRECTORY, isChapterPosition = true))
    }

    /** Commit the leaf already shown by Pager without issuing another text-position jump. */
    fun settlePage(destination: ReaderPageDestination) {
        val state = _uiState.value
        if (state.chapterIndex != destination.sourceChapterIndex ||
            destination.chapterIndex == state.chapterIndex ||
            destination.chapterIndex !in state.chapters.indices
        ) return
        locationJourney.finish("superseded")
        beginPageTurn(
            direction = destination.chapterIndex - destination.sourceChapterIndex,
            source = "pager",
            fromChapter = destination.sourceChapterIndex,
            toChapter = destination.chapterIndex,
        )
        navigateToChapter(
            index = destination.chapterIndex,
            position = destination.paragraphIndex,
            charOffset = destination.charOffset,
            settledPageIndex = destination.pageIndex,
        )
    }

    fun saveParagraphCorrection(
        chapterIndex: Int,
        paragraphIndex: Int,
        displayedText: String,
        replacementText: String,
    ) = annotationActions.saveParagraphCorrection(chapterIndex, paragraphIndex, displayedText, replacementText)

    fun deleteCorrection(uuid: String) = annotationActions.deleteCorrection(uuid)

    fun saveParagraphHighlight(
        chapterIndex: Int,
        paragraphIndex: Int,
        displayedText: String,
        startOffset: Int,
        endOffset: Int,
    ) = annotationActions.saveParagraphHighlight(chapterIndex, paragraphIndex, displayedText, startOffset, endOffset)

    fun saveParagraphUnderline(
        chapterIndex: Int,
        paragraphIndex: Int,
        displayedText: String,
        startOffset: Int,
        endOffset: Int,
    ) = annotationActions.saveParagraphUnderline(chapterIndex, paragraphIndex, displayedText, startOffset, endOffset)

    fun saveParagraphNote(
        chapterIndex: Int,
        paragraphIndex: Int,
        displayedText: String,
        startOffset: Int,
        endOffset: Int,
        note: String,
    ) = annotationActions.saveParagraphNote(chapterIndex, paragraphIndex, displayedText, startOffset, endOffset, note)

    fun deleteAnnotation(uuid: String) = annotationActions.deleteAnnotation(uuid)

    fun updateAnnotationNote(uuid: String, note: String) = annotationActions.updateAnnotationNote(uuid, note)

    /**
     * Corrections can change from the editor, the management destination, or cloud sync. Keep
     * this observer as the single cache-invalidation path so every source immediately restores
     * the immutable chapter text (or applies its new overlay) without reopening the reader.
     */
    private suspend fun applyCorrectionSnapshot(corrections: List<TextCorrection>) {
        val snapshot = _uiState.value
        val affectedPositions = changedCorrectionChapterPositions(
            previous = snapshot.corrections,
            current = corrections,
            chapters = snapshot.chapters,
        )
        _uiState.update { state ->
            state.copy(
                corrections = corrections,
                prefetchedChapters = state.prefetchedChapters - affectedPositions,
            )
        }
        if (affectedPositions.isEmpty()) return

        // A neighbour prefetch may already hold a chapter with the previous correction overlay.
        // Cancel the window before clearing individual requests so it cannot publish stale text
        // after the database observer has delivered the newer correction set.
        chapterPrefetchJob?.cancel()
        chapterPrefetchJob = null
        prefetchedAroundChapterIndex = null
        affectedPositions.forEach { position ->
            chapterLoads.remove(position)?.deferred?.cancel()
        }

        val currentPosition = _uiState.value.chapterIndex
        if (currentPosition in affectedPositions) {
            reloadCorrectedChapter(currentPosition)
        } else if (!pageInteractionActive) {
            val state = _uiState.value
            if (state.chapter != null) prefetchNearbyChapters(state.chapterIndex, state.chapters)
        }
        // Corrected text can add or remove hits; re-run the active query so the result list and its
        // occurrence counts are never shown against stale text.
        searchController.invalidate()
    }

    private suspend fun reloadCorrectedChapter(position: Int) {
        val target = _uiState.value.chapters.getOrNull(position) ?: return
        chapterLoads.remove(position)?.deferred?.cancel()
        val refreshed = books.getChapter(bookUuid, target.index)?.toReaderChapter() ?: return
        val visiblePosition = _positionState.value
        var adjustedPosition: ReaderPositionState? = null
        _uiState.update { state ->
            if (state.chapterIndex != position || state.chapters.getOrNull(position)?.id != target.id) {
                state
            } else {
                // Correction changes are an in-place repagination, not chapter navigation.
                // restorePosition intentionally remains at the chapter-entry destination during
                // ordinary reading, so using it here jumps a later page back to page one. Promote
                // the currently visible anchor before rebuilding the pager and clamp its offset
                // when an undo restores a shorter source paragraph.
                val anchorParagraph = refreshed.contentParagraphs().firstOrNull {
                    it.index == visiblePosition.paragraphIndex
                }
                val anchorPosition = anchorParagraph?.index ?: visiblePosition.paragraphIndex
                val anchorOffset = visiblePosition.charOffset.coerceIn(
                    0,
                    anchorParagraph?.text?.length ?: 0,
                )
                adjustedPosition = ReaderPositionState(anchorPosition, anchorOffset)
                state.copy(
                    chapter = refreshed,
                    prefetchedChapters = mapOf(position to refreshed),
                    restorePosition = anchorPosition,
                    restoreCharOffset = anchorOffset,
                    navigationVersion = state.navigationVersion + 1,
                    settledPageIndex = null,
                )
            }
        }
        adjustedPosition?.let { _positionState.value = it }
    }

    private fun navigateToChapter(
        index: Int,
        position: Int,
        charOffset: Int = 0,
        persistProgress: Boolean = true,
        settledPageIndex: Int? = null,
    ) {
        val state = _uiState.value
        if (index == state.chapterIndex && state.chapter != null) {
            chapterNavigationJob?.cancel()
            session.clear()
            applyPositionWithinCurrentChapter(position, charOffset, persistProgress)
            return
        }
        chapterNavigationJob?.cancel()
        chapterPrefetchJob?.cancel()
        criticalNeighborPublishJob?.cancel()
        criticalNeighbourJobs.values.forEach(Job::cancel)
        criticalNeighbourJobs.clear()
        prefetchedAroundChapterIndex = null
        cancelPendingChapterLoadsExcept(index)

        // A paged boundary already renders the decoded adjacent chapter. Publishing the same
        // chapter through a new coroutine leaves a short interval in which the old Pager is at
        // its terminal item but the replacement Pager does not exist yet. With one-page EPUB
        // chapters a second fast gesture lands in that interval and visibly springs back.
        // Commit an in-memory neighbour synchronously so the next gesture always reaches the
        // newly active Pager.
        state.prefetchedChapters[index]?.let { cached ->
            session.clear()
            activateChapter(index, position, charOffset, cached, persistProgress, settledPageIndex)
            return
        }

        val token = session.begin(index)
        _uiState.update {
            it.copy(
                error = null,
            )
        }
        chapterNavigationJob = viewModelScope.launch {
            try {
                loadChapter(index, position, charOffset, persistProgress, settledPageIndex)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session.isCurrent(token)) {
                    locationJourney.failed(index)
                    completePageTurn(index, "failed")
                    _uiState.update { it.copy(error = error.message ?: context.getString(R.string.reader_error_chapter)) }
                }
            } finally {
                session.finish(token)
            }
        }
    }

    private suspend fun loadChapter(
        index: Int,
        position: Int,
        charOffset: Int,
        persistProgress: Boolean,
        settledPageIndex: Int? = null,
    ): Boolean {
        val startedAt = SystemClock.elapsedRealtime()
        val currentState = _uiState.value
        if (index == currentState.chapterIndex && currentState.chapter != null) return true
        if (currentState.chapters.getOrNull(index) == null) return false
        // Keep the current chapter rendered while the target is read. Removing the
        // reader from composition here left a blank screen when a search jump was slow
        // or its chapter index was not contiguous.
        val prefetched = currentState.prefetchedChapters[index]
        val readerChapter = prefetched
            ?: chapterLoad(index, currentState.chapters, ChapterLoadPriority.USER).await()
        if (readerChapter == null) {
            completePageTurn(index, "missing")
            DiagnosticLog.record(
                Category.READER,
                "chapter_navigation_finished",
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                outcome = "missing",
                details = mapOf("book" to bookUuid.take(8), "chapter" to index),
            )
            return false
        }
        activateChapter(index, position, charOffset, readerChapter, persistProgress, settledPageIndex)
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        if (prefetched == null || elapsedMs >= SLOW_NAVIGATION_MS) {
            DiagnosticLog.record(
                Category.READER,
                "chapter_navigation_finished",
                elapsedMs = elapsedMs,
                outcome = "success",
                details = mapOf(
                    "book" to bookUuid.take(8),
                    "chapter" to index,
                    "prefetched" to (prefetched != null),
                ),
            )
        }
        return true
    }

    private fun activateChapter(
        index: Int,
        position: Int,
        charOffset: Int,
        readerChapter: ReaderChapter,
        persistProgress: Boolean,
        settledPageIndex: Int? = null,
    ) {
        lastPosition = if (position == Int.MAX_VALUE) readerChapter.contentParagraphs().lastOrNull()?.index ?: 0 else position
        lastCharOffset = if (position == Int.MAX_VALUE) Int.MAX_VALUE else charOffset.coerceAtLeast(0)
        _positionState.value = ReaderPositionState(lastPosition, lastCharOffset)
        _uiState.update {
            it.copy(
                chapter = readerChapter,
                prefetchedChapters = (it.prefetchedChapters + (index to readerChapter))
                    .filterKeys { chapterIndex -> abs(chapterIndex - index) <= RENDER_PREFETCH_RADIUS },
                chapterIndex = index,
                restorePosition = lastPosition,
                restoreCharOffset = lastCharOffset,
                navigationVersion = it.navigationVersion + 1,
                settledPageIndex = settledPageIndex,
                loading = false,
                error = null,
            )
        }
        // Rotate the same three-chapter window after activation. Cached neighbours are published
        // synchronously, while missing ones are decoded outside the page-turn animation.
        prioritizeAdjacentChapter(index, -1)
        prioritizeAdjacentChapter(index, 1)
        if (persistProgress) savePosition(lastPosition, lastCharOffset)
    }

    private fun applyPositionWithinCurrentChapter(
        position: Int,
        charOffset: Int,
        persistProgress: Boolean,
    ) {
        val chapter = _uiState.value.chapter ?: return
        lastPosition = if (position == Int.MAX_VALUE) {
            chapter.contentParagraphs().lastOrNull()?.index ?: 0
        } else {
            position.coerceAtLeast(0)
        }
        lastCharOffset = if (position == Int.MAX_VALUE) Int.MAX_VALUE else charOffset.coerceAtLeast(0)
        _positionState.value = ReaderPositionState(lastPosition, lastCharOffset)
        _uiState.update { current ->
            current.copy(
                restorePosition = lastPosition,
                restoreCharOffset = lastCharOffset,
                navigationVersion = current.navigationVersion + 1,
                settledPageIndex = null,
            )
        }
        if (persistProgress) savePosition(lastPosition, lastCharOffset)
    }

    private fun applySyncedProgress(progress: ReadingProgress) {
        if (_uiState.value.loading || !shouldApplySyncedProgress(
                incomingUpdatedAt = progress.updatedTime,
                acceptedUpdatedAt = acceptedProgressUpdatedAt,
                latestLocalWriteAt = latestLocalProgressWriteAt,
            )
        ) return
        val state = _uiState.value
        val targetIndex = readerProgressChapterIndex(state.chapters, progress)
        // Only mark the update as accepted once it can actually be applied. Advancing the watermark
        // for an unresolvable chapter would suppress a later valid update.
        if (targetIndex < 0) return
        acceptedProgressUpdatedAt = progress.updatedTime
        val targetPosition = progress.paragraphIndex.coerceAtLeast(0)
        val targetCharOffset = progress.charOffset.coerceAtLeast(0)
        val currentPosition = _positionState.value
        if (
            targetIndex == state.chapterIndex &&
            targetPosition == currentPosition.paragraphIndex &&
            targetCharOffset == currentPosition.charOffset
        ) return

        if (targetIndex == state.chapterIndex && state.chapter != null) {
            chapterNavigationJob?.cancel()
            session.clear()
            lastPosition = targetPosition
            lastCharOffset = targetCharOffset
            _positionState.value = ReaderPositionState(targetPosition, targetCharOffset)
            _uiState.update { current ->
                current.copy(
                    restorePosition = targetPosition,
                    restoreCharOffset = targetCharOffset,
                    navigationVersion = current.navigationVersion + 1,
                    settledPageIndex = null,
                )
            }
        } else {
            requestLocation(ReaderLocationRequest(targetIndex, targetPosition, targetCharOffset,
                ReaderLocationSource.RESTORE, isChapterPosition = true, rememberOrigin = false))
        }
    }

    private suspend fun finishPriorityProgressGate(syncSucceeded: Boolean) {
        if (prioritySyncReady) return
        if (syncSucceeded) {
            // Room and coordinator are independent flows. Read once after the engine completes so
            // a fast remote write cannot be missed between observer registration and READY.
            books.observeProgress(bookUuid).first()?.let(::applySyncedProgress)
        }
        prioritySyncReady = true
        val pending = deferredLocalProgress
        deferredLocalProgress = null
        if (userMovedBeforePrioritySync && pending != null) {
            persistProgress(pending.copy(updatedTime = nextProgressUpdatedAt(System.currentTimeMillis(), latestLocalProgressWriteAt)))
        }
    }

    private fun chapterLoad(
        index: Int,
        chapters: List<Chapter>,
        priority: ChapterLoadPriority,
    ): Deferred<ReaderChapter?> {
        val existing = chapterLoads[index]
        if (existing != null && !existing.deferred.isCancelled) {
            // A completed failure must not be cached: retrying the same chapter has to start a new
            // load instead of rethrowing the old exception forever.
            val failed = existing.deferred.isCompleted &&
                runCatching { existing.deferred.getCompleted() }.isFailure
            val promotesSpeculativeLoad = priority != ChapterLoadPriority.PREFETCH &&
                existing.priority == ChapterLoadPriority.PREFETCH
            val promotesReadAheadToUser = priority == ChapterLoadPriority.USER &&
                existing.priority == ChapterLoadPriority.READ_AHEAD
            if (!failed && !(promotesSpeculativeLoad || promotesReadAheadToUser)) {
                return existing.deferred
            }
            if (!failed) {
                // Current content must never inherit speculative scheduling. The prefetch parser
                // may still be unwinding a difficult XHTML file, but parsing happens outside the
                // repository commit lock so this USER request can overtake it immediately.
                existing.deferred.cancel()
            }
            chapterLoads.remove(index)
        }
        return viewModelScope.async {
            val target = chapters.getOrNull(index) ?: return@async null
            books.getChapter(bookUuid, target.index, priority)?.toReaderChapter()
        }.also { deferred ->
            chapterLoads[index] = ChapterLoadRequest(priority, deferred)
        }
    }

    private fun prefetchNearbyChapters(index: Int, chapters: List<Chapter>) {
        chapterPrefetchJob?.cancel()
        val loads = chapterLoads.iterator()
        while (loads.hasNext()) {
            val (chapterIndex, request) = loads.next()
            if (abs(chapterIndex - index) > CHAPTER_PREFETCH_RADIUS) {
                if (!request.deferred.isCompleted) request.deferred.cancel()
                loads.remove()
            }
        }
        val nearbyIndices = (1..CHAPTER_PREFETCH_RADIUS)
            // Most reading proceeds forward, so decode the next chapter before the previous one.
            // The repository's persistent binary cache and background index own the wider window;
            // retaining it here duplicated full paragraph graphs and text-layout work in memory.
            .flatMap { distance -> listOf(index + distance, index - distance) }
            .filter { it in chapters.indices }
        // One sequential job avoids competing EPUB parses at reader entry. The
        // visible chapter has already rendered when this starts, and a user jump cancels the job.
        chapterPrefetchJob = viewModelScope.launch {
            nearbyIndices.forEach { nearbyIndex ->
                val load = chapterLoad(nearbyIndex, chapters, ChapterLoadPriority.PREFETCH)
                val chapter = load.await() ?: return@forEach
                if (abs(nearbyIndex - index) <= RENDER_PREFETCH_RADIUS) {
                    _uiState.update { state ->
                        if (abs(state.chapterIndex - nearbyIndex) > RENDER_PREFETCH_RADIUS) state else state.copy(
                            prefetchedChapters = state.prefetchedChapters + (nearbyIndex to chapter),
                        )
                    }
                }
                kotlinx.coroutines.yield()
            }
        }
    }

    /**
     * Prepare one side of the reader's previous/current/next navigation window.
     *
     * These two immediate neighbours are navigation state, not speculative indexing. Their loads
     * therefore survive page drags and overlay animation, while the wider prefetch queue still
     * yields the frame budget. TXT and EPUB both enter the reader through this same chapter API;
     * only their repository parsing strategy differs.
     */
    fun prioritizeAdjacentChapter(sourceChapterIndex: Int, direction: Int) {
        val step = when {
            direction < 0 -> -1
            direction > 0 -> 1
            else -> return
        }
        val state = _uiState.value
        if (state.chapterIndex != sourceChapterIndex) return
        val targetIndex = sourceChapterIndex + step
        if (targetIndex !in state.chapters.indices || targetIndex in state.prefetchedChapters) return
        if (criticalNeighbourJobs[targetIndex]?.isActive == true) return

        criticalNeighbourJobs.remove(targetIndex)?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val chapter = chapterLoad(
                    targetIndex,
                    state.chapters,
                    ChapterLoadPriority.READ_AHEAD,
                ).await() ?: return@launch
                _uiState.update { current ->
                    if (
                        current.chapterIndex != sourceChapterIndex ||
                        targetIndex !in current.chapters.indices
                    ) {
                        current
                    } else {
                        current.copy(
                            prefetchedChapters = current.prefetchedChapters +
                                (targetIndex to chapter),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                val currentJob = currentCoroutineContext()[Job]
                if (criticalNeighbourJobs[targetIndex] === currentJob) {
                    criticalNeighbourJobs.remove(targetIndex)
                }
            }
        }
        criticalNeighbourJobs[targetIndex] = job
        job.start()
    }

    /**
     * Directory and search jumps are foreground work. Cancel queued speculative reads so a
     * distant chapter never waits behind the previous chapter's twenty-item prefetch window.
     * A load for the requested chapter is retained when it was already prefetched.
     */
    private fun cancelPendingChapterLoadsExcept(targetIndex: Int) {
        val loads = chapterLoads.iterator()
        while (loads.hasNext()) {
            val (chapterIndex, request) = loads.next()
            if (chapterIndex != targetIndex && !request.deferred.isCompleted) {
                request.deferred.cancel()
                loads.remove()
            }
        }
    }

    fun jumpToPosition(chapterIndex: Int, position: Int) {
        requestLocation(ReaderLocationRequest(chapterIndex, position, source = ReaderLocationSource.BOOKMARK))
    }

    private fun jumpToPositionRaw(chapterIndex: Int, position: Int, charOffset: Int) {
        requestLocation(
            ReaderLocationRequest(
                chapterIndex = chapterIndex,
                paragraphIndex = position,
                charOffset = charOffset,
                source = ReaderLocationSource.SEARCH,
                rememberOrigin = false,
            ),
        )
    }

    internal fun requestLocation(request: ReaderLocationRequest) {
        val target = request.resolve(_uiState.value.chapters) ?: return
        // An explicit destination supersedes any natural page turn still waiting to render.
        pendingPageTurn = null
        if (target != currentLocation()) {
            locationJourney.begin(request.source, target.chapterPosition, _uiState.value.navigationVersion)
        }
        if (request.rememberOrigin) {
            locationHistory.record(currentLocation(), target)
            publishLocationHistoryState()
        }
        navigateToChapter(target.chapterPosition, target.paragraphIndex, target.charOffset,
            persistProgress = request.source != ReaderLocationSource.RESTORE)
    }

    fun navigateHistoryBack() {
        val target = locationHistory.goBack(currentLocation()) ?: return
        publishLocationHistoryState()
        requestLocation(ReaderLocationRequest(target.chapterPosition, target.paragraphIndex, target.charOffset,
            ReaderLocationSource.HISTORY, isChapterPosition = true, rememberOrigin = false))
    }

    fun navigateHistoryForward() {
        val target = locationHistory.goForward(currentLocation()) ?: return
        publishLocationHistoryState()
        requestLocation(ReaderLocationRequest(target.chapterPosition, target.paragraphIndex, target.charOffset,
            ReaderLocationSource.HISTORY, isChapterPosition = true, rememberOrigin = false))
    }

    fun openEpubLink(target: String) {
        viewModelScope.launch {
            when (val result = books.resolveEpubLink(bookUuid, target)) {
                is EpubLinkResult.Footnote -> _uiState.update { it.copy(epubFootnote = result) }
                is EpubLinkResult.Location -> {
                    requestLocation(ReaderLocationRequest(result.chapterIndex, result.paragraphIndex, source = ReaderLocationSource.DOCUMENT_LINK))
                }
                null -> Unit
            }
        }
    }

    fun closeEpubFootnote() {
        _uiState.update { it.copy(epubFootnote = null) }
    }

    private fun recordNavigationOrigin(chapterIndex: Int, paragraphIndex: Int) {
        val target = ReaderLocationRequest(chapterIndex, paragraphIndex, source = ReaderLocationSource.SEARCH)
            .resolve(_uiState.value.chapters) ?: return
        locationHistory.record(
            origin = currentLocation(),
            destination = target,
        )
        publishLocationHistoryState()
    }

    private fun currentLocation(): ReaderLocation {
        val position = _positionState.value
        return ReaderLocation(
            chapterPosition = _uiState.value.chapterIndex,
            paragraphIndex = position.paragraphIndex,
            charOffset = position.charOffset,
        )
    }

    /**
     * Records a natural page turn once the target chapter is actually on screen. Backward turns are
     * the ones that used to spring back, so direction and source make a rollback visible in order.
     */
    private fun beginPageTurn(direction: Int, source: String, fromChapter: Int, toChapter: Int) {
        if (direction == 0 || fromChapter == toChapter) return
        pendingPageTurn = PendingPageTurn(
            direction = if (direction < 0) -1 else 1,
            source = source,
            fromChapter = fromChapter,
            toChapter = toChapter,
            startedAt = SystemClock.elapsedRealtime(),
        )
    }

    private fun completePageTurn(chapter: Int, outcome: String) {
        val pending = pendingPageTurn ?: return
        if (pending.toChapter != chapter) return
        pendingPageTurn = null
        DiagnosticLog.record(
            Category.READER,
            "page_turn",
            elapsedMs = (SystemClock.elapsedRealtime() - pending.startedAt).coerceAtLeast(0),
            outcome = outcome,
            details = mapOf(
                "direction" to if (pending.direction < 0) "backward" else "forward",
                "source" to pending.source,
                "fromChapter" to pending.fromChapter,
                "chapter" to chapter,
            ),
        )
    }

    private fun publishLocationHistoryState() {
        _uiState.update {
            it.copy(
                canNavigateBack = locationHistory.canGoBack,
                canNavigateForward = locationHistory.canGoForward,
            )
        }
    }

    fun savePosition(
        position: Int,
        charOffset: Int = 0,
        chapterComplete: Boolean = false,
        visibleEndPosition: Int = position,
    ) {
        val state = _uiState.value
        val chapter = state.chapter ?: return
        val content = chapter.contentParagraphs()
        val paragraphOffset = content.indexOfLast { it.index <= position }.coerceAtLeast(0)
        val paragraph = content.getOrNull(paragraphOffset)
        val safePosition = paragraph?.index ?: 0
        val safeCharOffset = charOffset.coerceIn(0, paragraph?.text?.length ?: 0)
        val safeVisibleEnd = content.lastOrNull { it.index <= visibleEndPosition }?.index
            ?.coerceAtLeast(safePosition)
            ?: safePosition
        lastPosition = safePosition
        lastCharOffset = safeCharOffset
        _positionState.value = ReaderPositionState(safePosition, safeCharOffset, safeVisibleEnd)
        val total = positions.bookFraction(
            chapterIndex = state.chapterIndex,
            chapterCount = state.chapters.size,
            paragraphOffset = paragraphOffset,
            paragraphCount = content.size,
            chapterComplete = chapterComplete,
        )
        val progress = ReadingProgress(
            bookUuid,
            chapter.id,
            safePosition,
            offset = safeCharOffset,
            updatedTime = nextProgressUpdatedAt(System.currentTimeMillis(), latestLocalProgressWriteAt),
            fraction = total,
            paragraphIndex = safePosition,
            charOffset = safeCharOffset,
            chapterKey = state.chapters.getOrNull(state.chapterIndex)?.chapterKey.orEmpty(),
        )
        val moved = hasReaderMovedFromOpening(
            openingChapterId = openingChapterId,
            openingPosition = openingPosition,
            openingCharOffset = openingCharOffset,
            currentChapterId = chapter.id,
            currentPosition = safePosition,
            currentCharOffset = safeCharOffset,
        )
        if (!prioritySyncReady) {
            if (moved) {
                userMovedBeforePrioritySync = true
                deferredLocalProgress = progress
                // User movement is durable immediately. Keeping it only in this ViewModel loses
                // the latest chapter when the destination is popped before the priority pull
                // completes. The deferred copy is retained so the active reader wins again after
                // that pull, while the DAO rejects any delayed older write.
                persistProgress(progress)
            }
            return
        }
        persistProgress(progress)
    }

    private fun persistProgress(progress: ReadingProgress) {
        // These timestamps suppress stale sync echoes; progressWriter tracks actual completion.
        latestLocalProgressWriteAt = progress.updatedTime
        acceptedProgressUpdatedAt = maxOf(acceptedProgressUpdatedAt, progress.updatedTime)
        progressWriter.submit(progress)
    }

    fun checkpointReadingProgress() {
        progressWriter.checkpoint()
    }

    /** HyperOS can ask for a process checkpoint immediately before enforcing its memory budget. */
    override fun onMemoryPressure(level: MemoryPressureLevel) {
        if (level != MemoryPressureLevel.CRITICAL) return
        // The registry always dispatches off the main thread (the vendor receiver and the Android
        // trim callbacks both run on a background HandlerThread). Keep a firm deadline so the
        // complete TRIM/KILL response remains inside HyperOS's three-second limit.
        val saved = runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(PROGRESS_CHECKPOINT_TIMEOUT_MS) {
                progressWriter.flush()
            }
        }
        check(saved == true) { "Reading progress checkpoint failed or exceeded its deadline" }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val current = _uiState.value.settings
        // Record the scope at submit time. The mode toggle is applied synchronously, so an edit
        // made while per-book settings are on targets this book even if the user turns the mode
        // off before the debounced write runs. The write must not be reinterpreted later.
        val local = bookSettingsEnabled
        settingRequests.changes(current, transform).forEach { (field, patch) ->
            settingWrites.submit("reader:$field") {
                if (local && field in BOOK_SETTING_KEYS) {
                    bookSettings.updateField(bookUuid, field, patch)
                } else {
                    settingsRepository.update { applySettingsPatch(it, patch) }
                }
            }
        }
    }

    fun setBookSettingsEnabled(enabled: Boolean) {
        // Apply in memory before the persisted write so an immediate edit sees the new mode.
        bookSettingsEnabled = enabled
        _uiState.update { it.copy(bookSettingsEnabled = enabled) }
        settingRequests.clear()
        settingWrites.submit("bookProfile") { bookSettings.setEnabled(bookUuid, enabled) }
    }

    fun importFont(uri: String) = operations.submit { fonts.importFont(uri).getOrThrow() }

    fun deleteFont(font: UserFont) = operations.confirmDelete(font.name) {
        bookSettings.clearFontReferences(font.uuid)
        fonts.deleteFont(font.uuid)
    }

    fun addBookmark() {
        val state = _uiState.value
        val chapter = state.chapter ?: return
        val position = lastPosition
        val preview = chapter.contentParagraphs()
            .firstOrNull { it.index >= position && it.kind == ParagraphKind.TEXT }
            ?.text
            ?.replace(Regex("\\s+"), " ")
            ?.take(80)
            .orEmpty()
        val request = Bookmark(
                uuid = java.util.UUID.randomUUID().toString(),
                bookUuid = bookUuid,
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                chapterIndex = chapter.index,
                position = position,
                preview = preview,
                createdTime = System.currentTimeMillis(),
            )
        // Retry must repeat the original user intent, even after the reader has moved on.
        operations.submit { books.addBookmark(request) }
    }

    fun deleteBookmark(uuid: String) = operations.confirmDelete(_uiState.value.bookmarks.firstOrNull { it.uuid == uuid }?.preview?.take(80)) { books.deleteBookmark(uuid) }

    fun search(query: String, scope: ReaderSearchScope) = searchController.search(query, scope)

    fun selectSearchResult(index: Int) = searchController.select(index)

    fun returnFromSearchResult() = searchController.returnToReadingPosition()

    fun moveSearchResult(delta: Int) = searchController.move(delta)
    fun moveSearchMatch(delta: Int) = searchController.moveMatch(delta)
    fun moveSearchResultPage(delta: Int) = searchController.movePage(delta)

    fun clearSearch() = searchController.clear()

    fun clearSearchHistory() {
        _uiState.update { it.copy(searchHistory = emptyList()) }
        viewModelScope.launch { settingsRepository.clearSearchHistory() }
    }

    /** Counts only time during which this reader destination is resumed with readable content. */
    fun setReadingActive(active: Boolean) {
        if (sessionFinished.get()) {
            // The scene may be disposed (e.g. a nested route on top) and composed again while the
            // ViewModel survives. Re-activating starts a fresh session instead of staying latched.
            if (!active) return
            sessionFinished.set(false)
        }
        sessionTimer.setActive(active)
    }

    /** Suspends full-book indexing while retaining on-demand current and neighbour chapter loads. */
    fun setReaderVisible(visible: Boolean) {
        books.setReaderSessionActive(visible)
    }

    /**
     * A drag or animated page turn owns the frame budget. Keep already prepared neighbours, but
     * stop the remaining neighbouring speculative queue until the pager settles. This mirrors the
     * interaction freeze used by mature web-reader preloaders and prevents EPUB parsing from
     * causing intermittent missed frames on otherwise fast devices.
     */
    fun setPageInteractionActive(active: Boolean) {
        if (pageInteractionActive == active) return
        pageInteractionActive = active
        books.setReaderInteractionActive(active)
        if (active) {
            chapterPrefetchJob?.cancel()
            chapterPrefetchJob = null
            publishInFlightNavigationWindow()
            prefetchedAroundChapterIndex = null
            return
        }
        criticalNeighborPublishJob?.cancel()
        criticalNeighborPublishJob = null
        val state = _uiState.value
        if (state.chapter != null && session.pendingIndex == null) {
            prefetchedAroundChapterIndex = state.chapterIndex
            prefetchNearbyChapters(state.chapterIndex, state.chapters)
        }
    }

    /**
     * Stopping the ten-chapter queue must not discard the short navigation runway already in
     * flight. Keep awaiting the two chapters on either side and publish them for the boundary
     * pager; no new parsing work is started here.
     */
    private fun publishInFlightNavigationWindow() {
        criticalNeighborPublishJob?.cancel()
        val origin = _uiState.value.chapterIndex
        val candidates = (1..RENDER_PREFETCH_RADIUS)
            .flatMap { distance -> listOf(origin + distance, origin - distance) }
            .mapNotNull { index ->
                chapterLoads[index]?.takeIf { !it.deferred.isCancelled }?.let { index to it.deferred }
            }
        if (candidates.isEmpty()) return
        criticalNeighborPublishJob = viewModelScope.launch {
            candidates.forEach { (index, deferred) ->
                launch {
                    val chapter = try {
                        deferred.await()
                    } catch (error: CancellationException) {
                        return@launch
                    } ?: return@launch
                    _uiState.update { state ->
                        if (state.chapterIndex != origin || abs(index - origin) > RENDER_PREFETCH_RADIUS) {
                            state
                        } else {
                            state.copy(prefetchedChapters = state.prefetchedChapters + (index to chapter))
                        }
                    }
                }
            }
        }
    }

    fun chapterRendered(navigationVersion: Int) {
        val rendered = _uiState.value
        if (rendered.navigationVersion != navigationVersion || rendered.chapter == null) return
        locationJourney.rendered(rendered.chapterIndex, navigationVersion)
        completePageTurn(rendered.chapterIndex, "success")
        if (rendered.loadStage == ReaderLoadStage.PAGINATING_FIRST_PAGE) {
            _uiState.update { current ->
                if (current.navigationVersion != navigationVersion) current else {
                    current.copy(loadStage = null)
                }
            }
        }
        if (!pageInteractionActive && prefetchedAroundChapterIndex != rendered.chapterIndex) {
            prefetchedAroundChapterIndex = rendered.chapterIndex
            prefetchNearbyChapters(rendered.chapterIndex, rendered.chapters)
        }
    }

    fun finishSession() {
        if (!sessionFinished.compareAndSet(false, true)) return
        val duration = sessionTimer.finish()
        viewModelScope.launch(Dispatchers.IO) { stats.recordSession(bookUuid, duration) }
    }

    override fun onCleared() {
        searchController.close()
        locationJourney.finish()
        progressWriter.close()
        MemoryPressureRegistry.unregister(this)
        chapterPrefetchJob?.cancel()
        criticalNeighbourJobs.values.forEach(Job::cancel)
        criticalNeighbourJobs.clear()
        chapterLoads.values.forEach { request ->
            if (!request.deferred.isCompleted) request.deferred.cancel()
        }
        chapterLoads.clear()
        criticalNeighborPublishJob?.cancel()
        books.setReaderSessionActive(false)
        books.setReaderInteractionActive(false)
        cloudSync.releaseBook(bookUuid)
        books.releaseReaderMemory(bookUuid)
    }
}

private const val READER_OBSERVER_RETRY_MILLIS = 1_000L
