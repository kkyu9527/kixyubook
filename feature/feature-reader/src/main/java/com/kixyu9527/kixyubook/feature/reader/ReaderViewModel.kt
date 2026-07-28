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
    private val chapterLoads = mutableMapOf<Int, Deferred<ReaderChapter?>>()
    private var chapterNavigationJob: Job? = null
    private var pendingChapterIndex: Int? = null

    init {
        viewModelScope.launch {
            combine(settingsRepository.settings, fonts.observeFonts()) { settings, fontList ->
                Triple(settings, fontList.firstOrNull { it.uuid == settings.fontUuid }?.filePath, fontList)
            }.collect { (settings, path, available) ->
                _uiState.update { it.copy(settings = settings, fontPath = path, availableFonts = available) }
            }
        }
        viewModelScope.launch { loadInitial() }
        viewModelScope.launch {
            books.observeChapters(bookUuid).collect { chapters ->
                if (chapters.isEmpty()) return@collect
                _uiState.update { current ->
                    if (current.chapters == chapters) current else current.copy(chapters = chapters)
                }
                val current = _uiState.value
                if (current.chapter != null) prefetchNearbyChapters(current.chapterIndex, chapters)
            }
        }
        viewModelScope.launch {
            books.observeBookmarks(bookUuid).collect { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    private suspend fun loadInitial() = runCatching {
        val initialData = coroutineScope {
            val book = async { books.getBook(bookUuid) }
            val chapters = async { books.observeChapters(bookUuid).first { it.isNotEmpty() } }
            val progress = async { books.observeProgress(bookUuid).first() }
            Triple(book.await(), chapters.await(), progress.await())
        }
        val book = initialData.first ?: error("书籍不存在")
        val chapters = initialData.second
        require(chapters.isNotEmpty()) { "书籍没有可阅读章节" }
        val progress = initialData.third
        val index = progress?.chapterId?.let { id -> chapters.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
        val content = chapterLoad(index, chapters).await() ?: error("章节读取失败")
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
                loading = false,
            )
        }
        prefetchNearbyChapters(index, chapters)
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
            ?: chapterLoad(index, currentState.chapters).await()
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
        prefetchNearbyChapters(index, currentState.chapters)
        savePosition(lastPosition)
    }

    private fun chapterLoad(index: Int, chapters: List<Chapter>): Deferred<ReaderChapter?> =
        chapterLoads.getOrPut(index) {
            viewModelScope.async {
                val target = chapters.getOrNull(index) ?: return@async null
                books.getChapter(bookUuid, target.index)?.toReaderChapter()
            }
        }

    private fun prefetchNearbyChapters(index: Int, chapters: List<Chapter>) {
        val loads = chapterLoads.iterator()
        while (loads.hasNext()) {
            val (chapterIndex, load) = loads.next()
            if (abs(chapterIndex - index) > CHAPTER_PREFETCH_RADIUS) {
                if (!load.isCompleted) load.cancel()
                loads.remove()
            }
        }
        (1..CHAPTER_PREFETCH_RADIUS)
            // Most reading proceeds forward, so warm the next chapter before the previous one
            // at every distance while still retaining a symmetric ten-chapter window.
            .flatMap { distance -> listOf(index + distance, index - distance) }
            .filter { it in chapters.indices }
            .forEach { nearbyIndex ->
                // Start every load immediately, ordered nearest-first. The
                // repository serializes source reads and keeps the decoded
                // chapters in its LRU; only the closest render window enters Compose state.
                // Two chapters on either side let rapid EPUB turns reuse completed layout.
                val load = chapterLoad(nearbyIndex, chapters)
                if (abs(nearbyIndex - index) <= RENDER_PREFETCH_RADIUS) {
                    viewModelScope.launch {
                        val chapter = load.await() ?: return@launch
                        _uiState.update { state ->
                            if (abs(state.chapterIndex - nearbyIndex) > RENDER_PREFETCH_RADIUS) state else state.copy(
                                prefetchedChapters = state.prefetchedChapters + (nearbyIndex to chapter),
                            )
                        }
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
            val (chapterIndex, load) = loads.next()
            if (chapterIndex != targetIndex && !load.isCompleted) {
                load.cancel()
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

    fun chapterRendered(navigationVersion: Int) {
        _uiState.update { current ->
            if (current.navigationVersion != navigationVersion || !current.chapterLoading) current else {
                current.copy(chapterLoading = false, pendingChapterTitle = null)
            }
        }
    }

    fun finishSession() {
        if (!sessionFinished.compareAndSet(false, true)) return
        val duration = sessionTimer.finish()
        viewModelScope.launch(Dispatchers.IO) { stats.recordSession(bookUuid, duration, sessionCharacters) }
    }
}

private fun ChapterContent.toReaderChapter() = ReaderChapter(chapter.id, chapter.bookUuid, chapter.title, chapter.index, paragraphs)

private const val CHAPTER_PREFETCH_RADIUS = 10
private const val RENDER_PREFETCH_RADIUS = 2
