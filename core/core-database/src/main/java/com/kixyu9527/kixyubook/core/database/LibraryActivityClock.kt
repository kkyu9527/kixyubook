package com.kixyu9527.kixyubook.core.database

import java.util.concurrent.atomic.AtomicLong

/**
 * Produces activity timestamps that remain ordered when cloud data or a corrected device clock
 * has placed existing library records ahead of the current wall clock.
 */
internal class LibraryActivityClock(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val latest = AtomicLong()

    fun observe(timestamp: Long) {
        latest.updateAndGet { previous -> maxOf(previous, timestamp) }
    }

    fun next(): Long = latest.updateAndGet { previous ->
        val current = currentTimeMillis()
        when {
            previous < current -> current
            previous < Long.MAX_VALUE -> previous + 1
            else -> Long.MAX_VALUE
        }
    }
}
