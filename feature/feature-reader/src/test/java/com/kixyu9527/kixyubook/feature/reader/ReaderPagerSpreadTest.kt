package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.reader.engine.ReaderPage
import org.junit.Assert.assertEquals
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
            neighbourLeafCount = 2,
        )

        val nextSpread = buildReaderPagerSpreads(window, true).last()
        assertEquals(listOf(0, 1), nextSpread.items.map { it.pageIndex })
        assertEquals("3:spread:0", nextSpread.key)
    }

    @Test
    fun nextSpreadKeyDoesNotChangeWhenRightLeafFinishesLoading() {
        fun nextSpread(nextPages: List<ReaderPage>): ReaderPagerSpread {
            val window = buildReaderPagerWindow(
                currentChapterIndex = 2,
                currentPages = chapterPages(chapter = 2, count = 2).mapNotNull { it.page },
                previousPages = emptyList(),
                nextPages = nextPages,
                hasPrevious = false,
                hasNext = true,
                currentPlaceholderPageIndex = 0,
                chapterCount = 4,
                neighbourLeafCount = 2,
            )
            return buildReaderPagerSpreads(window, true).last()
        }

        val pending = nextSpread(emptyList())
        val loaded = nextSpread(chapterPages(chapter = 3, count = 3).mapNotNull { it.page })

        assertEquals("3:spread:0", pending.key)
        assertEquals(pending.key, loaded.key)
        assertEquals(1, pending.items.size)
        assertEquals(2, loaded.items.size)
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
            neighbourLeafCount = 2,
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
            neighbourLeafCount = 2,
        )

        val evenPreviousSpread = buildReaderPagerSpreads(evenWindow, true).first {
            it.items.first().chapterIndex == 1
        }
        val oddPreviousSpread = buildReaderPagerSpreads(oddWindow, true).first {
            it.items.first().chapterIndex == 1
        }
        assertEquals(listOf(2, 3), evenPreviousSpread.items.map { it.pageIndex })
        assertEquals(listOf(4), oddPreviousSpread.items.map { it.pageIndex })
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
