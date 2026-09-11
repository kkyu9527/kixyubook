package com.kixyu9527.kixyubook.core.common.operation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserOperationState(
    val attempt: Long = 0,
    val running: Boolean = false,
    val succeeded: Boolean = false,
    val failed: Boolean = false,
    val awaitingConfirmation: Boolean = false,
    val failure: OperationFailure? = null,
    val targetLabel: String? = null,
)

/** UI-thread owned write coordinator. Failed requests retain their payload until retry/dismiss. */
class UserOperationController(private val scope: CoroutineScope, private val reportFailure: (Exception) -> Unit = {}) {
    private val mutableState = MutableStateFlow(UserOperationState())
    val state = mutableState.asStateFlow()
    private var retryAction: (suspend () -> Unit)? = null

    fun submit(action: suspend () -> Unit) {
        if (state.value.awaitingConfirmation) return
        start(state.value.attempt + 1, action)
    }

    fun confirmDelete(targetLabel: String? = null, action: suspend () -> Unit) {
        if (state.value.running || state.value.awaitingConfirmation) return
        retryAction = action
        mutableState.value = UserOperationState(state.value.attempt + 1, awaitingConfirmation = true, targetLabel = targetLabel)
    }

    fun acceptConfirmation() {
        if (state.value.awaitingConfirmation) retryAction?.let { start(state.value.attempt, it) }
    }

    fun cancelConfirmation() {
        if (!state.value.awaitingConfirmation) return
        retryAction = null
        mutableState.value = UserOperationState(state.value.attempt)
    }

    private fun start(attempt: Long, action: suspend () -> Unit) {
        if (state.value.running) return
        retryAction = action
        mutableState.value = UserOperationState(attempt, running = true)
        scope.launch {
            try {
                action()
                retryAction = null
                mutableState.value = UserOperationState(attempt, succeeded = true)
            } catch (cancelled: CancellationException) {
                retryAction = null
                mutableState.value = UserOperationState(attempt)
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = UserOperationState(attempt, failed = true, failure = OperationFailure.from(error))
                runCatching { reportFailure(error) }
            }
        }
    }

    fun retry(attempt: Long) {
        if (state.value.attempt == attempt && state.value.failed && state.value.failure?.retryable != false) retryAction?.let { start(attempt, it) }
    }

    fun dismissFailure(attempt: Long) {
        if (state.value.attempt != attempt || !state.value.failed) return
        retryAction = null
        mutableState.value = UserOperationState(attempt)
    }
}
