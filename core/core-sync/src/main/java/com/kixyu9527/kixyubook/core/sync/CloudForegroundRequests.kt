package com.kixyu9527.kixyubook.core.sync

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Full sync yields at durable object boundaries; an open request never waits for the library. */
internal class CloudForegroundRequests {
    private val waiting = AtomicInteger()
    private val requestedBooks = ConcurrentHashMap<String, AtomicInteger>()

    suspend fun <T> prioritize(bookUuid: String? = null, block: suspend () -> T): T {
        bookUuid?.let { requestedBooks.compute(it) { _, count -> (count ?: AtomicInteger()).apply { incrementAndGet() } } }
        waiting.incrementAndGet()
        return try { block() } finally {
            waiting.decrementAndGet()
            bookUuid?.let { requestedBooks.computeIfPresent(it) { _, count -> count.takeIf { it.decrementAndGet() > 0 } } }
        }
    }

    fun checkpoint() {
        if (waiting.get() > 0) throw CloudSyncYieldedException()
    }

    fun checkpoint(bookUuid: String) {
        // Joining the file already being downloaded must not cancel and restart that file.
        if ((requestedBooks[bookUuid]?.get() ?: 0) == 0) checkpoint()
    }
}

/** Schedules a fresh continuation, not exponential backoff or an authorization-failure prompt. */
internal class CloudSyncYieldedException : Exception("sync yielded to a foreground book download")

/** Inherited only by background transfers; retries/chunks are also safe preemption boundaries. */
internal class BackgroundSyncCheckpoint(val check: () -> Unit) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<BackgroundSyncCheckpoint>
}
