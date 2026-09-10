package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.Chapter

internal enum class ReaderLocationSource { DIRECTORY, BOOKMARK, ANNOTATION, SEARCH, DOCUMENT_LINK, HISTORY, RESTORE }

/** Source chapter indexes are not necessarily positions in the displayed directory. */
internal data class ReaderLocationRequest(
    val chapterIndex: Int,
    val paragraphIndex: Int = 0,
    val charOffset: Int = 0,
    val source: ReaderLocationSource,
    val isChapterPosition: Boolean = false,
    val rememberOrigin: Boolean = true,
) {
    fun resolve(chapters: List<Chapter>): ReaderLocation? {
        if (chapters.isEmpty()) return null
        val position = if (isChapterPosition) chapterIndex else {
            chapters.indexOfFirst { it.index == chapterIndex }.takeIf { it >= 0 } ?: chapterIndex
        }
        return ReaderLocation(position.coerceIn(chapters.indices), paragraphIndex.coerceAtLeast(0), charOffset.coerceAtLeast(0))
    }
}
