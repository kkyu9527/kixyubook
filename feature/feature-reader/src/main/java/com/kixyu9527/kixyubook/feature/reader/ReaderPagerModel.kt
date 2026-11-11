package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.reader.engine.ReaderPage

/**
 * Pure pager data model and window math. Keeping this separate from the Compose pager lets the
 * spread/window rules stay unit-testable and prevents [PagedReader] from growing into a god file.
 */

internal data class ReaderPagerItem(
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val page: ReaderPage?,
) {
    // Pager/LazyLayout keys participate in saveable state and therefore must be Bundle-compatible.
    val key = "${page?.chapterIndex ?: chapterIndex}:$pageIndex"
}

internal data class ReaderPagerSpread(
    val items: List<ReaderPagerItem>,
    val key: String,
)

/** Groups two consecutive leaves from the same chapter into one landscape page turn. */
internal fun buildReaderPagerSpreads(
    items: List<ReaderPagerItem>,
    twoPageSpread: Boolean,
): List<ReaderPagerSpread> {
    if (!twoPageSpread) return items.map { ReaderPagerSpread(listOf(it), it.key) }
    return buildList {
        var index = 0
        while (index < items.size) {
            val first = items[index]
            val second = items.getOrNull(index + 1)
            val pairable = first.page != null &&
                first.pageIndex >= 0 &&
                first.pageIndex % 2 == 0 &&
                second?.page != null &&
                second.chapterIndex == first.chapterIndex &&
                second.pageIndex == first.pageIndex + 1
            if (pairable) {
                add(readerPagerSpread(listOf(first, second)))
                index += 2
            } else {
                add(readerPagerSpread(listOf(first)))
                index++
            }
        }
    }
}

/** A right leaf may arrive after the left one; the spread identity must not change when it does. */
internal fun readerPagerSpread(items: List<ReaderPagerItem>): ReaderPagerSpread {
    val first = items.first()
    val spreadIndex = if (first.pageIndex >= 0) first.pageIndex / 2 else first.pageIndex
    return ReaderPagerSpread(
        items = items,
        key = "${first.page?.chapterIndex ?: first.chapterIndex}:spread:$spreadIndex",
    )
}

internal fun buildReaderPagerWindow(
    currentChapterIndex: Int,
    currentPages: List<ReaderPage>,
    previousPages: List<ReaderPage>,
    nextPages: List<ReaderPage>,
    hasPrevious: Boolean,
    hasNext: Boolean,
    currentPagesComplete: Boolean = true,
    currentPlaceholderPageIndex: Int,
    chapterCount: Int,
): List<ReaderPagerItem> = buildList {
    if (hasPrevious && currentChapterIndex > 0) {
        // Keep the previous chapter's complete lightweight page index in the stable Pager. Only
        // beyondViewportPageCount pages are composed, but the chapter button can now target its
        // first page without rebuilding the Pager around a new ViewModel chapter first.
        if (previousPages.isNotEmpty()) {
            previousPages.forEach { page ->
                add(
                    ReaderPagerItem(
                        chapterIndex = currentChapterIndex - 1,
                        pageIndex = page.index,
                        pageCount = previousPages.size,
                        page = page,
                    ),
                )
            }
        }
    }
    if (currentPages.isEmpty()) {
        add(
            ReaderPagerItem(
                chapterIndex = currentChapterIndex,
                pageIndex = currentPlaceholderPageIndex,
                pageCount = 0,
                page = null,
            ),
        )
    } else {
        currentPages.forEach { page ->
            add(
                ReaderPagerItem(
                    currentChapterIndex,
                    page.index,
                    if (currentPagesComplete) currentPages.size else 0,
                    page,
                ),
            )
        }
    }
    if (hasNext && currentChapterIndex < chapterCount - 1 && currentPagesComplete) {
        // Keep the complete next chapter index in the same Pager. HorizontalPager still composes
        // only its configured viewport neighbour, but a rapid second gesture can now target page
        // two (or later) without waiting for the ViewModel's chapter-window recentering frame.
        if (nextPages.isNotEmpty()) {
            nextPages.forEach { page ->
                add(
                    ReaderPagerItem(
                        chapterIndex = currentChapterIndex + 1,
                        pageIndex = page.index,
                        pageCount = nextPages.size,
                        page = page,
                    ),
                )
            }
        }
    }
}

/** Returns the first page of the adjacent chapter for an explicit chapter-skip action. */
internal fun directChapterTargetSpreadIndex(
    pagerSpreads: List<ReaderPagerSpread>,
    currentChapterIndex: Int,
    direction: Int,
): Int {
    if (direction == 0) return -1
    val targetChapterIndex = currentChapterIndex + if (direction < 0) -1 else 1
    return pagerSpreads.indexOfFirst { spread ->
        spread.items.any { item ->
            item.chapterIndex == targetChapterIndex && item.pageIndex == 0 && item.page != null
        }
    }
}

/** A backward page-boundary gesture continues from the prior chapter's end; a skip does not. */
internal fun chapterTransitionOpensAtEnd(direction: Int, directChapterTurn: Boolean): Boolean =
    direction < 0 && !directChapterTurn

internal fun readerPagerVisualCurrentIndex(
    pagerSpreads: List<ReaderPagerSpread>,
    settledSpreadKey: String,
    pagerCurrentPage: Int,
    scrolling: Boolean,
): Int {
    if (scrolling) return pagerCurrentPage.coerceIn(0, pagerSpreads.lastIndex.coerceAtLeast(0))
    return pagerSpreads.indexOfFirst { it.key == settledSpreadKey }
        .takeIf { it >= 0 }
        ?: pagerCurrentPage.coerceIn(0, pagerSpreads.lastIndex.coerceAtLeast(0))
}
