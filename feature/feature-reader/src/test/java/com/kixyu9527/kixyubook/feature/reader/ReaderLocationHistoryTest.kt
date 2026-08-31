package com.kixyu9527.kixyubook.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLocationHistoryTest {
    @Test
    fun backAndForwardPreserveExactPosition() {
        val history = ReaderLocationHistory()
        val first = ReaderLocation(1, 8, 12)
        val second = ReaderLocation(4, 3, 2)

        history.record(first, second)
        assertTrue(history.canGoBack)
        assertEquals(first, history.goBack(second))
        assertTrue(history.canGoForward)
        assertEquals(second, history.goForward(first))
    }

    @Test
    fun newJumpClearsForwardHistoryAndIgnoresNoOp() {
        val history = ReaderLocationHistory()
        val first = ReaderLocation(0, 0, 0)
        val second = ReaderLocation(1, 2, 0)
        val third = ReaderLocation(2, 4, 0)

        history.record(first, second)
        history.goBack(second)
        history.record(first, third)
        history.record(third, third)

        assertFalse(history.canGoForward)
        assertEquals(first, history.goBack(third))
    }
}
