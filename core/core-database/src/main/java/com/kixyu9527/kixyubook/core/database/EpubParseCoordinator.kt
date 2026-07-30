package com.kixyu9527.kixyubook.core.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializes EPUB XHTML parsing and lets an interactive chapter request preempt silent indexing.
 * A background parse is cancelled cooperatively; the worker then reloads its durable checkpoint.
 */
@Singleton
class EpubParseCoordinator @Inject constructor() {
    private val parserMutex = Mutex()
    private val interactiveWaiters = AtomicInteger(0)
    private val readerInteractionActive = AtomicBoolean(false)
    private val appAnimationActive = AtomicBoolean(false)
    private val activeBackground = AtomicReference<Deferred<Unit>?>(null)
    private val backgroundDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread({
            // android.jar throws in local JVM tests; production devices apply this scheduler hint.
            runCatching {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            }
            task.run()
        }, "epub-silent-index").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    fun setReaderInteractionActive(active: Boolean) {
        readerInteractionActive.set(active)
        if (active) activeBackground.get()?.cancel()
    }

    fun setAppAnimationActive(active: Boolean) {
        appAnimationActive.set(active)
        if (active) activeBackground.get()?.cancel()
    }

    private fun backgroundPaused(): Boolean =
        readerInteractionActive.get() || appAnimationActive.get()

    suspend fun <T> interactive(block: suspend () -> T): T {
        interactiveWaiters.incrementAndGet()
        try {
            activeBackground.get()?.cancelAndJoin()
            return parserMutex.withLock { block() }
        } finally {
            interactiveWaiters.decrementAndGet()
        }
    }

    /** Returns false when an interactive request preempted this unit of background work. */
    suspend fun background(block: suspend () -> Unit): Boolean = coroutineScope {
        while (interactiveWaiters.get() > 0 || backgroundPaused()) {
            delay(PRIORITY_POLL_MILLIS)
        }
        val task = async {
            parserMutex.withLock {
                if (interactiveWaiters.get() > 0 || backgroundPaused()) {
                    throw BackgroundPreempted()
                }
                withContext(backgroundDispatcher) { block() }
            }
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

    private companion object {
        const val PRIORITY_POLL_MILLIS = 12L
    }
}
