package com.kixyu9527.kixyubook.core.reader.engine

import kotlin.math.roundToInt

class ReaderLayoutEngine {
    fun paginate(chapter: ReaderChapter, spec: ReaderLayoutSpec): List<ReaderPage> {
        val usableWidth = (spec.viewportWidthDp - spec.horizontalMarginDp * 2).coerceAtLeast(160f)
        // Reserve the renderer's compact page header, footer and vertical padding.
        // The old estimate used the full viewport and could lay the last line under navigation bars.
        val usableHeight = (spec.viewportHeightDp - PAGE_CHROME_HEIGHT_DP).coerceAtLeast(220f)
        val charactersPerLine = (usableWidth / (spec.fontSizeSp * 0.95f)).coerceAtLeast(8f)
        val linesPerPage = (usableHeight / (spec.fontSizeSp * spec.lineHeightMultiplier)).coerceAtLeast(8f)
        val normalLimit = (charactersPerLine * linesPerPage).roundToInt().coerceIn(120, 1600)
        val paragraphSpacingCost = (charactersPerLine * PARAGRAPH_SPACING_LINES).roundToInt().coerceAtLeast(2)
        val pages = mutableListOf<ReaderPage>()
        var blocks = mutableListOf<DocumentBlock>()
        var used = 0
        var opening = true

        fun currentLimit() = if (opening) (normalLimit * 0.56f).roundToInt() else normalLimit
        fun flush() {
            if (blocks.isEmpty()) return
            pages += ReaderPage(pages.size, chapter.index, chapter.title, opening, blocks.toList())
            blocks = mutableListOf(); used = 0; opening = false
        }

        chapter.contentParagraphs().forEach { paragraph ->
            var remaining = paragraph.text
            var continuation = false
            while (remaining.isNotEmpty()) {
                val available = (currentLimit() - used).coerceAtLeast(1)
                // Use the remainder of the page when it can hold useful text. The old
                // implementation moved every long paragraph to a new page and produced
                // visibly half-empty pages after a short paragraph.
                if (used > 0 && remaining.length + paragraphSpacingCost > available &&
                    available < charactersPerLine * MIN_SPLIT_LINES
                ) {
                    flush(); continue
                }
                val part = remaining.take(available)
                val reachesParagraphEnd = part.length == remaining.length
                blocks += DocumentBlock(
                    paragraph.index,
                    paragraph.text,
                    part,
                    continuation,
                    bottomSpacing = reachesParagraphEnd && part.length + paragraphSpacingCost <= available,
                )
                used += part.length + paragraphSpacingCost
                remaining = remaining.drop(part.length)
                continuation = true
                if (remaining.isNotEmpty() || used >= currentLimit()) flush()
            }
        }
        flush()
        return pages.ifEmpty { listOf(ReaderPage(0, chapter.index, chapter.title, true, emptyList())) }
    }

    private companion object {
        const val PAGE_CHROME_HEIGHT_DP = 92f
        const val PARAGRAPH_SPACING_LINES = 0.55f
        const val MIN_SPLIT_LINES = 2f
    }
}

class ReaderPositionManager {
    fun pageFor(pages: List<ReaderPage>, paragraphIndex: Int): Int =
        pages.indexOfLast { it.startParagraph <= paragraphIndex }.coerceAtLeast(0)

    fun positionFor(page: ReaderPage): ReaderPosition = ReaderPosition(page.chapterIndex, page.startParagraph)

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
