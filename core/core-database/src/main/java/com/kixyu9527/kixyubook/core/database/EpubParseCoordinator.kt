package com.kixyu9527.kixyubook.core.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assigns EPUB work to three priority tiers.
 *
 * The chapter explicitly requested by the reader is never paused by an animation: it runs off the
 * main thread and is required before the user can read. Animations and gestures preempt neighbour
 * prefetch, while the full-book search index remains paused for the whole visible reader session.
 */
@Singleton
class EpubParseCoordinator @Inject constructor() {
    private val interactiveWaiters = AtomicInteger(0)
    private val readerInteractionActive = AtomicBoolean(false)
    private val readerSessionActive = AtomicBoolean(false)
    private val appAnimationActive = AtomicBoolean(false)
    private val activeBackground = AtomicReference<Deferred<Unit>?>(null)
    private val activePrefetch = AtomicReference<Job?>(null)
    private val backgroundDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread({
            // android.jar throws in local JVM tests; production devices apply this scheduler hint.
            runCatching {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST)
            }
            task.run()
        }, "epub-silent-index").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val prefetchDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread({
            runCatching {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            }
            task.run()
        }, "epub-reader-prefetch").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    fun setReaderInteractionActive(active: Boolean) {
        readerInteractionActive.set(active)
        if (active) {
            activePrefetch.get()?.cancel()
            activeBackground.get()?.cancel()
        }
    }

    fun setReaderSessionActive(active: Boolean) {
        readerSessionActive.set(active)
        if (active) activeBackground.get()?.cancel()
    }

    fun setAppAnimationActive(active: Boolean) {
        appAnimationActive.set(active)
        if (active) {
            activePrefetch.get()?.cancel()
            activeBackground.get()?.cancel()
        }
    }

    private fun backgroundPaused(): Boolean =
        readerSessionActive.get() || readerInteractionActive.get() || appAnimationActive.get()

    private fun prefetchPaused(): Boolean =
        readerInteractionActive.get() || appAnimationActive.get()

    suspend fun <T> interactive(block: suspend () -> T): T {
        interactiveWaiters.incrementAndGet()
        try {
            // Background indexing owns a separate parser instance. Cancellation can therefore be
            // cooperative without making a page turn wait for a blocking XML parse to unwind.
            activePrefetch.get()?.cancel()
            activeBackground.get()?.cancel()
            return block()
        } finally {
            interactiveWaiters.decrementAndGet()
        }
    }

    /**
     * Runs a speculative neighbouring chapter only after animation and direct interaction settle.
     * A later user chapter request cancels this job and starts immediately on the normal IO pool.
     */
    suspend fun <T> prefetch(block: suspend () -> T): T = coroutineScope {
        while (interactiveWaiters.get() > 0 || prefetchPaused()) {
            delay(PRIORITY_POLL_MILLIS)
        }
        val task = async {
            if (interactiveWaiters.get() > 0 || prefetchPaused()) {
                throw PrefetchPreempted()
            }
            withContext(prefetchDispatcher) { block() }
        }
        activePrefetch.set(task)
        try {
            task.await()
        } finally {
            activePrefetch.compareAndSet(task, null)
        }
    }

    /** Returns false when an interactive request preempted this unit of background work. */
    suspend fun background(block: suspend () -> Unit): Boolean = coroutineScope {
        while (interactiveWaiters.get() > 0 || backgroundPaused()) {
            delay(PRIORITY_POLL_MILLIS)
        }
        val task = async {
            if (interactiveWaiters.get() > 0 || backgroundPaused()) {
                throw BackgroundPreempted()
            }
            withContext(backgroundDispatcher) { block() }
        }
        activeBackground.set(task)
        try {
            task.await()
            true
        } catch (error: CancellationException) {
            if (!currentCoroutineContext().isActive) throw error
            false
        } finally {
            activeBackground.compareAndSet(task, null)
        }
    }

    private class BackgroundPreempted : CancellationException()
    private class PrefetchPreempted : CancellationException()

    private companion object {
        const val PRIORITY_POLL_MILLIS = 12L
    }
}
