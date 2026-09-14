package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.Chapter

internal enum class ReaderLocationSource { DIRECTORY, BOOKMARK, ANNOTATION, SEARCH, DOCUMENT_LINK, HISTORY, RESTORE }

/**
 * Distinguishes the two meanings an integer chapter number used to carry: a source chapter `index`
 * (stable across directory reordering) versus a position in the displayed directory. Keeping them as
 * separate types makes it impossible to pass one where the other is expected.
 */
internal sealed interface ChapterRef {
    data class SourceIndex(val value: Int) : ChapterRef
    data class DirectoryPosition(val value: Int) : ChapterRef
}

/** A requested reader location, resolved against the current directory before it is applied. */
internal data class ReaderLocationRequest(
    val chapter: ChapterRef,
    val paragraphIndex: Int = 0,
    val charOffset: Int = 0,
    val source: ReaderLocationSource,
    val rememberOrigin: Boolean = true,
) {
    /**
     * Resolves a request against the displayed directory, or returns null when it cannot be
     * located. An unknown source chapter index is deliberately *not* reused as a directory
     * position: doing so turned an unresolvable location into a legal but wrong page.
     */
    fun resolve(chapters: List<Chapter>): ReaderLocation? {
        if (chapters.isEmpty()) return null
        val position = when (chapter) {
            is ChapterRef.DirectoryPosition -> chapter.value.takeIf { it in chapters.indices } ?: return null
            is ChapterRef.SourceIndex ->
                chapters.indexOfFirst { it.index == chapter.value }.takeIf { it >= 0 } ?: return null
        }
        return ReaderLocation(position, paragraphIndex.coerceAtLeast(0), charOffset.coerceAtLeast(0))
    }
}

internal fun sourceLocation(
    chapterIndex: Int,
    paragraphIndex: Int = 0,
    charOffset: Int = 0,
    source: ReaderLocationSource,
    rememberOrigin: Boolean = true,
): ReaderLocationRequest = ReaderLocationRequest(
    chapter = ChapterRef.SourceIndex(chapterIndex),
    paragraphIndex = paragraphIndex,
    charOffset = charOffset,
    source = source,
    rememberOrigin = rememberOrigin,
)

internal fun directoryLocation(
    chapterPosition: Int,
    paragraphIndex: Int = 0,
    charOffset: Int = 0,
    source: ReaderLocationSource,
    rememberOrigin: Boolean = true,
): ReaderLocationRequest = ReaderLocationRequest(
    chapter = ChapterRef.DirectoryPosition(chapterPosition),
    paragraphIndex = paragraphIndex,
    charOffset = charOffset,
    source = source,
    rememberOrigin = rememberOrigin,
)
