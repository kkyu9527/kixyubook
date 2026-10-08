package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.database.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudSyncAccountGateTest {
    private val a = SyncAccount("account-a", "a@example.com", "A", null)
    private val b = SyncAccount("account-b", "b@example.com", "B", null)

    @Test fun aCachedTokenCannotCrossAccountIdentities() {
        val token = AccountAccessToken("test-token", 20, a.subject)
        assertTrue(token.isUsable(a.subject, now = 10))
        assertFalse(token.isUsable(b.subject, now = 10))
        assertFalse(token.isUsable(a.subject, now = 20))
    }

    private suspend fun seed(f: RemoteApplyFixture) {
        f.sync.upsertOutbox(SyncOutboxEntity("delete", "BOOK", "book", "DELETE", 1, 1, "device"))
        f.sync.upsertObjectState(SyncObjectStateEntity("books/book/metadata", "old-id", "hash", 1, 1, 1))
        f.sync.upsertTombstone(SyncTombstoneEntity("tombstones/book/book", 1, "device", Long.MAX_VALUE))
        CloudRemoteInbox(f.database, f.sync).receive(mapOf("annotations/note" to remoteTestObject("annotations/note")))
    }

    @Test fun failedAndCancelledAuthorizationPreserveTheOldAccountAndEveryLedgerRow() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            f.preferences.clearAccount()
            val gate = CloudSyncAccountGate(f.database, f.preferences)
            gate.commitAuthorization(a) {}
            seed(f)
            for (error in listOf(IllegalStateException("authorization failed"), CancellationException("cancelled"))) {
                var tokenPublished = false
                val failure = runCatching { gate.commitVerifiedAuthorization({ throw error }) { tokenPublished = true } }.exceptionOrNull()
                assertSame(error, failure)
                assertFalse(tokenPublished)
                assertEquals(a.subject, f.preferences.current().account?.subject)
                assertEquals(1, f.sync.allPending().size)
                assertEquals(1, f.sync.allObjectStates().size)
                assertNotNull(f.sync.tombstone("tombstones/book/book"))
                assertEquals(1, f.sync.remoteInbox().size)
            }
            f.preferences.clearAccount()
        }
    }

    @Test fun sameAccountReauthorizationPreservesQueuedDeletions() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            f.preferences.clearAccount()
            val gate = CloudSyncAccountGate(f.database, f.preferences)
            gate.commitAuthorization(a) {}
            seed(f)
            gate.commitVerifiedAuthorization({ a }) {}
            assertEquals(1, f.sync.allPending().size)
            assertEquals(1, f.sync.remoteInbox().size)
            f.preferences.clearAccount()
        }
    }

    @Test fun aResolvedAccountSwitchClearsTheLedgerBeforePublishingItsToken() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            f.preferences.clearAccount()
            val gate = CloudSyncAccountGate(f.database, f.preferences)
            gate.commitAuthorization(a) {}
            seed(f)
            var tokenPublished = false
            gate.commitVerifiedAuthorization({ b }) {
                f.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("old deletions must be gone before the new token becomes usable", 0, cursor.getInt(0))
                }
                tokenPublished = true
            }
            assertTrue(tokenPublished)
            assertEquals(b.subject, f.preferences.current().account?.subject)
            assertFalse(f.preferences.current().accountLedgerResetPending)
            assertTrue(f.sync.allPending().isEmpty())
            assertTrue(f.sync.allObjectStates().isEmpty())
            assertTrue(f.sync.remoteInbox().isEmpty())
            assertNull(f.sync.tombstone("tombstones/book/book"))
            f.preferences.clearAccount()
        }
    }

    @Test fun anInterruptedResetIsCompletedBeforeTheNextSyncCanUseTheLedger() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            f.preferences.clearAccount()
            f.preferences.saveAccount(a)
            seed(f)
            // Simulate process death after DataStore publishes account B but before Room cleanup.
            f.preferences.saveAccount(b)
            assertTrue(f.preferences.current().accountLedgerResetPending)
            val reopened = CloudSyncAccountGate(f.database, f.preferences)
            reopened.mutex.withLock { reopened.finishPendingReset() }
            assertTrue(f.sync.allPending().isEmpty())
            assertTrue(f.sync.remoteInbox().isEmpty())
            assertFalse(f.preferences.current().accountLedgerResetPending)
            f.preferences.clearAccount()
        }
    }

    @Test fun switchingWaitsForTheInFlightSyncBeforeChangingIdentity() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            f.preferences.clearAccount()
            val gate = CloudSyncAccountGate(f.database, f.preferences)
            gate.commitAuthorization(a) {}
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val running = launch { gate.mutex.withLock { entered.complete(Unit); release.await(); seed(f) } }
            entered.await()
            val switching = async { gate.commitAuthorization(b) {} }
            assertEquals(a.subject, f.preferences.current().account?.subject)
            release.complete(Unit)
            running.join()
            switching.await()
            assertEquals(b.subject, f.preferences.current().account?.subject)
            assertTrue(f.sync.allPending().isEmpty())
            f.preferences.clearAccount()
        }
    }
}
