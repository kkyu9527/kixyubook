package com.kixyu9527.kixyubook.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kixyu9527.kixyubook.core.common.repository.SettingsWriteGate
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.common.repository.syncMutationRecordingSuppressed
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * DataStore and Room cannot share a transaction. Persist the delivery token WITH the settings,
 * then acknowledge only after Room has its outbox entry. Replaying after death is idempotent.
 */
class SettingsMutationJournal(
    private val store: DataStore<Preferences>,
    private val recorder: SyncMutationRecorder,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    init {
        // The persisted token is visible to a remote apply even before the replay below reaches the
        // outbox, including right after a process restart where no in-memory state survives.
        val unregister = SettingsWriteGate.registerPersistedPendingCheck {
            store.data.first()[PENDING] != null
        }
        scope.coroutineContext[Job]?.invokeOnCompletion { unregister() }
        scope.launch {
            // Crash-safety net: replay a token that survived a process death or a failed synchronous
            // registration in edit(). Recording again is harmless because the outbox coalesces by key.
            store.data.map { it[PENDING] }.distinctUntilChanged().onEach { token ->
                if (token != null) {
                    recorder.record(SyncEntityType.SETTINGS, "global")
                    store.edit { if (it[PENDING] == token) it.remove(PENDING) }
                }
            }.retryWhen { failure, _ ->
                if (failure is CancellationException) false else { delay(1_000); true }
            }.collect()
        }
    }

    suspend fun edit(transform: suspend (MutablePreferences) -> Unit) {
        if (syncMutationRecordingSuppressed()) {
            // Remote applies already serialize with local writes through SettingsWriteGate.
            store.edit { transform(it) }
            return
        }
        SettingsWriteGate.mutex.withLock {
            val token = UUID.randomUUID().toString()
            store.edit {
                transform(it)
                it[PENDING] = token
            }
            // Mark before the outbox row exists: a cloud apply that starts right now must still see
            // this persisted-but-unregistered write. The token collector only retries after failure.
            SettingsWriteGate.markLocalWrite()
            runCatching { recorder.record(SyncEntityType.SETTINGS, "global") }.onSuccess {
                store.edit { if (it[PENDING] == token) it.remove(PENDING) }
            }
        }
    }

    private companion object { val PENDING = stringPreferencesKey("pending_sync_mutation") }
}
