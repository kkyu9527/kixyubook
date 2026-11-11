package com.kixyu9527.kixyubook

import com.kixyu9527.kixyubook.core.common.model.BookPreparationProgress
import com.kixyu9527.kixyubook.core.common.model.BookPreparationStage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

internal data class BookOpeningState(
    val bookUuid: String,
    val progress: BookPreparationProgress = BookPreparationProgress(BookPreparationStage.WAITING_SYNC),
    val feedbackVisible: Boolean = false,
    val error: Throwable? = null,
)

/** One owner for availability, feedback, retry and navigation, including stale-completion guards. */
internal class BookOpeningController(
    private val scope: CoroutineScope,
    private val ensureAvailable: suspend (String, (BookPreparationProgress) -> Unit) -> Result<Unit>,
    private val feedbackDelayMillis: Long = 200,
) {
    private val current = MutableStateFlow<BookOpeningState?>(null)
    val state = current.asStateFlow()
    private val generation = AtomicLong()
    private var job: Job? = null
    private var retryRequest: (() -> Unit)? = null

    fun open(uuid: String, onReady: () -> Unit) {
        if (current.value?.let { it.bookUuid == uuid && it.error == null } == true) return
        cancel()
        val request = generation.get()
        current.value = BookOpeningState(uuid)
        retryRequest = { open(uuid, onReady) }
        job = scope.launch {
            val feedback = launch {
                delay(feedbackDelayMillis)
                current.update { if (request == generation.get()) it?.copy(feedbackVisible = true) else it }
            }
            try {
                val result = try {
                    ensureAvailable(uuid) { progress ->
                        current.update { if (request == generation.get()) it?.copy(progress = progress) else it }
                    }
                } catch (error: CancellationException) { throw error }
                  catch (error: Exception) { Result.failure(error) }
                ensureActive()
                if (request != generation.get()) return@launch
                result.fold(
                    onSuccess = { current.value = null; retryRequest = null; onReady() },
                    onFailure = { current.value = current.value?.copy(feedbackVisible = true, error = it) },
                )
            } catch (error: CancellationException) {
                // Provider/account cancellation is not necessarily initiated by cancel(). Do not
                // leave an invisible running request that blocks every later tap of this book.
                if (request == generation.get()) {
                    current.value = null
                    retryRequest = null
                }
                throw error
            } finally { feedback.cancel() }
        }
    }

    fun retry() { retryRequest?.invoke() }

    fun cancel() {
        generation.incrementAndGet()
        job?.cancel()
        job = null
        retryRequest = null
        current.value = null
    }
}
