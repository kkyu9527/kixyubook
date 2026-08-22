package com.kixyu9527.kixyubook.feature.reader

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val chapter: ReaderChapter? = null,
    val prefetchedChapters: Map<Int, ReaderChapter> = emptyMap(),
    val chapterIndex: Int = 0,
    val restorePosition: Int = 0,
    val restoreCharOffset: Int = 0,
    val currentPosition: Int = 0,
    val currentCharOffset: Int = 0,
    val settings: ReaderSettings = ReaderSettings(),
    val settingsLoaded: Boolean = false,
    val fontPath: String? = null,
    val availableFonts: List<UserFont> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val corrections: List<TextCorrection> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<BookSearchResult> = emptyList(),
    val selectedSearchIndex: Int = -1,
    val searchReturnAvailable: Boolean = false,
    val navigationVersion: Int = 0,
    val loading: Boolean = true,
    val chapterLoading: Boolean = false,
    val pendingChapterTitle: String? = null,
    val error: String? = null,
)

@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val books: BookRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val fonts: FontRepository,
    private val stats: ReadingStatsRepository,
    private val cloudSync: CloudSyncCoordinator,
    private val textCorrections: TextCorrectionRepository,
) : ViewModel(), MemoryPressureListener {
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): ReaderViewModel
    }

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState = _uiState.asStateFlow()
    private val sessionFinished = AtomicBoolean(false)
    private val sessionTimer = ReadingSessionTimer(SystemClock::elapsedRealtime)
    private var lastPosition = 0
    private var lastCharOffset = 0
    private var searchReturnPosition: SearchReturnPosition? = null
    private val positions = ReaderPositionManager()
    private val chapterLoads = mutableMapOf<Int, ChapterLoadRequest>()
    private var chapterNavigationJob: Job? = null
    private var chapterPrefetchJob: Job? = null
    private var criticalNeighborPublishJob: Job? = null
    private var criticalReadAheadJob: Job? = null
    private var criticalReadAheadIndex: Int? = null
    private var pendingChapterIndex: Int? = null
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
    @Volatile private var latestProgressCheckpoint: ReadingProgress? = null

    init {
        MemoryPressureRegistry.register(this)
        cloudSync.prioritizeBook(bookUuid)
        viewModelScope.launch {
            // Initial loading already performs the first chapter/progress queries. Starting the
            // long-lived observers at the same time duplicated those reads. Settings and fonts
            // are also folded into that first atomic state publication so the destination does
            // not rebuild once for settings and again for content during its enter transition.
            loadInitial()
            launch {
                combine(settingsRepository.settings, fonts.observeFonts()) { settings, fontList ->
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
            launch {
                books.observeChapters(bookUuid).collect { chapters ->
                    if (chapters.isEmpty()) return@collect
                    _uiState.update { current ->
                        if (current.chapters == chapters) current else current.copy(chapters = chapters)
                    }
                }
            }
            launch {
                books.observeBookmarks(bookUuid).collect { bookmarks ->
                    _uiState.update { it.copy(bookmarks = bookmarks) }
                }
            }
            launch {
                textCorrections.observeBookCorrections(bookUuid).collectLatest { corrections ->
                    applyCorrectionSnapshot(corrections)
                }
            }
            launch {
                books.observeProgress(bookUuid).filterNotNull().collect(::applySyncedProgress)
            }
            launch {
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

    private suspend fun loadInitial() = runCatching {
        val initialData = coroutineScope {
            val book = async { books.getBook(bookUuid) }
            val chapters = async { books.observeChapters(bookUuid).first { it.isNotEmpty() } }
            val progress = async { books.observeProgress(bookUuid).first() }
            val presentation = async {
                combine(settingsRepository.settings, fonts.observeFonts()) { settings, fontList ->
                    InitialReaderPresentation(
                        settings = settings,
                        fontPath = fontList.firstOrNull { it.uuid == settings.fontUuid }?.filePath,
                        fonts = fontList,
                    )
                }.first()
            }
            InitialReaderData(book.await(), chapters.await(), progress.await(), presentation.await())
        }
        val book = initialData.book ?: error("书籍不存在")
        val chapters = initialData.chapters
        require(chapters.isNotEmpty()) { "书籍没有可阅读章节" }
        val progress = initialData.progress
        acceptedProgressUpdatedAt = progress?.updatedTime ?: Long.MIN_VALUE
        val index = progress?.chapterId?.let { id -> chapters.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
        val content = chapterLoad(index, chapters, ChapterLoadPriority.USER).await() ?: error("章节读取失败")
        lastPosition = progress?.paragraphIndex ?: 0
        lastCharOffset = progress?.charOffset?.coerceAtLeast(0) ?: 0
        openingChapterId = progress?.chapterId ?: chapters[index].id
        openingPosition = lastPosition
        openingCharOffset = lastCharOffset
        _uiState.update {
            it.copy(
                book = book,
                chapters = chapters,
                chapter = content,
                prefetchedChapters = mapOf(index to content),
                chapterIndex = index,
                restorePosition = lastPosition,
                restoreCharOffset = lastCharOffset,
                currentPosition = lastPosition,
                currentCharOffset = lastCharOffset,
                settings = initialData.presentation.settings,
                settingsLoaded = true,
                fontPath = initialData.presentation.fontPath,
                availableFonts = initialData.presentation.fonts,
                loading = false,
            )
        }
    }.onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message) } }

    fun moveChapter(delta: Int, openAtEnd: Boolean = false) {
        val state = _uiState.value
        val baseIndex = pendingChapterIndex ?: state.chapterIndex
        navigateToChapter(
            index = (baseIndex + delta).coerceIn(0, state.chapters.lastIndex),
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
        navigateToChapter(
            index = (sourceChapterIndex + delta).coerceIn(0, state.chapters.lastIndex),
            position = if (openAtEnd) Int.MAX_VALUE else 0,
        )
    }

    fun jumpToChapter(index: Int) {
        navigateToChapter(
            index = index.coerceIn(0, _uiState.value.chapters.lastIndex),
            position = 0,
        )
    }

    fun saveParagraphCorrection(
        chapterIndex: Int,
        paragraphIndex: Int,
        displayedText: String,
        replacementText: String,
    ) {
        val state = _uiState.value
        val chapterPosition = state.chapters.indexOfFirst { it.index == chapterIndex }
        val chapter = state.chapters.getOrNull(chapterPosition) ?: return
        val existing = state.corrections.firstOrNull {
            it.chapterKey == chapter.chapterKey &&
                it.paragraphIndex == paragraphIndex && it.status != TextCorrectionStatus.UNRESOLVED
        }
        viewModelScope.launch {
            if (existing != null) {
                textCorrections.updateCorrection(existing.uuid, replacementText)
            } else {
                textCorrections.createParagraphCorrection(
                    bookUuid = bookUuid,
                    chapterKey = chapter.chapterKey,
                    chapterIndex = chapter.index,
                    paragraphIndex = paragraphIndex,
                    originalText = displayedText,
                    replacementText = replacementText,
                )
            }
        }
    }

    fun deleteCorrection(uuid: String) {
        viewModelScope.launch {
            textCorrections.deleteCorrection(uuid)
        }
    }

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
    }

    private suspend fun reloadCorrectedChapter(position: Int) {
        val target = _uiState.value.chapters.getOrNull(position) ?: return
        chapterLoads.remove(position)?.deferred?.cancel()
        val refreshed = books.getChapter(bookUuid, target.index)?.toReaderChapter() ?: return
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
                    it.index == state.currentPosition
                }
                val anchorPosition = anchorParagraph?.index ?: state.currentPosition
                val anchorOffset = state.currentCharOffset.coerceIn(
                    0,
                    anchorParagraph?.text?.length ?: 0,
                )
                state.copy(
                    chapter = refreshed,
                    prefetchedChapters = mapOf(position to refreshed),
                    restorePosition = anchorPosition,
                    restoreCharOffset = anchorOffset,
                    currentPosition = anchorPosition,
                    currentCharOffset = anchorOffset,
                    navigationVersion = state.navigationVersion + 1,
                )
            }
        }
    }

    private fun navigateToChapter(
        index: Int,
        position: Int,
        charOffset: Int = 0,
        persistProgress: Boolean = true,
    ) {
        val state = _uiState.value
        if (index == state.chapterIndex && state.chapter != null) {
            chapterNavigationJob?.cancel()
            pendingChapterIndex = null
            applyPositionWithinCurrentChapter(position, charOffset, persistProgress)
            return
        }
        chapterNavigationJob?.cancel()
        chapterPrefetchJob?.cancel()
        criticalNeighborPublishJob?.cancel()
        criticalReadAheadJob?.cancel()
        criticalReadAheadIndex = null
        prefetchedAroundChapterIndex = null
        cancelPendingChapterLoadsExcept(index)

        // A paged boundary already renders the decoded adjacent chapter. Publishing the same
        // chapter through a new coroutine leaves a short interval in which the old Pager is at
        // its terminal item but the replacement Pager does not exist yet. With one-page EPUB
        // chapters a second fast gesture lands in that interval and visibly springs back.
        // Commit an in-memory neighbour synchronously so the next gesture always reaches the
        // newly active Pager.
        state.prefetchedChapters[index]?.let { cached ->
            pendingChapterIndex = null
            activateChapter(index, position, charOffset, cached, persistProgress)
            return
        }

        pendingChapterIndex = index
        _uiState.update {
            it.copy(
                chapterLoading = true,
                pendingChapterTitle = state.chapters.getOrNull(index)?.title,
                error = null,
            )
        }
        chapterNavigationJob = viewModelScope.launch {
            var chapterLoaded = false
            try {
                chapterLoaded = loadChapter(index, position, charOffset, persistProgress)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (pendingChapterIndex == index) {
                    _uiState.update { it.copy(error = error.message ?: "章节读取失败") }
                }
            } finally {
                if (pendingChapterIndex == index) {
                    pendingChapterIndex = null
                    if (!chapterLoaded) {
                        _uiState.update { it.copy(chapterLoading = false, pendingChapterTitle = null) }
                    }
                }
            }
        }
    }

    private suspend fun loadChapter(
        index: Int,
        position: Int,
        charOffset: Int,
        persistProgress: Boolean,
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
            DiagnosticLog.record(
                Category.READER,
                "chapter_navigation_finished",
                elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                outcome = "missing",
                details = mapOf("book" to bookUuid.take(8), "chapter" to index),
            )
            return false
        }
        activateChapter(index, position, charOffset, readerChapter, persistProgress)
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
    ) {
        lastPosition = if (position == Int.MAX_VALUE) readerChapter.contentParagraphs().lastOrNull()?.index ?: 0 else position
        lastCharOffset = if (position == Int.MAX_VALUE) Int.MAX_VALUE else charOffset.coerceAtLeast(0)
        _uiState.update {
            it.copy(
                chapter = readerChapter,
                prefetchedChapters = (it.prefetchedChapters + (index to readerChapter))
                    .filterKeys { chapterIndex -> abs(chapterIndex - index) <= RENDER_PREFETCH_RADIUS },
                chapterIndex = index,
                restorePosition = lastPosition,
                restoreCharOffset = lastCharOffset,
                currentPosition = lastPosition,
                currentCharOffset = lastCharOffset,
                navigationVersion = it.navigationVersion + 1,
                loading = false,
                chapterLoading = false,
                pendingChapterTitle = null,
                error = null,
            )
        }
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
        _uiState.update { current ->
            current.copy(
                restorePosition = lastPosition,
                restoreCharOffset = lastCharOffset,
                currentPosition = lastPosition,
                currentCharOffset = lastCharOffset,
                navigationVersion = current.navigationVersion + 1,
                chapterLoading = false,
                pendingChapterTitle = null,
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
        acceptedProgressUpdatedAt = progress.updatedTime
        val state = _uiState.value
        val targetIndex = state.chapters.indexOfFirst { chapter ->
            chapter.id == progress.chapterId ||
                (progress.chapterKey.isNotBlank() && chapter.chapterKey == progress.chapterKey)
        }
        if (targetIndex < 0) return
        val targetPosition = progress.paragraphIndex.coerceAtLeast(0)
        val targetCharOffset = progress.charOffset.coerceAtLeast(0)
        if (
            targetIndex == state.chapterIndex &&
            targetPosition == state.currentPosition &&
            targetCharOffset == state.currentCharOffset
        ) return

        if (targetIndex == state.chapterIndex && state.chapter != null) {
            chapterNavigationJob?.cancel()
            pendingChapterIndex = null
            lastPosition = targetPosition
            lastCharOffset = targetCharOffset
            _uiState.update { current ->
                current.copy(
                    restorePosition = targetPosition,
                    restoreCharOffset = targetCharOffset,
                    currentPosition = targetPosition,
                    currentCharOffset = targetCharOffset,
                    navigationVersion = current.navigationVersion + 1,
                )
            }
        } else {
            navigateToChapter(
                targetIndex,
                targetPosition,
                targetCharOffset,
                persistProgress = false,
            )
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
            persistProgress(pending.copy(updatedTime = System.currentTimeMillis()))
        }
    }

    private fun chapterLoad(
        index: Int,
        chapters: List<Chapter>,
        priority: ChapterLoadPriority,
    ): Deferred<ReaderChapter?> {
        val existing = chapterLoads[index]
        if (existing != null && !existing.deferred.isCancelled) {
            val promotesSpeculativeLoad = priority != ChapterLoadPriority.PREFETCH &&
                existing.priority == ChapterLoadPriority.PREFETCH
            val promotesReadAheadToUser = priority == ChapterLoadPriority.USER &&
                existing.priority == ChapterLoadPriority.READ_AHEAD
            if (promotesSpeculativeLoad || promotesReadAheadToUser) {
                // Current content must never inherit speculative scheduling. The prefetch parser
                // may still be unwinding a difficult XHTML file, but parsing happens outside the
                // repository commit lock so this USER request can overtake it immediately.
                existing.deferred.cancel()
                chapterLoads.remove(index)
            } else {
                return existing.deferred
            }
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
     * Promote only the next readable chapter when the adaptive end-of-chapter deadline is reached.
     *
     * Normal entry-time prefetch stays speculative and yields to every page animation. This
     * deadline path uses a persistent background-priority lane: an uncached, newly imported EPUB
     * keeps progressing through the final page turns without competing at render-thread priority.
     * Cached chapters take the same path but return from memory/disk without parsing again.
     */
    fun prioritizeNextChapter(sourceChapterIndex: Int) {
        val state = _uiState.value
        if (state.chapterIndex != sourceChapterIndex) return
        val targetIndex = sourceChapterIndex + 1
        if (targetIndex !in state.chapters.indices || targetIndex in state.prefetchedChapters) return
        if (criticalReadAheadIndex == targetIndex && criticalReadAheadJob?.isActive == true) return

        criticalReadAheadJob?.cancel()
        criticalReadAheadIndex = targetIndex
        criticalReadAheadJob = viewModelScope.launch {
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
                if (criticalReadAheadIndex == targetIndex) {
                    criticalReadAheadIndex = null
                    criticalReadAheadJob = null
                }
            }
        }
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
        val state = _uiState.value
        val safeChapter = state.chapters.indexOfFirst { it.index == chapterIndex }
            .takeIf { it >= 0 }
            ?: chapterIndex.coerceIn(0, state.chapters.lastIndex)
        navigateToChapter(safeChapter, position.coerceAtLeast(0))
    }

    fun savePosition(position: Int, charOffset: Int = 0, chapterComplete: Boolean = false) {
        val state = _uiState.value
        val chapter = state.chapter ?: return
        val content = chapter.contentParagraphs()
        val paragraphOffset = content.indexOfLast { it.index <= position }.coerceAtLeast(0)
        val paragraph = content.getOrNull(paragraphOffset)
        val safePosition = paragraph?.index ?: 0
        val safeCharOffset = charOffset.coerceIn(0, paragraph?.text?.length ?: 0)
        lastPosition = safePosition
        lastCharOffset = safeCharOffset
        _uiState.update {
            it.copy(currentPosition = safePosition, currentCharOffset = safeCharOffset)
        }
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
        )
        latestProgressCheckpoint = progress
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
        latestProgressCheckpoint = progress
        latestLocalProgressWriteAt = progress.updatedTime
        acceptedProgressUpdatedAt = maxOf(acceptedProgressUpdatedAt, progress.updatedTime)
        viewModelScope.launch {
            books.saveProgress(progress)
            // Reusing prioritizeBook here wakes the already active conflated P1 channel; it does
            // not restart the reader or enqueue a global Worker.
            cloudSync.prioritizeBook(bookUuid)
        }
    }

    /** HyperOS can ask for a process checkpoint immediately before enforcing its memory budget. */
    override fun onMemoryPressure(level: MemoryPressureLevel) {
        if (level != MemoryPressureLevel.CRITICAL) return
        val checkpoint = latestProgressCheckpoint ?: return
        // The registry dispatches on the vendor receiver's background HandlerThread. Keep a firm
        // deadline so the complete TRIM/KILL response remains inside HyperOS's three-second limit.
        val saved = runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(PROGRESS_CHECKPOINT_TIMEOUT_MS) {
                books.saveProgress(checkpoint)
                true
            }
        }
        check(saved == true) { "Reading progress checkpoint exceeded its deadline" }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) { viewModelScope.launch { settingsRepository.update(transform) } }

    fun importFont(uri: String) = viewModelScope.launch { fonts.importFont(uri) }

    fun deleteFont(font: UserFont) = viewModelScope.launch {
        if (_uiState.value.settings.fontUuid == font.uuid) {
            settingsRepository.update { it.copy(fontUuid = null) }
        }
        fonts.deleteFont(font.uuid)
    }

    fun addBookmark() = viewModelScope.launch {
        val state = _uiState.value
        val chapter = state.chapter ?: return@launch
        val position = lastPosition
        val preview = chapter.contentParagraphs()
            .firstOrNull { it.index >= position && it.kind == ParagraphKind.TEXT }
            ?.text
            ?.replace(Regex("\\s+"), " ")
            ?.take(80)
            .orEmpty()
        books.addBookmark(
            Bookmark(
                uuid = java.util.UUID.randomUUID().toString(),
                bookUuid = bookUuid,
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                chapterIndex = state.chapterIndex,
                position = position,
                preview = preview,
                createdTime = System.currentTimeMillis(),
            ),
        )
    }

    fun deleteBookmark(uuid: String) = viewModelScope.launch { books.deleteBookmark(uuid) }

    fun search(query: String) = viewModelScope.launch {
        val normalized = query.trim()
        searchReturnPosition = null
        if (normalized.isBlank()) {
            _uiState.update {
                it.copy(
                    searchQuery = "",
                    searchResults = emptyList(),
                    selectedSearchIndex = -1,
                    searchReturnAvailable = false,
                )
            }
            return@launch
        }
        val results = books.searchBook(bookUuid, normalized)
        _uiState.update {
            it.copy(
                searchQuery = normalized,
                searchResults = results,
                selectedSearchIndex = if (results.isEmpty()) -1 else 0,
                searchReturnAvailable = false,
            )
        }
    }

    fun selectSearchResult(index: Int) {
        val state = _uiState.value
        if (state.searchResults.isEmpty()) return
        val safeIndex = index.coerceIn(0, state.searchResults.lastIndex)
        val result = state.searchResults.getOrNull(safeIndex) ?: return
        if (searchReturnPosition == null) {
            searchReturnPosition = SearchReturnPosition(
                chapterIndex = state.chapterIndex,
                paragraphIndex = lastPosition,
                charOffset = lastCharOffset,
            )
        }
        _uiState.update {
            it.copy(selectedSearchIndex = safeIndex, searchReturnAvailable = true)
        }
        jumpToPosition(result.chapterIndex, result.paragraphIndex)
    }

    fun returnFromSearchResult() {
        val position = searchReturnPosition ?: return
        searchReturnPosition = null
        _uiState.update { it.copy(searchReturnAvailable = false) }
        navigateToChapter(
            index = position.chapterIndex,
            position = position.paragraphIndex,
            charOffset = position.charOffset,
        )
    }

    fun moveSearchResult(delta: Int) {
        val state = _uiState.value
        if (state.searchResults.isEmpty()) return
        val current = state.selectedSearchIndex.coerceAtLeast(0)
        selectSearchResult((current + delta).coerceIn(state.searchResults.indices))
    }

    fun clearSearch() {
        searchReturnPosition = null
        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                selectedSearchIndex = -1,
                searchReturnAvailable = false,
            )
        }
    }

    /** Counts only time during which this reader destination is resumed with readable content. */
    fun setReadingActive(active: Boolean) {
        if (sessionFinished.get()) return
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
        if (state.chapter != null && pendingChapterIndex == null) {
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
        if (rendered.chapterLoading) {
            _uiState.update { current ->
                if (current.navigationVersion != navigationVersion) current else {
                    current.copy(chapterLoading = false, pendingChapterTitle = null)
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
        MemoryPressureRegistry.unregister(this)
        chapterPrefetchJob?.cancel()
        criticalReadAheadJob?.cancel()
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

private data class SearchReturnPosition(
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val charOffset: Int,
)

internal fun hasReaderMovedFromOpening(
    openingChapterId: Long?,
    openingPosition: Int,
    openingCharOffset: Int = 0,
    currentChapterId: Long,
    currentPosition: Int,
    currentCharOffset: Int = 0,
): Boolean = openingChapterId != currentChapterId ||
    openingPosition != currentPosition ||
    openingCharOffset != currentCharOffset

private data class ChapterLoadRequest(
    val priority: ChapterLoadPriority,
    val deferred: Deferred<ReaderChapter?>,
)

private data class InitialReaderPresentation(
    val settings: ReaderSettings,
    val fontPath: String?,
    val fonts: List<UserFont>,
)

private data class InitialReaderData(
    val book: Book?,
    val chapters: List<Chapter>,
    val progress: ReadingProgress?,
    val presentation: InitialReaderPresentation,
)

private fun ChapterContent.toReaderChapter() = ReaderChapter(chapter.id, chapter.bookUuid, chapter.title, chapter.index, paragraphs)

// Decode only what the pager can immediately reach. The wider ±10 chapter window is persisted by
// the EPUB binary cache/background index instead of being retained as live paragraph objects.
private const val CHAPTER_PREFETCH_RADIUS = 2
// The renderer composes only the immediate previous/next chapter, but retaining a second decoded
// pair gives one-page EPUB chapters enough runway for rapid consecutive boundary gestures.
private const val RENDER_PREFETCH_RADIUS = 2
private const val SLOW_NAVIGATION_MS = 150L
private const val PROGRESS_CHECKPOINT_TIMEOUT_MS = 1_500L

internal fun shouldApplySyncedProgress(
    incomingUpdatedAt: Long,
    acceptedUpdatedAt: Long,
    latestLocalWriteAt: Long,
): Boolean = incomingUpdatedAt > acceptedUpdatedAt && incomingUpdatedAt > latestLocalWriteAt

internal fun nextProgressUpdatedAt(currentTime: Long, latestLocalWriteAt: Long): Long =
    if (latestLocalWriteAt >= currentTime && latestLocalWriteAt < Long.MAX_VALUE) {
        latestLocalWriteAt + 1
    } else {
        currentTime
    }

internal fun changedCorrectionChapterPositions(
    previous: List<TextCorrection>,
    current: List<TextCorrection>,
    chapters: List<Chapter>,
): Set<Int> {
    if (previous == current) return emptySet()
    val previousById = previous.associateBy(TextCorrection::uuid)
    val currentById = current.associateBy(TextCorrection::uuid)
    val changed = (previousById.keys + currentById.keys).flatMap { uuid ->
        val old = previousById[uuid]
        val new = currentById[uuid]
        if (old == new) emptyList() else listOfNotNull(old, new)
    }
    return chapters.mapIndexedNotNull { position, chapter ->
        position.takeIf {
            changed.any { correction ->
                (correction.chapterKey.isNotBlank() && correction.chapterKey == chapter.chapterKey) ||
                    correction.chapterIndex == chapter.index
            }
        }
    }.toSet()
}
