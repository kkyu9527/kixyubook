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
    /**
     * Resolves a request against the displayed directory, or returns null when it cannot be
     * located. An unknown source chapter index is deliberately *not* reused as a directory
     * position: doing so turned an unresolvable location into a legal but wrong page.
     */
    fun resolve(chapters: List<Chapter>): ReaderLocation? {
        if (chapters.isEmpty()) return null
        val position = if (isChapterPosition) {
            chapterIndex.takeIf { it in chapters.indices } ?: return null
        } else {
            chapters.indexOfFirst { it.index == chapterIndex }.takeIf { it >= 0 } ?: return null
        }
        return ReaderLocation(position, paragraphIndex.coerceAtLeast(0), charOffset.coerceAtLeast(0))
    }
}
