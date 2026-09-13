package com.kixyu9527.kixyubook.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.kixyu9527.kixyubook.core.common.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SettingsMutationJournalTest {
    private class Store : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(data.value)
            data.value = next
            return next
        }
    }
    private val setting = stringPreferencesKey("setting")
    private val token = stringPreferencesKey("pending_sync_mutation")

    @Test fun failedDeliveryDoesNotFailSaveAndRestartRetriesDurableToken() = runBlocking {
        val store = Store()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fail = object : SyncMutationRecorder {
            override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) {
                throw IOException("outbox unavailable")
            }
        }
        val journal = SettingsMutationJournal(store, fail, firstScope)
        journal.edit { it[setting] = "saved" }
        assertEquals("saved", store.data.first()[setting])
        assertNotNull(store.data.first()[token])
        firstScope.cancel()
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var delivered = 0
        try {
            SettingsMutationJournal(store, object : SyncMutationRecorder {
                override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) { delivered++ }
            }, secondScope)
            assertEquals(1, delivered)
            assertNull(store.data.first()[token])
            assertEquals("saved", store.data.first()[setting])
        } finally { secondScope.cancel() }
    }

    @Test fun pendingTokenOnDiskBlocksRemoteApplyAfterProcessDeath() = runBlocking {
        val store = Store()
        store.data.value = preferencesOf(token to "crashed-token")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val replayBlocked = CompletableDeferred<Unit>()
        try {
            // No in-memory state exists after a restart; only the persisted token can block an apply.
            SettingsMutationJournal(store, object : SyncMutationRecorder {
                override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) {
                    replayBlocked.await()
                }
            }, scope)
            assertTrue(SettingsWriteGate.hasPendingLocalWrite())
        } finally {
            scope.cancel()
            replayBlocked.complete(Unit)
        }
    }

    @Test fun repeatedFailedRegistrationsLeaveNoStalePendingAfterReplay() = runBlocking {
        val store = Store()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val failing = object : SyncMutationRecorder {
            override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) {
                throw IOException("outbox unavailable")
            }
        }
        val journal = SettingsMutationJournal(store, failing, scope)
        journal.edit { it[setting] = "first" }
        journal.edit { it[setting] = "second" }
        assertNotNull(store.data.value[token])
        scope.cancel()

        val replayScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var delivered = 0
        try {
            SettingsMutationJournal(store, object : SyncMutationRecorder {
                override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) { delivered++ }
            }, replayScope)
            assertEquals(1, delivered)
            assertEquals("second", store.data.value[setting])
            assertNull(store.data.value[token])
            assertFalse(SettingsWriteGate.hasPendingLocalWrite())
        } finally {
            replayScope.cancel()
        }
    }

    @Test fun localEditAdvancesTheSettingsWriteGenerationAndRemoteRestoreDoesNot() = runBlocking {
        val store = Store()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val journal = SettingsMutationJournal(store, object : SyncMutationRecorder {
                override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) = Unit
            }, scope)
            val before = SettingsWriteGate.currentGeneration()
            journal.edit { it[setting] = "local" }
            val afterLocal = SettingsWriteGate.currentGeneration()
            assertTrue(afterLocal > before)

            withoutRecordingSyncMutations { journal.edit { it[setting] = "remote" } }
            assertEquals(afterLocal, SettingsWriteGate.currentGeneration())
            assertEquals("remote", store.data.value[setting])
        } finally {
            scope.cancel()
        }
    }

    @Test fun failedEditHasNoMutationAndRemoteRestoreDoesNotEcho() = runBlocking {
        val store = Store()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var delivered = 0
        try {
            val journal = SettingsMutationJournal(store, object : SyncMutationRecorder {
                override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) { delivered++ }
            }, scope)
            assertTrue(runCatching { journal.edit { it[setting] = "bad"; throw IOException() } }.isFailure)
            assertNull(store.data.value[setting])
            withoutRecordingSyncMutations { journal.edit { it[setting] = "remote" } }
            assertEquals("remote", store.data.value[setting])
            assertEquals(0, delivered)
            assertNull(store.data.value[token])
        } finally { scope.cancel() }
    }
}
