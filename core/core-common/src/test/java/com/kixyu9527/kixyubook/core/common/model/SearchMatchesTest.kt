package com.kixyu9527.kixyubook.core.common.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatchesTest {
    @Test
    fun returnsEveryNonOverlappingRangeInReadingOrder() {
        val text = "needle 和 NEEDLE，还有一个 needle"
        val matches = text.searchMatches("needle")

        assertEquals(3, matches.size)
        assertTrue(matches.all { text.substring(it.start, it.start + it.length).equals("needle", true) })
        assertEquals(matches.sortedBy { it.start }, matches)
    }

    @Test
    fun overlappingOccurrencesAreNotCountedTwice() {
        // "aaaa" contains "aa" at 0 and 2 (non-overlapping), and no third full window.
        assertEquals(2, "aaaa".searchMatches("aa").size)
        assertEquals(1, "aaa".searchMatches("aa").size)
    }

    @Test
    fun blankQueryHasNoMatches() {
        assertTrue("anything".searchMatches("").isEmpty())
    }
}
