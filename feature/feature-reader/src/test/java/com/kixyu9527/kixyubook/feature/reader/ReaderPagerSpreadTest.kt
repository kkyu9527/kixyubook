package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.reader.engine.ReaderPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPagerSpreadTest {
    @Test
    fun phoneLayoutKeepsEveryLeafIndependent() {
        val spreads = buildReaderPagerSpreads(chapterPages(chapter = 2, count = 3), false)

        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), spreads.pageIndexes())
    }

    @Test
    fun tabletLandscapeGroupsConsecutiveLeavesIntoPageTurns() {
        val spreads = buildReaderPagerSpreads(chapterPages(chapter = 2, count = 5), true)

        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), spreads.pageIndexes())
    }

    @Test
    fun resizingKeepsTheSameLogicalLeafKey() {
        val pages = chapterPages(chapter = 2, count = 5)
        val phone = buildReaderPagerSpreads(pages, false)
        val tablet = buildReaderPagerSpreads(pages, true)
        val currentLeafKey = phone[3].items.single().key

        val resizedSpread = tablet.single { spread ->
            spread.items.any { item -> item.key == currentLeafKey }
        }

        assertEquals(listOf(2, 3), resizedSpread.items.map { it.pageIndex })
        assertEquals(currentLeafKey, resizedSpread.items.last().key)
    }

    @Test
    fun spreadNeverPairsLeavesAcrossChapterBoundary() {
        val items = chapterPages(chapter = 2, count = 1) + chapterPages(chapter = 3, count = 2)

        val spreads = buildReaderPagerSpreads(items, true)

        assertEquals(listOf(listOf(0), listOf(0, 1)), spreads.pageIndexes())
        assertEquals(listOf(2, 3), spreads.map { it.items.first().chapterIndex })
    }

    @Test
    fun landscapeWindowKeepsNextChapterFirstSpreadReady() {
        val window = buildReaderPagerWindow(
            currentChapterIndex = 2,
            currentPages = chapterPages(chapter = 2, count = 3).mapNotNull { it.page },
            previousPages = emptyList(),
            nextPages = chapterPages(chapter = 3, count = 4).mapNotNull { it.page },
            hasPrevious = false,
            hasNext = true,
            currentPlaceholderPageIndex = 0,
            chapterCount = 4,
        )

        val nextSpreads = buildReaderPagerSpreads(window, true).filter {
            it.items.first().chapterIndex == 3
        }
        val nextSpread = nextSpreads.first()
        assertEquals(listOf(0, 1), nextSpread.items.map { it.pageIndex })
        assertEquals("3:spread:0", nextSpread.key)
        assertEquals(listOf(2, 3), nextSpreads.last().items.map { it.pageIndex })
    }

    @Test
    fun rapidSecondTurnCanReachNextChapterSecondPageBeforeRecentering() {
        val window = buildReaderPagerWindow(
            currentChapterIndex = 2,
            currentPages = chapterPages(chapter = 2, count = 1).mapNotNull { it.page },
            previousPages = chapterPages(chapter = 1, count = 2).mapNotNull { it.page },
            nextPages = chapterPages(chapter = 3, count = 4).mapNotNull { it.page },
            hasPrevious = true,
            hasNext = true,
            currentPlaceholderPageIndex = 0,
            chapterCount = 4,
        )
        val spreads = buildReaderPagerSpreads(window, false)

        assertEquals(
            listOf("3:0", "3:1", "3:2", "3:3"),
            spreads.filter { it.items.first().chapterIndex == 3 }.map { it.key },
        )
    }

    @Test
    fun partialCurrentPaginationCannotExposeChapterBoundary() {
        val window = buildReaderPagerWindow(
            currentChapterIndex = 1,
            currentPages = chapterPages(chapter = 1, count = 4).mapNotNull { it.page },
            previousPages = emptyList(),
            nextPages = chapterPages(chapter = 2, count = 2).mapNotNull { it.page },
            hasPrevious = false,
            hasNext = true,
            currentPagesComplete = false,
            currentPlaceholderPageIndex = 0,
            chapterCount = 3,
        )

        assertTrue(window.all { it.chapterIndex == 1 })
        assertTrue(window.all { it.pageCount == 0 })
    }

    @Test
    fun nextChapterAppearsOnlyAfterItsRealPagesAreReady() {
        fun nextSpreads(nextPages: List<ReaderPage>): List<ReaderPagerSpread> {
            val window = buildReaderPagerWindow(
                currentChapterIndex = 2,
                currentPages = chapterPages(chapter = 2, count = 2).mapNotNull { it.page },
                previousPages = emptyList(),
                nextPages = nextPages,
                hasPrevious = false,
                hasNext = true,
                currentPlaceholderPageIndex = 0,
                chapterCount = 4,
            )
            return buildReaderPagerSpreads(window, true)
        }

        val pending = nextSpreads(emptyList()).last()
        val loaded = nextSpreads(chapterPages(chapter = 3, count = 3).mapNotNull { it.page })
            .first { it.items.first().chapterIndex == 3 }

        assertEquals("2:spread:0", pending.key)
        assertTrue(pending.items.all { it.chapterIndex == 2 && it.page != null })
        assertEquals("3:spread:0", loaded.key)
        assertEquals(2, loaded.items.size)
        assertTrue(loaded.items.all { it.chapterIndex == 3 && it.page != null })
    }

    @Test
    fun landscapeWindowKeepsCorrectPreviousFinalSpreadParity() {
        val evenWindow = buildReaderPagerWindow(
            currentChapterIndex = 2,
            currentPages = chapterPages(chapter = 2, count = 2).mapNotNull { it.page },
            previousPages = chapterPages(chapter = 1, count = 4).mapNotNull { it.page },
            nextPages = emptyList(),
            hasPrevious = true,
            hasNext = false,
            currentPlaceholderPageIndex = 0,
            chapterCount = 3,
        )
        val oddWindow = buildReaderPagerWindow(
            currentChapterIndex = 2,
            currentPages = chapterPages(chapter = 2, count = 2).mapNotNull { it.page },
            previousPages = chapterPages(chapter = 1, count = 5).mapNotNull { it.page },
            nextPages = emptyList(),
            hasPrevious = true,
            hasNext = false,
            currentPlaceholderPageIndex = 0,
            chapterCount = 3,
        )

        val evenPreviousSpread = buildReaderPagerSpreads(evenWindow, true).last {
            it.items.first().chapterIndex == 1
        }
        val oddPreviousSpread = buildReaderPagerSpreads(oddWindow, true).last {
            it.items.first().chapterIndex == 1
        }
        assertEquals(listOf(2, 3), evenPreviousSpread.items.map { it.pageIndex })
        assertEquals(listOf(4), oddPreviousSpread.items.map { it.pageIndex })
    }

    @Test
    fun chapterButtonsTargetFirstPageInsideStablePagerWindow() {
        val window = buildReaderPagerWindow(
            currentChapterIndex = 2,
            currentPages = chapterPages(chapter = 2, count = 3).mapNotNull { it.page },
            previousPages = chapterPages(chapter = 1, count = 4).mapNotNull { it.page },
            nextPages = chapterPages(chapter = 3, count = 2).mapNotNull { it.page },
            hasPrevious = true,
            hasNext = true,
            currentPlaceholderPageIndex = 0,
            chapterCount = 4,
        )
        val spreads = buildReaderPagerSpreads(window, false)

        assertEquals(
            "1:0",
            spreads[directChapterTargetSpreadIndex(spreads, 2, -1)].key,
        )
        assertEquals(
            "3:0",
            spreads[directChapterTargetSpreadIndex(spreads, 2, 1)].key,
        )
        assertEquals(false, chapterTransitionOpensAtEnd(-1, directChapterTurn = true))
        assertEquals(true, chapterTransitionOpensAtEnd(-1, directChapterTurn = false))
        assertEquals(false, chapterTransitionOpensAtEnd(1, directChapterTurn = false))
    }

    @Test
    fun idleCoverAnimationTracksStableKeyAcrossWindowRecentering() {
        val before = buildReaderPagerSpreads(
            chapterPages(chapter = 1, count = 4) +
                chapterPages(chapter = 2, count = 3).take(1),
            false,
        )
        val settledKey = before[4].key
        val after = buildReaderPagerSpreads(
            chapterPages(chapter = 1, count = 4).takeLast(1) +
                chapterPages(chapter = 2, count = 3) +
                chapterPages(chapter = 3, count = 1),
            false,
        )

        assertEquals(
            after.indexOfFirst { it.key == settledKey },
            readerPagerVisualCurrentIndex(after, settledKey, pagerCurrentPage = 4, scrolling = false),
        )
        assertEquals(
            4,
            readerPagerVisualCurrentIndex(after, settledKey, pagerCurrentPage = 4, scrolling = true),
        )
    }

    private fun chapterPages(chapter: Int, count: Int): List<ReaderPagerItem> =
        List(count) { pageIndex ->
            ReaderPagerItem(
                chapterIndex = chapter,
                pageIndex = pageIndex,
                pageCount = count,
                page = ReaderPage(
                    index = pageIndex,
                    chapterIndex = chapter,
                    chapterTitle = "第${chapter}章",
                    isChapterOpening = pageIndex == 0,
                    blocks = emptyList(),
                ),
            )
        }

    private fun List<ReaderPagerSpread>.pageIndexes(): List<List<Int>> =
        map { spread -> spread.items.map { it.pageIndex } }
}
