package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.Paragraph
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind

class ReaderPositionManager {
    /**
     * Resolves a persisted text position to the first page that actually contains that paragraph.
     *
     * Comparing only page start positions is insufficient: a long paragraph can span several
     * pages, and EPUB images can share their neighbouring text paragraph's index. In both cases
     * `indexOfLast(start <= target)` lands after the searched text.
     */
    fun pageFor(
        pages: List<ReaderPage>,
        paragraphIndex: Int,
        searchQuery: String? = null,
        charOffset: Int = 0,
    ): Int {
        if (pages.isEmpty()) return 0
        val normalizedQuery = searchQuery?.trim().orEmpty()
        if (normalizedQuery.isNotEmpty()) {
            val matchingTextPage = pages.indexOfFirst { page ->
                page.blocks.any { block ->
                    block.kind == ParagraphKind.TEXT &&
                        block.paragraphIndex == paragraphIndex &&
                        block.visibleText.contains(normalizedQuery, ignoreCase = true)
                }
            }
            if (matchingTextPage >= 0) return matchingTextPage

            // A match may cross a pagination boundary. In that case neither visible block
            // contains the whole query, so locate the block containing the match's start offset.
            val matchingOffsetPage = pages.indexOfFirst { page ->
                page.blocks.any { block ->
                    if (block.kind != ParagraphKind.TEXT || block.paragraphIndex != paragraphIndex) {
                        false
                    } else {
                        val matchStart = block.fullText.indexOf(normalizedQuery, ignoreCase = true)
                        matchStart >= 0 && matchStart in block.textStart until
                            (block.textStart + block.visibleText.length.coerceAtLeast(1))
                    }
                }
            }
            if (matchingOffsetPage >= 0) return matchingOffsetPage
        }
        val safeCharOffset = charOffset.coerceAtLeast(0)
        val exactTextPage = pages.indexOfFirst { page ->
            page.blocks.any { block ->
                block.kind == ParagraphKind.TEXT &&
                    block.paragraphIndex == paragraphIndex &&
                    safeCharOffset >= block.textStart &&
                    safeCharOffset < block.textStart + block.visibleText.length.coerceAtLeast(1)
            }
        }
        if (exactTextPage >= 0) return exactTextPage

        // Pagination may skip whitespace between two fragments. Prefer the first fragment after
        // the persisted offset so a cross-device restore still lands at the same reading point.
        val followingFragmentPage = pages.indexOfFirst { page ->
            page.blocks.any { block ->
                block.kind == ParagraphKind.TEXT &&
                    block.paragraphIndex == paragraphIndex &&
                    block.textStart >= safeCharOffset
            }
        }
        if (followingFragmentPage >= 0) return followingFragmentPage

        val lastParagraphPage = pages.indexOfLast { page ->
            page.blocks.any { block ->
                block.kind == ParagraphKind.TEXT && block.paragraphIndex == paragraphIndex
            }
        }
        if (lastParagraphPage >= 0) return lastParagraphPage

        val followingTextPage = pages.indexOfFirst { page ->
            page.blocks.any { block ->
                block.kind == ParagraphKind.TEXT && block.paragraphIndex > paragraphIndex
            }
        }
        if (followingTextPage >= 0) return followingTextPage

        return pages.indexOfLast { page ->
            page.blocks.any { it.kind == ParagraphKind.TEXT }
        }.coerceAtLeast(0)
    }

    /** Returns the rendered content item matching a database paragraph position. */
    fun contentItemFor(paragraphs: List<Paragraph>, paragraphIndex: Int): Int {
        if (paragraphs.isEmpty()) return 0
        val exactTextItem = paragraphs.indexOfFirst { paragraph ->
            paragraph.kind == ParagraphKind.TEXT && paragraph.index == paragraphIndex
        }
        if (exactTextItem >= 0) return exactTextItem

        val followingTextItem = paragraphs.indexOfFirst { paragraph ->
            paragraph.kind == ParagraphKind.TEXT && paragraph.index > paragraphIndex
        }
        if (followingTextItem >= 0) return followingTextItem

        return paragraphs.indexOfLast { it.kind == ParagraphKind.TEXT }.coerceAtLeast(0)
    }

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
