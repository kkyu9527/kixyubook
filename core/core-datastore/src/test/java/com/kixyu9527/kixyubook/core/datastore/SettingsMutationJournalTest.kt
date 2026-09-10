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
