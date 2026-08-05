package com.kixyu9527.kixyubook.core.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveRetryAfterTest {
    @Test
    fun deltaSecondsAreConvertedToMilliseconds() {
        assertEquals(3_000L, parseRetryAfterMillis("3", nowMillis = 0L))
    }

    @Test
    fun httpDateIsMeasuredFromCurrentTime() {
        val now = Instant.parse("2026-08-05T00:00:00Z").toEpochMilli()
        assertEquals(
            5_000L,
            parseRetryAfterMillis("Wed, 05 Aug 2026 00:00:05 GMT", nowMillis = now),
        )
    }

    @Test
    fun invalidHeaderDoesNotDelayTheRequest() {
        assertNull(parseRetryAfterMillis("soon", nowMillis = 0L))
    }
}
