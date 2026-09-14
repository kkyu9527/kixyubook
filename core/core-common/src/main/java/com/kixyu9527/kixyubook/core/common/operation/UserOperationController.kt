package com.kixyu9527.kixyubook.core.common.operation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lets the feedback host label long operations without coupling it to a feature module. */
enum class UserOperationKind { GENERIC, DELETE }

/**
 * Mutually exclusive operation phase. A single sealed state makes impossible combinations (for
 * example "succeeded and failed", or "running while awaiting confirmation") unrepresentable, so new
 * features cannot forget an invariant the old boolean flags required them to remember.
 */
sealed interface UserOperationState {
    val attempt: Long

    data class Idle(override val attempt: Long) : UserOperationState
    data class Confirming(override val attempt: Long, val targetLabel: String?) : UserOperationState
    data class Running(override val attempt: Long, val kind: UserOperationKind) : UserOperationState
    data class Succeeded(override val attempt: Long, val kind: UserOperationKind) : UserOperationState
    data class Failed(
        override val attempt: Long,
        val failure: OperationFailure,
        val kind: UserOperationKind,
    ) : UserOperationState {
        val retryable: Boolean get() = failure.retryable
    }
}

/** UI-thread owned write coordinator. Failed requests retain their payload until retry/dismiss. */
class UserOperationController(private val scope: CoroutineScope, private val reportFailure: (Exception) -> Unit = {}) {
    private val mutableState = MutableStateFlow<UserOperationState>(UserOperationState.Idle(0))
    val state = mutableState.asStateFlow()
    private var retryAction: (suspend () -> Unit)? = null
    private var retryKind = UserOperationKind.GENERIC

    // `kind` precedes `action` so existing trailing-lambda calls keep binding to the action.
    fun submit(kind: UserOperationKind = UserOperationKind.GENERIC, action: suspend () -> Unit) {
        val current = state.value
        if (current is UserOperationState.Confirming) return
        start(current.attempt + 1, action, kind)
    }

    fun confirmDelete(targetLabel: String? = null, action: suspend () -> Unit) {
        val current = state.value
        if (current is UserOperationState.Running || current is UserOperationState.Confirming) return
        retryAction = action
        retryKind = UserOperationKind.GENERIC
        mutableState.value = UserOperationState.Confirming(current.attempt + 1, targetLabel)
    }

    fun acceptConfirmation() {
        val current = state.value
        if (current is UserOperationState.Confirming) retryAction?.let { start(current.attempt, it, retryKind) }
    }

    fun cancelConfirmation() {
        val current = state.value
        if (current !is UserOperationState.Confirming) return
        retryAction = null
        mutableState.value = UserOperationState.Idle(current.attempt)
    }

    private fun start(attempt: Long, action: suspend () -> Unit, kind: UserOperationKind) {
        if (state.value is UserOperationState.Running) return
        retryAction = action
        retryKind = kind
        mutableState.value = UserOperationState.Running(attempt, kind)
        scope.launch {
            try {
                action()
                retryAction = null
                mutableState.value = UserOperationState.Succeeded(attempt, kind)
            } catch (cancelled: CancellationException) {
                retryAction = null
                mutableState.value = UserOperationState.Idle(attempt)
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = UserOperationState.Failed(attempt, OperationFailure.from(error), kind)
                runCatching { reportFailure(error) }
            }
        }
    }

    fun retry(attempt: Long) {
        val current = state.value
        if (current is UserOperationState.Failed && current.attempt == attempt && current.retryable) {
            retryAction?.let { start(attempt, it, current.kind) }
        }
    }

    fun dismissFailure(attempt: Long) {
        val current = state.value
        if (current !is UserOperationState.Failed || current.attempt != attempt) return
        retryAction = null
        mutableState.value = UserOperationState.Idle(attempt)
    }
}
