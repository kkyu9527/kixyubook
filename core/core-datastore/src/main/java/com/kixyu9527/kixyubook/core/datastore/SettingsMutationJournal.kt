package com.kixyu9527.kixyubook.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.common.repository.syncMutationRecordingSuppressed
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
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
    recorder: SyncMutationRecorder,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    init {
        scope.launch {
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
        val suppressed = syncMutationRecordingSuppressed()
        store.edit {
            transform(it)
            if (!suppressed) it[PENDING] = UUID.randomUUID().toString()
        }
    }

    private companion object { val PENDING = stringPreferencesKey("pending_sync_mutation") }
}
