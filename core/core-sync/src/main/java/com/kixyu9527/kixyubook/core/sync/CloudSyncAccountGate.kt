package com.kixyu9527.kixyubook.core.sync

import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException

/** Publishing a new account/token and using its ledger share the same critical section. */
@Singleton
class CloudSyncAccountGate @Inject constructor(
    private val database: KixyuDatabase,
    private val preferences: SyncPreferencesStore,
) {
    internal val mutex = Mutex()
    private val transferGeneration = AtomicLong()

    /** Call under mutex. A transfer may perform network I/O outside the ledger critical section. */
    internal suspend fun transferLease(): CloudTransferLease {
        finishPendingReset()
        val subject = preferences.current().account?.subject ?: throw CloudAccountChangedException()
        val generation = transferGeneration.get()
        return CloudTransferLease(subject) {
            if (transferGeneration.get() != generation) throw CloudAccountChangedException()
        }
    }

    internal fun invalidateTransfers() { transferGeneration.incrementAndGet() }

    suspend fun commitVerifiedAuthorization(resolveAccount: suspend () -> SyncAccount, publishToken: (SyncAccount) -> Unit) {
        // Failed/cancelled verification must not touch either the account or its ledger.
        commitAuthorization(resolveAccount(), publishToken)
    }

    suspend fun commitAuthorization(account: SyncAccount, publishToken: (SyncAccount) -> Unit) = mutex.withLock {
        // Once account identity changes, cancellation must not expose the old token/ledger under
        // the new account. A durable intent still covers process death between the two stores.
        withContext(NonCancellable) {
            if (preferences.current().account?.subject != account.subject) invalidateTransfers()
            preferences.saveAccount(account)
            finishPendingReset()
            publishToken(account)
        }
    }

    /** Caller holds [mutex]. Also repairs an authorization interrupted by process death. */
    internal suspend fun finishPendingReset() {
        if (!preferences.current().accountLedgerResetPending) return
        invalidateTransfers()
        database.withTransaction {
            database.syncDao().apply {
                clearOutbox()
                clearObjectStates()
                clearTombstones()
                clearRemoteInbox()
            }
        }
        preferences.completeAccountLedgerReset()
    }
}

internal class CloudAccountChangedException : CancellationException("cloud account changed")

internal class CloudTransferLease(val accountSubject: String, val check: () -> Unit) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CloudTransferLease>
}
