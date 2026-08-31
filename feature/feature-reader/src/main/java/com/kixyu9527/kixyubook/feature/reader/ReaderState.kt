package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.runtime.Immutable
import com.kixyu9527.kixyubook.core.common.model.Book
import com.kixyu9527.kixyubook.core.common.model.BookSearchResult
import com.kixyu9527.kixyubook.core.common.model.Bookmark
import com.kixyu9527.kixyubook.core.common.model.Chapter
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.TextCorrection
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.reader.engine.ReaderChapter

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val chapter: ReaderChapter? = null,
    val prefetchedChapters: Map<Int, ReaderChapter> = emptyMap(),
    val chapterIndex: Int = 0,
    val restorePosition: Int = 0,
    val restoreCharOffset: Int = 0,
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

@Immutable
data class ReaderPositionState(
    val paragraphIndex: Int = 0,
    val charOffset: Int = 0,
)

/**
 * Stable projection used by the text renderer. Bookmarks, corrections, transient loading labels
 * and the frequently updated progress cursor intentionally do not invalidate measured pages.
 */
@Immutable
internal data class ReaderContentState(
    val book: Book?,
    val chapters: List<Chapter>,
    val chapter: ReaderChapter?,
    val prefetchedChapters: Map<Int, ReaderChapter>,
    val chapterIndex: Int,
    val restorePosition: Int,
    val restoreCharOffset: Int,
    val settings: ReaderSettings,
    val fontPath: String?,
    val searchQuery: String,
    val searchResults: List<BookSearchResult>,
    val selectedSearchIndex: Int,
    val navigationVersion: Int,
)

internal fun ReaderUiState.toReaderContentState() = ReaderContentState(
    book = book,
    chapters = chapters,
    chapter = chapter,
    prefetchedChapters = prefetchedChapters,
    chapterIndex = chapterIndex,
    restorePosition = restorePosition,
    restoreCharOffset = restoreCharOffset,
    settings = settings,
    fontPath = fontPath,
    searchQuery = searchQuery,
    searchResults = searchResults,
    selectedSearchIndex = selectedSearchIndex,
    navigationVersion = navigationVersion,
)
