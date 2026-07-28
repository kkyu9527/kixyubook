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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val chapter: ReaderChapter? = null,
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
    private val sessionStart = SystemClock.elapsedRealtime()
    private val sessionFinished = AtomicBoolean(false)
    private var sessionCharacters = 0L
    private var lastPosition = 0
    private val positions = ReaderPositionManager()

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
            books.observeBookmarks(bookUuid).collect { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    private suspend fun loadInitial() = runCatching {
        val book = books.getBook(bookUuid) ?: error("书籍不存在")
        val chapters = books.getChapters(bookUuid)
        require(chapters.isNotEmpty()) { "书籍没有可阅读章节" }
        val progress = books.observeProgress(bookUuid).first()
        val index = progress?.chapterId?.let { id -> chapters.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
        val content = books.getChapter(bookUuid, chapters[index].index) ?: error("章节读取失败")
        lastPosition = progress?.position ?: 0
        _uiState.update {
            it.copy(
                book = book,
                chapters = chapters,
                chapter = content.toReaderChapter(),
                chapterIndex = index,
                restorePosition = lastPosition,
                currentPosition = lastPosition,
                loading = false,
            )
        }
    }.onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message) } }

    fun moveChapter(delta: Int, openAtEnd: Boolean = false) = viewModelScope.launch {
        loadChapter((_uiState.value.chapterIndex + delta).coerceIn(0, _uiState.value.chapters.lastIndex), if (openAtEnd) Int.MAX_VALUE else 0)
    }

    fun jumpToChapter(index: Int) = viewModelScope.launch { loadChapter(index.coerceIn(0, _uiState.value.chapters.lastIndex), 0) }

    private suspend fun loadChapter(index: Int, position: Int) {
        if (index == _uiState.value.chapterIndex && _uiState.value.chapter != null) return
        val target = _uiState.value.chapters.getOrNull(index) ?: return
        // Keep the current chapter rendered while the target is read. Removing the
        // reader from composition here left a blank screen when a search jump was slow
        // or its chapter index was not contiguous.
        val content = books.getChapter(bookUuid, target.index) ?: return
        val readerChapter = content.toReaderChapter()
        lastPosition = if (position == Int.MAX_VALUE) readerChapter.contentParagraphs().lastOrNull()?.index ?: 0 else position
        _uiState.update {
            it.copy(
                chapter = readerChapter,
                chapterIndex = index,
                restorePosition = lastPosition,
                currentPosition = lastPosition,
                navigationVersion = it.navigationVersion + 1,
                loading = false,
            )
        }
        savePosition(lastPosition)
    }

    fun jumpToPosition(chapterIndex: Int, position: Int) = viewModelScope.launch {
        val state = _uiState.value
        val safeChapter = state.chapters.indexOfFirst { it.index == chapterIndex }
            .takeIf { it >= 0 }
            ?: chapterIndex.coerceIn(0, state.chapters.lastIndex)
        if (safeChapter == _uiState.value.chapterIndex && _uiState.value.chapter != null) {
            lastPosition = position.coerceAtLeast(0)
            _uiState.update {
                it.copy(
                    restorePosition = lastPosition,
                    currentPosition = lastPosition,
                    navigationVersion = it.navigationVersion + 1,
                )
            }
            savePosition(lastPosition)
        } else {
            loadChapter(safeChapter, position.coerceAtLeast(0))
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

    fun saveTextEdit(paragraphIndex: Int, replacement: String) = viewModelScope.launch {
        val chapterIndex = _uiState.value.chapterIndex
        books.updateTxtParagraph(bookUuid, chapterIndex, paragraphIndex, replacement)
            .onSuccess {
                val chapters = books.getChapters(bookUuid)
                val safeChapterIndex = chapterIndex.coerceIn(0, chapters.lastIndex)
                val refreshed = books.getChapter(bookUuid, safeChapterIndex) ?: return@onSuccess
                _uiState.update {
                    it.copy(
                        chapters = chapters,
                        chapter = refreshed.toReaderChapter(),
                        chapterIndex = safeChapterIndex,
                    )
                }
            }
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

    fun finishSession() {
        if (!sessionFinished.compareAndSet(false, true)) return
        val duration = SystemClock.elapsedRealtime() - sessionStart
        viewModelScope.launch(Dispatchers.IO) { stats.recordSession(bookUuid, duration, sessionCharacters) }
    }
}

private fun ChapterContent.toReaderChapter() = ReaderChapter(chapter.id, chapter.bookUuid, chapter.title, chapter.index, paragraphs)
