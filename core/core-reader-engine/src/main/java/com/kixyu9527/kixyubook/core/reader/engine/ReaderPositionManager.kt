package com.kixyu9527.kixyubook.core.reader.engine

class ReaderPositionManager {
    fun pageFor(pages: List<ReaderPage>, paragraphIndex: Int): Int =
        pages.indexOfLast { it.startParagraph <= paragraphIndex }.coerceAtLeast(0)

    fun bookFraction(
        chapterIndex: Int,
        chapterCount: Int,
        paragraphOffset: Int,
        paragraphCount: Int,
        chapterComplete: Boolean,
    ): Float {
        val safeChapterCount = chapterCount.coerceAtLeast(1)
        val safeChapterIndex = chapterIndex.coerceIn(0, safeChapterCount - 1)
        val chapterFraction = if (chapterComplete) {
            1f
        } else {
            paragraphOffset.coerceAtLeast(0).toFloat() / paragraphCount.coerceAtLeast(1)
        }
        return ((safeChapterIndex + chapterFraction.coerceIn(0f, 1f)) / safeChapterCount)
            .coerceIn(0f, 1f)
    }
}
