package com.kixyu9527.kixyubook.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryActivityClockTest {
    @Test
    fun next_isNewerThanFutureTimestampAlreadyPresentInLibrary() {
        val clock = LibraryActivityClock(currentTimeMillis = { 1_000L })

        clock.observe(2_000L)

        assertEquals(2_001L, clock.next())
    }

    @Test
    fun next_usesWallClockWhenItIsNewestAndRemainsStrictlyIncreasing() {
        val clock = LibraryActivityClock(currentTimeMillis = { 2_000L })

        clock.observe(1_000L)

        assertEquals(2_000L, clock.next())
        assertEquals(2_001L, clock.next())
    }

    @Test
    fun observingOlderActivityNeverMovesClockBackwards() {
        val clock = LibraryActivityClock(currentTimeMillis = { 1_000L })

        clock.observe(3_000L)
        clock.observe(2_000L)

        assertEquals(3_001L, clock.next())
    }
}
