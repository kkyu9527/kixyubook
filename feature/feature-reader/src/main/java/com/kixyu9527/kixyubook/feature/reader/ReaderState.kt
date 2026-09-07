package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.runtime.Immutable
import com.kixyu9527.kixyubook.core.common.model.Book
import com.kixyu9527.kixyubook.core.common.model.BookSearchResult
import com.kixyu9527.kixyubook.core.common.model.BookSearchStage
import com.kixyu9527.kixyubook.core.common.model.Bookmark
import com.kixyu9527.kixyubook.core.common.model.Chapter
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderAnnotation
import com.kixyu9527.kixyubook.core.common.model.EpubLinkResult
import com.kixyu9527.kixyubook.core.common.model.EpubNavigationEntry
import com.kixyu9527.kixyubook.core.common.model.TextCorrection
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.reader.engine.ReaderChapter

enum class ReaderLoadStage {
    OPENING_BOOK,
    READING_CONTENT,
    PAGINATING_FIRST_PAGE,
}

enum class ReaderSearchScope { BOOK, CURRENT_CHAPTER }

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val epubNavigation: List<EpubNavigationEntry> = emptyList(),
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
    val annotations: List<ReaderAnnotation> = emptyList(),
    val searchQuery: String = "",
    val searchScope: ReaderSearchScope = ReaderSearchScope.BOOK,
    val searchHistory: List<String> = emptyList(),
    val searchResults: List<BookSearchResult> = emptyList(),
    val selectedSearchIndex: Int = -1,
    val searchReturnAvailable: Boolean = false,
    val searchInProgress: Boolean = false,
    val searchProgress: Float = 0f,
    val searchStage: BookSearchStage = BookSearchStage.INDEXING,
    val searchCompleted: Int = 0,
    val searchTotal: Int = 0,
    val searchError: String? = null,
    val canNavigateBack: Boolean = false,
    val canNavigateForward: Boolean = false,
    val epubFootnote: EpubLinkResult.Footnote? = null,
    val navigationVersion: Int = 0,
    val loading: Boolean = true,
    val loadStage: ReaderLoadStage? = ReaderLoadStage.OPENING_BOOK,
    val error: String? = null,
)

@Immutable
data class ReaderPositionState(
    val paragraphIndex: Int = 0,
    val charOffset: Int = 0,
    /** Last source paragraph represented by the visible page or scroll viewport. */
    val visibleEndParagraphIndex: Int = paragraphIndex,
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
    val annotations: List<ReaderAnnotation>,
    val selectedSearchIndex: Int,
    val navigationVersion: Int,
)

/** Directory enrichment changes list positions, never the identity of an open chapter. */
internal fun ReaderUiState.withChapters(updated: List<Chapter>): ReaderUiState {
    val activeIndex = updated.indexOfFirst { it.id == chapter?.id }
        .takeIf { it >= 0 } ?: chapterIndex.coerceIn(0, updated.lastIndex.coerceAtLeast(0))
    val positions = updated.mapIndexed { index, item -> item.id to index }.toMap()
    return copy(
        chapters = updated,
        chapterIndex = activeIndex,
        prefetchedChapters = prefetchedChapters.values.mapNotNull { content ->
            positions[content.id]?.let { it to content }
        }.toMap(),
    )
}

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
    annotations = annotations,
    selectedSearchIndex = selectedSearchIndex,
    navigationVersion = navigationVersion,
)
