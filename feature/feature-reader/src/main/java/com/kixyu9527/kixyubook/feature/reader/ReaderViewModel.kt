package com.kixyu9527.kixyubook.feature.reader

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.*
import com.kixyu9527.kixyubook.core.reader.engine.ReaderChapter
import com.kixyu9527.kixyubook.core.reader.engine.ReaderPositionManager
import com.kixyu9527.kixyubook.core.reader.engine.contentParagraphs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.abs

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val chapter: ReaderChapter? = null,
    val prefetchedChapters: Map<Int, ReaderChapter> = emptyMap(),
    val chapterIndex: Int = 0,
    val restorePosition: Int = 0,
    val currentPosition: Int = 0,
    val settings: ReaderSettings = ReaderSettings(),
    val settingsLoaded: Boolean = false,
    val fontPath: String? = null,
    val availableFonts: List<UserFont> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<BookSearchResult> = emptyList(),
    val selectedSearchIndex: Int = -1,
    val navigationVersion: Int = 0,
    val loading: Boolean = true,
    val chapterLoading: Boolean = false,
    val pendingChapterTitle: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val books: BookRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val fonts: FontRepository,
    private val stats: ReadingStatsRepository,
) : ViewModel() {
    private val bookUuid: String = checkNotNull(savedStateHandle["bookUuid"])
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState = _uiState.asStateFlow()
    private val sessionFinished = AtomicBoolean(false)
    private var sessionCharacters = 0L
    private val sessionTimer = ReadingSessionTimer(SystemClock::elapsedRealtime)
    private var lastPosition = 0
    private val positions = ReaderPositionManager()
    private val chapterLoads = mutableMapOf<Int, ChapterLoadRequest>()
    private var chapterNavigationJob: Job? = null
    private var chapterPrefetchJob: Job? = null
    private var criticalNeighborPublishJob: Job? = null
    private var pendingChapterIndex: Int? = null
    private var prefetchedAroundChapterIndex: Int? = null
    private var pageInteractionActive = false

    init {
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
        val index = progress?.chapterId?.let { id -> chapters.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
        val content = chapterLoad(index, chapters, ChapterLoadPriority.USER).await() ?: error("章节读取失败")
        lastPosition = progress?.position ?: 0
        _uiState.update {
            it.copy(
                book = book,
                chapters = chapters,
                chapter = content,
                prefetchedChapters = mapOf(index to content),
                chapterIndex = index,
                restorePosition = lastPosition,
                currentPosition = lastPosition,
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

    /** Rejects callbacks emitted by a pager that is being replaced by a direct jump. */
    fun moveChapterFromPage(sourceChapterIndex: Int, delta: Int, openAtEnd: Boolean = false) {
        val state = _uiState.value
        if (pendingChapterIndex != null || state.chapterIndex != sourceChapterIndex) return
        moveChapter(delta, openAtEnd)
    }

    fun jumpToChapter(index: Int) {
        navigateToChapter(
            index = index.coerceIn(0, _uiState.value.chapters.lastIndex),
            position = 0,
        )
    }

    private fun navigateToChapter(index: Int, position: Int) {
        val state = _uiState.value
        if (index == state.chapterIndex && state.chapter != null) {
            chapterNavigationJob?.cancel()
            pendingChapterIndex = null
            _uiState.update { it.copy(chapterLoading = false, pendingChapterTitle = null) }
            return
        }
        pendingChapterIndex = index
        chapterPrefetchJob?.cancel()
        criticalNeighborPublishJob?.cancel()
        prefetchedAroundChapterIndex = null
        _uiState.update {
            it.copy(
                chapterLoading = true,
                pendingChapterTitle = state.chapters.getOrNull(index)?.title,
                error = null,
            )
        }
        chapterNavigationJob?.cancel()
        cancelPendingChapterLoadsExcept(index)
        chapterNavigationJob = viewModelScope.launch {
            var chapterLoaded = false
            try {
                loadChapter(index, position)
                chapterLoaded = true
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

    private suspend fun loadChapter(index: Int, position: Int) {
        val currentState = _uiState.value
        if (index == currentState.chapterIndex && currentState.chapter != null) return
        if (currentState.chapters.getOrNull(index) == null) return
        // Keep the current chapter rendered while the target is read. Removing the
        // reader from composition here left a blank screen when a search jump was slow
        // or its chapter index was not contiguous.
        val readerChapter = currentState.prefetchedChapters[index]
            ?: chapterLoad(index, currentState.chapters, ChapterLoadPriority.USER).await()
            ?: return
        lastPosition = if (position == Int.MAX_VALUE) readerChapter.contentParagraphs().lastOrNull()?.index ?: 0 else position
        _uiState.update {
            it.copy(
                chapter = readerChapter,
                prefetchedChapters = (it.prefetchedChapters + (index to readerChapter))
                    .filterKeys { chapterIndex -> abs(chapterIndex - index) <= RENDER_PREFETCH_RADIUS },
                chapterIndex = index,
                restorePosition = lastPosition,
                currentPosition = lastPosition,
                navigationVersion = it.navigationVersion + 1,
                loading = false,
            )
        }
        savePosition(lastPosition)
    }

    private fun chapterLoad(
        index: Int,
        chapters: List<Chapter>,
        priority: ChapterLoadPriority,
    ): Deferred<ReaderChapter?> {
        val existing = chapterLoads[index]
        if (existing != null && !existing.deferred.isCancelled) {
            // A foreground boundary swipe requests the same exact chapter that prefetch is
            // already parsing. Cancelling a nearly-complete EPUB parse and restarting it as USER
            // caused the pager to pause on its boundary and spring back under rapid reversals.
            if (priority == ChapterLoadPriority.USER && existing.priority == ChapterLoadPriority.PREFETCH) {
                chapterLoads[index] = existing.copy(priority = ChapterLoadPriority.USER)
            }
            return existing.deferred
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
            // Most reading proceeds forward, so warm the next chapter before the previous one
            // at every distance while still retaining a symmetric ten-chapter window.
            .flatMap { distance -> listOf(index + distance, index - distance) }
            .filter { it in chapters.indices }
        // One sequential job avoids creating twenty competing EPUB parses at reader entry. The
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
        if (safeChapter != state.chapterIndex || state.chapter == null) {
            navigateToChapter(safeChapter, position.coerceAtLeast(0))
            return
        }
        chapterNavigationJob?.cancel()
        pendingChapterIndex = null
        chapterNavigationJob = viewModelScope.launch {
            lastPosition = position.coerceAtLeast(0)
            _uiState.update { current ->
                current.copy(
                    restorePosition = lastPosition,
                    currentPosition = lastPosition,
                    navigationVersion = current.navigationVersion + 1,
                )
            }
            savePosition(lastPosition)
        }
    }

    fun savePosition(position: Int, chapterComplete: Boolean = false) {
        val state = _uiState.value
        val chapter = state.chapter ?: return
        val content = chapter.contentParagraphs()
        val currentOffset = content.indexOfLast { it.index <= position }.coerceAtLeast(0)
        val previousOffset = content.indexOfLast { it.index <= lastPosition }.coerceAtLeast(0)
        val safePosition = content.getOrNull(currentOffset)?.index ?: 0
        if (content.isNotEmpty() && currentOffset > previousOffset) {
            sessionCharacters += content.subList(previousOffset, currentOffset)
                .filter { it.kind == ParagraphKind.TEXT }
                .sumOf { it.text.length }
                .toLong()
        }
        lastPosition = safePosition
        _uiState.update { it.copy(currentPosition = safePosition) }
        val total = positions.bookFraction(
            chapterIndex = state.chapterIndex,
            chapterCount = state.chapters.size,
            paragraphOffset = currentOffset,
            paragraphCount = content.size,
            chapterComplete = chapterComplete,
        )
        viewModelScope.launch { books.saveProgress(ReadingProgress(bookUuid, chapter.id, safePosition, updatedTime = System.currentTimeMillis(), fraction = total)) }
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
        if (normalized.isBlank()) {
            _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), selectedSearchIndex = -1) }
            return@launch
        }
        val results = books.searchBook(bookUuid, normalized)
        _uiState.update {
            it.copy(
                searchQuery = normalized,
                searchResults = results,
                selectedSearchIndex = if (results.isEmpty()) -1 else 0,
            )
        }
    }

    fun selectSearchResult(index: Int) {
        val state = _uiState.value
        if (state.searchResults.isEmpty()) return
        val safeIndex = index.coerceIn(0, state.searchResults.lastIndex)
        val result = state.searchResults.getOrNull(safeIndex) ?: return
        _uiState.update { it.copy(selectedSearchIndex = safeIndex) }
        jumpToPosition(result.chapterIndex, result.paragraphIndex)
    }

    fun moveSearchResult(delta: Int) {
        val state = _uiState.value
        if (state.searchResults.isEmpty()) return
        val current = state.selectedSearchIndex.coerceAtLeast(0)
        selectSearchResult((current + delta).coerceIn(state.searchResults.indices))
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), selectedSearchIndex = -1) }
    }

    /** Counts only time during which this reader destination is resumed with readable content. */
    fun setReadingActive(active: Boolean) {
        if (sessionFinished.get()) return
        sessionTimer.setActive(active)
    }

    /**
     * A drag or animated page turn owns the frame budget. Keep already prepared neighbours, but
     * stop the remaining ten-chapter speculative queue until the pager settles. This mirrors the
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
            publishInFlightImmediateNeighbors()
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
     * Stopping the ten-chapter queue must not discard the exact previous/next chapter already in
     * flight. Keep awaiting only those two deferred values and publish them for the boundary pager;
     * no new parsing work is started here.
     */
    private fun publishInFlightImmediateNeighbors() {
        criticalNeighborPublishJob?.cancel()
        val origin = _uiState.value.chapterIndex
        val candidates = listOf(origin + 1, origin - 1).mapNotNull { index ->
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
        viewModelScope.launch(Dispatchers.IO) { stats.recordSession(bookUuid, duration, sessionCharacters) }
    }

    override fun onCleared() {
        criticalNeighborPublishJob?.cancel()
        books.setReaderInteractionActive(false)
        super.onCleared()
    }
}

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

private const val CHAPTER_PREFETCH_RADIUS = 10
// The renderer consumes only the immediate previous/next chapter. Keeping a second pair in the
// StateFlow caused two extra full reader recompositions without making any swipe path faster.
private const val RENDER_PREFETCH_RADIUS = 1
