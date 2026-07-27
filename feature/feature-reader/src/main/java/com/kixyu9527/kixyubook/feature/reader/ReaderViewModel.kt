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
    val settings: ReaderSettings = ReaderSettings(),
    val fontPath: String? = null,
    val availableFonts: List<UserFont> = emptyList(),
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
    }

    private suspend fun loadInitial() = runCatching {
        val book = books.getBook(bookUuid) ?: error("书籍不存在")
        val chapters = books.getChapters(bookUuid)
        require(chapters.isNotEmpty()) { "书籍没有可阅读章节" }
        val progress = books.observeProgress(bookUuid).first()
        val index = progress?.chapterId?.let { id -> chapters.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
        val content = books.getChapter(bookUuid, index) ?: error("章节读取失败")
        lastPosition = progress?.position ?: 0
        _uiState.update { it.copy(book = book, chapters = chapters, chapter = content.toReaderChapter(), chapterIndex = index, restorePosition = lastPosition, loading = false) }
    }.onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message) } }

    fun moveChapter(delta: Int, openAtEnd: Boolean = false) = viewModelScope.launch {
        loadChapter((_uiState.value.chapterIndex + delta).coerceIn(0, _uiState.value.chapters.lastIndex), if (openAtEnd) Int.MAX_VALUE else 0)
    }

    fun jumpToChapter(index: Int) = viewModelScope.launch { loadChapter(index.coerceIn(0, _uiState.value.chapters.lastIndex), 0) }

    private suspend fun loadChapter(index: Int, position: Int) {
        if (index == _uiState.value.chapterIndex && _uiState.value.chapter != null) return
        _uiState.update { it.copy(loading = true) }
        val content = books.getChapter(bookUuid, index) ?: return
        val readerChapter = content.toReaderChapter()
        lastPosition = if (position == Int.MAX_VALUE) readerChapter.contentParagraphs().lastOrNull()?.index ?: 0 else position
        _uiState.update { it.copy(chapter = readerChapter, chapterIndex = index, restorePosition = lastPosition, loading = false) }
        savePosition(lastPosition)
    }

    fun savePosition(position: Int, chapterComplete: Boolean = false) {
        val state = _uiState.value
        val chapter = state.chapter ?: return
        val content = chapter.contentParagraphs()
        val currentOffset = content.indexOfLast { it.index <= position }.coerceAtLeast(0)
        val previousOffset = content.indexOfLast { it.index <= lastPosition }.coerceAtLeast(0)
        val safePosition = content.getOrNull(currentOffset)?.index ?: 0
        if (content.isNotEmpty() && currentOffset > previousOffset) {
            sessionCharacters += content.subList(previousOffset, currentOffset).sumOf { it.text.length }.toLong()
        }
        lastPosition = safePosition
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

    fun finishSession() {
        if (!sessionFinished.compareAndSet(false, true)) return
        val duration = SystemClock.elapsedRealtime() - sessionStart
        viewModelScope.launch(Dispatchers.IO) { stats.recordSession(bookUuid, duration, sessionCharacters) }
    }
}

private fun ChapterContent.toReaderChapter() = ReaderChapter(chapter.id, chapter.bookUuid, chapter.title, chapter.index, paragraphs)
