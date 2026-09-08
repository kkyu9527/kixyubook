package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Small, ordered local writes. This scope intentionally outlives ViewModel cancellation, but
 * closes after draining the final checkpoint. No timer, network wait, or main-thread disk I/O.
 * A queued position is not an acknowledged write; failed positions remain available for retry
 * on pause/stop. This is not process keep-alive: settled pages must be saved while still active.
 */
internal class ReaderProgressWriter(
    private val save: suspend (ReadingProgress) -> Unit,
    private val onFailure: (Throwable) -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val writeMutex = Mutex()
    @Volatile private var latest: ReadingProgress? = null
    @Volatile var persisted: ReadingProgress? = null
        private set

    private val worker = scope.launch {
        for (request in requests) flush()
    }.also { job -> job.invokeOnCompletion { scope.cancel() } }

    @Synchronized
    fun submit(progress: ReadingProgress) {
        if (progress.updatedTime < (latest?.updatedTime ?: Long.MIN_VALUE)) return
        latest = progress
        checkpoint()
    }

    fun checkpoint() {
        requests.trySend(Unit)
    }

    suspend fun flush(): Boolean = withContext(dispatcher) {
        writeMutex.withLock {
            val progress = latest ?: return@withLock true
            if (progress.updatedTime <= (persisted?.updatedTime ?: Long.MIN_VALUE)) return@withLock true
            try {
                save(progress)
                persisted = progress
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onFailure(error)
                false
            }
        }
    }

    fun close() {
        checkpoint()
        requests.close()
    }

    internal suspend fun awaitClosed() = worker.join()
}
