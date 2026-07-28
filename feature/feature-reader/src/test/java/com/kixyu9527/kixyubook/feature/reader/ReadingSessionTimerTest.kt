package com.kixyu9527.kixyubook.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingSessionTimerTest {
    @Test
    fun countsOnlyActiveReaderIntervals() {
        var now = 1_000L
        val timer = ReadingSessionTimer { now }

        timer.setActive(true)
        now += 2_500
        timer.setActive(false)
        now += 20_000 // App remains open outside the resumed reader.
        timer.setActive(true)
        now += 1_500

        assertEquals(4_000L, timer.finish())
    }

    @Test
    fun repeatedLifecycleEventsDoNotDoubleCount() {
        var now = 0L
        val timer = ReadingSessionTimer { now }

        timer.setActive(true)
        now += 500
        timer.setActive(true)
        now += 500
        timer.setActive(false)
        timer.setActive(false)

        assertEquals(1_000L, timer.finish())
    }
}
