package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** Import, deletion, indexing asset writes and restore installation cannot replace files together. */
internal object LibraryStorageGate {
    val mutex = Mutex()

    /** Set while a backup restore swaps the database file; writers must not open it mid-swap. */
    @Volatile var restoreInProgress: Boolean = false

    /**
     * Called by the highest-frequency writers. A restore that is swapping the database makes the
     * current handle invalid, so in-flight writes cancel instead of touching the replaced file.
     */
    fun requireWritable() {
        if (restoreInProgress) {
            throw kotlinx.coroutines.CancellationException("library restore in progress")
        }
    }
}

private class FileCleanupBatch : AbstractCoroutineContextElement(FileCleanupBatch) {
    /** Must-complete cross-store repairs; their failure fails the caller so it can retry. */
    val repairs = mutableListOf<suspend () -> Unit>()

    /** Best-effort file removals; a failure is logged but never fails the caller. */
    val blocks = mutableListOf<suspend () -> Unit>()

    companion object Key : CoroutineContext.Key<FileCleanupBatch>
}

/**
 * Defers a file cleanup until the surrounding [runWithPostCommitFileCleanup] returns. Outside such
 * a scope the cleanup runs immediately, so normal repository calls keep their previous behavior.
 */
internal suspend fun deferFileCleanup(block: suspend () -> Unit) {
    val batch = coroutineContext[FileCleanupBatch]
    if (batch == null) block() else batch.blocks += block
}

/**
 * Defers a repair that must not be lost (a DataStore write paired with a committed Room delete).
 * Outside such a scope it runs immediately; inside, it runs after the transaction and a failure
 * propagates to the caller instead of disappearing.
 */
internal suspend fun deferRequiredRepair(block: suspend () -> Unit) {
    val batch = coroutineContext[FileCleanupBatch]
    if (batch == null) block() else batch.repairs += block
}

/**
 * Runs [block] (a Room transaction that calls repositories) and performs the file cleanups it
 * deferred only after it returns successfully. A failure drops them: the database rolled back, so
 * the files must stay to match the restored rows.
 */
suspend fun <T> runWithPostCommitFileCleanup(block: suspend () -> T): T {
    val batch = FileCleanupBatch()
    val result = withContext(batch) { block() }
    // A cancelled caller must still finish the cleanups its committed transaction produced. File
    // removal may fail harmlessly, but it must be visible: a silent failure hides a broken
    // configuration repair until a later backup export fails for an unrelated-looking reason.
    withContext(NonCancellable) {
        // Required repairs first: a failure propagates so the caller reports it and retries.
        batch.repairs.forEach { it() }
        batch.blocks.forEach { block ->
            runCatching { block() }.onFailure { error ->
                DiagnosticLog.record(
                    DiagnosticLog.Category.LIBRARY,
                    "post_commit_cleanup_failed",
                    outcome = "failure",
                    details = mapOf("error" to (error.message ?: error::class.java.simpleName)),
                )
            }
        }
    }
    return result
}
