package com.kixyu9527.kixyubook.core.common.repository

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex

/**
 * Serializes remote settings applies against local settings writes.
 *
 * A cloud apply downloads first (network latency) and cannot hold a lock for that whole time.
 * It captures [currentGeneration] before the download and re-checks while holding [mutex]; local
 * writes also run under [mutex].
 *
 * A local write is durable in DataStore before Room can record its outbox row, so an apply must also
 * look at the persisted pending token. Journals register a reader with [registerPersistedPendingCheck];
 * it is consulted under [mutex] and read from disk, so a cold-start replay that has not reached the
 * outbox yet still wins. Nothing is accumulated in memory: the persisted token is the single source
 * of truth, so an overwritten or replayed token can never leave a stale count behind.
 */
object SettingsWriteGate {
    val mutex = Mutex()

    private val generation = AtomicLong()
    private val persistedPendingChecks = CopyOnWriteArrayList<suspend () -> Boolean>()

    fun currentGeneration(): Long = generation.get()

    /** Must be called by local writes while holding [mutex], right after the data is persisted. */
    fun markLocalWrite() {
        generation.incrementAndGet()
    }

    /** Registers a reader for one journal's persisted token. Call the returned handle to unregister. */
    fun registerPersistedPendingCheck(check: suspend () -> Boolean): () -> Unit {
        persistedPendingChecks.add(check)
        return { persistedPendingChecks.remove(check) }
    }

    /** True while any local settings write is persisted but not yet durable in the sync outbox. */
    suspend fun hasPendingLocalWrite(): Boolean = persistedPendingChecks.any { it() }
}
