package com.kixyu9527.kixyubook.core.database

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** Import, deletion, indexing asset writes and restore installation cannot replace files together. */
internal object LibraryStorageGate { val mutex = Mutex() }

private class FileCleanupBatch : AbstractCoroutineContextElement(FileCleanupBatch) {
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
 * Runs [block] (a Room transaction that calls repositories) and performs the file cleanups it
 * deferred only after it returns successfully. A failure drops them: the database rolled back, so
 * the files must stay to match the restored rows.
 */
suspend fun <T> runWithPostCommitFileCleanup(block: suspend () -> T): T {
    val batch = FileCleanupBatch()
    val result = withContext(batch) { block() }
    // A cancelled caller must still finish the cleanups its committed transaction produced.
    withContext(NonCancellable) {
        batch.blocks.forEach { runCatching { it() } }
    }
    return result
}
