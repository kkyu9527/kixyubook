package com.kixyu9527.kixyubook.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSearchHistoryCodecTest {
    @Test
    fun roundTripPreservesUnicodeWhitespaceAndDelimiters() {
        val values = listOf("灵魂 意志", "100%+成长", "第一行\n第二行")

        assertEquals(values, decodeSearchHistory(encodeSearchHistory(values)))
    }

    @Test
    fun decodeDeduplicatesAndCapsHistory() {
        val values = (0..12).map { "搜索$it" } + "搜索0"

        assertEquals(
            (0 until MAX_SEARCH_HISTORY).map { "搜索$it" },
            decodeSearchHistory(encodeSearchHistory(values)),
        )
    }

    @Test
    fun malformedEntriesDoNotDiscardValidHistory() {
        val encoded = encodeSearchHistory(listOf("有效搜索")) + "\n%not-valid"

        assertEquals(listOf("有效搜索"), decodeSearchHistory(encoded))
    }
}
