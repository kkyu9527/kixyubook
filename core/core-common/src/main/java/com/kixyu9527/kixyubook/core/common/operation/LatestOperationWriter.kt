package com.kixyu9527.kixyubook.core.common.operation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** UI-thread owned. Coalesces each setting independently without cancelling an in-flight write. */
class LatestOperationWriter(private val scope: CoroutineScope, private val controller: UserOperationController) {
    private val pending = linkedMapOf<String, suspend () -> Unit>()
    private var worker: Job? = null
    private var failedKey: String? = null

    fun submit(key: String, action: suspend () -> Unit) {
        if (failedKey == key) controller.dismissFailure(controller.state.value.attempt)
        pending[key] = action
        if (worker?.isActive == true) return
        worker = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var active: Pair<String, suspend () -> Unit>? = null
            try {
                delay(180)
                while (pending.isNotEmpty()) {
                    controller.state.first { !it.running && !it.awaitingConfirmation && !it.failed }
                    val entry = pending.entries.first()
                    val keyToWrite = entry.key
                    val write = entry.value
                    pending.remove(keyToWrite)
                    active = keyToWrite to write
                    controller.submit(write)
                    val result = controller.state.first { !it.running }
                    if (result.succeeded || result.failed) active = null
                    failedKey = keyToWrite.takeIf { result.failed }
                    // A newer value supersedes the failed payload, not the user's latest intention.
                    if (result.failed && pending.containsKey(keyToWrite)) controller.dismissFailure(result.attempt)
                }
            } finally {
                if (!currentCoroutineContext().isActive) {
                    // Navigation may clear the ViewModel during the debounce or an I/O suspension.
                    // Settings writes are idempotent; drain the newest intentions without retaining
                    // the screen. DataStore itself performs disk I/O off the main thread.
                    val remaining = linkedMapOf<String, suspend () -> Unit>()
                    active?.let { remaining[it.first] = it.second }
                    remaining.putAll(pending)
                    pending.clear()
                    withContext(NonCancellable) {
                        remaining.values.forEach { action ->
                            try { action() } catch (_: Exception) {
                                com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.record(
                                    com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category.READER,
                                    "settings_flush_failed",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
