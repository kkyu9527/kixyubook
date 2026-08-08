package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cloudSyncDataStore by preferencesDataStore(name = "cloud_sync")

data class PersistedSyncState(
    val account: SyncAccount? = null,
    val enabled: Boolean = false,
    val syncOriginalFiles: Boolean = true,
    val syncFonts: Boolean = true,
    val wifiOnlyForLargeFiles: Boolean = true,
    val pageToken: String? = null,
    val lastSyncTime: Long = 0,
    val phase: CloudSyncPhase = CloudSyncPhase.IDLE,
    val error: String? = null,
    val initialMergeComplete: Boolean = false,
    val initialSyncApproved: Boolean = false,
    val conflicts: List<InitialSyncConflict> = emptyList(),
    val preferLocalConflictsUntil: Long = 0,
)

@Singleton
class SyncPreferencesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val state: Flow<PersistedSyncState> = context.cloudSyncDataStore.data.map { values ->
        val subject = values[ACCOUNT_SUBJECT]
        PersistedSyncState(
            account = subject?.let {
                SyncAccount(
                    subject = it,
                    email = values[ACCOUNT_EMAIL].orEmpty(),
                    displayName = values[ACCOUNT_NAME].orEmpty(),
                    avatarUrl = values[ACCOUNT_AVATAR],
                )
            },
            enabled = values[ENABLED] ?: false,
            syncOriginalFiles = values[SYNC_ORIGINALS] ?: true,
            syncFonts = values[SYNC_FONTS] ?: true,
            wifiOnlyForLargeFiles = values[WIFI_ONLY] ?: true,
            pageToken = values[PAGE_TOKEN],
            lastSyncTime = values[LAST_SYNC] ?: 0,
            phase = values[PHASE]?.let { runCatching { CloudSyncPhase.valueOf(it) }.getOrNull() }
                ?: CloudSyncPhase.IDLE,
            error = values[ERROR],
            initialMergeComplete = values[INITIAL_MERGE] ?: false,
            initialSyncApproved = values[INITIAL_SYNC_APPROVED]
                ?: (values[INITIAL_MERGE] ?: false),
            conflicts = decodeConflicts(values[SYNC_CONFLICTS]),
            preferLocalConflictsUntil = values[PREFER_LOCAL_CONFLICTS_UNTIL] ?: 0,
        )
    }

    suspend fun current() = state.first()

    suspend fun saveAccount(account: SyncAccount) = context.cloudSyncDataStore.edit {
        val sameAccount = it[ACCOUNT_SUBJECT] == account.subject
        it[ACCOUNT_SUBJECT] = account.subject
        it[ACCOUNT_EMAIL] = account.email
        it[ACCOUNT_NAME] = account.displayName
        account.avatarUrl?.let { value -> it[ACCOUNT_AVATAR] = value } ?: it.remove(ACCOUNT_AVATAR)
        if (!sameAccount) {
            it[ENABLED] = false
            it[INITIAL_SYNC_APPROVED] = false
            it[INITIAL_MERGE] = false
            it.remove(PAGE_TOKEN)
            it.remove(LAST_SYNC)
            it.remove(SYNC_CONFLICTS)
            it.remove(PREFER_LOCAL_CONFLICTS_UNTIL)
        }
        it[PHASE] = CloudSyncPhase.IDLE.name
        it.remove(ERROR)
    }

    suspend fun clearAccount() = context.cloudSyncDataStore.edit {
        it.remove(ACCOUNT_SUBJECT); it.remove(ACCOUNT_EMAIL); it.remove(ACCOUNT_NAME); it.remove(ACCOUNT_AVATAR)
        it.remove(PAGE_TOKEN); it.remove(LAST_SYNC); it.remove(ERROR); it.remove(INITIAL_MERGE)
        it.remove(INITIAL_SYNC_APPROVED)
        it.remove(SYNC_CONFLICTS)
        it.remove(PREFER_LOCAL_CONFLICTS_UNTIL)
        it[ENABLED] = false
        it[PHASE] = CloudSyncPhase.IDLE.name
    }

    suspend fun setEnabled(value: Boolean) = setBoolean(ENABLED, value)
    suspend fun setSyncOriginals(value: Boolean) = setBoolean(SYNC_ORIGINALS, value)
    suspend fun setSyncFonts(value: Boolean) = setBoolean(SYNC_FONTS, value)
    suspend fun setWifiOnly(value: Boolean) = setBoolean(WIFI_ONLY, value)

    suspend fun approveInitialSync() = context.cloudSyncDataStore.edit {
        it[INITIAL_SYNC_APPROVED] = true
        it[ENABLED] = true
        it[PHASE] = CloudSyncPhase.IDLE.name
        it.remove(ERROR)
        it.remove(SYNC_CONFLICTS)
    }

    suspend fun setConflicts(conflicts: List<InitialSyncConflict>) = context.cloudSyncDataStore.edit {
        if (conflicts.isEmpty()) {
            it.remove(SYNC_CONFLICTS)
        } else {
            it[SYNC_CONFLICTS] = conflicts.joinToString("\n") { conflict ->
                "${conflict.entityType.name}\t${conflict.entityId}"
            }
        }
        it[PHASE] = CloudSyncPhase.IDLE.name
        it.remove(ERROR)
    }

    suspend fun preferLocalConflictsFor(durationMillis: Long) = context.cloudSyncDataStore.edit {
        it[PREFER_LOCAL_CONFLICTS_UNTIL] = System.currentTimeMillis() + durationMillis
    }

    suspend fun clearLocalConflictPreference() = context.cloudSyncDataStore.edit {
        it.remove(PREFER_LOCAL_CONFLICTS_UNTIL)
    }

    suspend fun markAuthorizing() = context.cloudSyncDataStore.edit {
        it[PHASE] = CloudSyncPhase.AUTHORIZING.name
        it.remove(ERROR)
    }

    suspend fun markRunning() = context.cloudSyncDataStore.edit {
        it[PHASE] = CloudSyncPhase.SYNCING.name
        it.remove(ERROR)
    }

    suspend fun markInterrupted() = context.cloudSyncDataStore.edit {
        if (it[PHASE] == CloudSyncPhase.SYNCING.name) {
            it[PHASE] = CloudSyncPhase.IDLE.name
            it.remove(ERROR)
        }
    }

    suspend fun markIdle() = context.cloudSyncDataStore.edit {
        it[PHASE] = CloudSyncPhase.IDLE.name
        it.remove(ERROR)
    }

    suspend fun markSuccess(pageToken: String?) = context.cloudSyncDataStore.edit {
        it[PHASE] = CloudSyncPhase.SUCCESS.name
        it[LAST_SYNC] = System.currentTimeMillis()
        pageToken?.let { value -> it[PAGE_TOKEN] = value }
        it[INITIAL_MERGE] = true
        it[INITIAL_SYNC_APPROVED] = true
        it.remove(SYNC_CONFLICTS)
        it.remove(ERROR)
    }

    suspend fun markAuthRequired(@Suppress("UNUSED_PARAMETER") message: String) = context.cloudSyncDataStore.edit {
        it[PHASE] = CloudSyncPhase.AUTH_REQUIRED.name
        // Authorization is recovered silently on the next foreground pass. Never expose the
        // provider/HTTP exception as a user-facing sync error while that recovery is pending.
        it.remove(ERROR)
    }
    suspend fun markError(message: String) = markFailure(CloudSyncPhase.ERROR, message)

    suspend fun deviceId(): String {
        val existing = context.cloudSyncDataStore.data.first()[DEVICE_ID]
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        context.cloudSyncDataStore.edit { if (it[DEVICE_ID] == null) it[DEVICE_ID] = generated }
        return context.cloudSyncDataStore.data.first()[DEVICE_ID] ?: generated
    }

    private suspend fun setBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) =
        context.cloudSyncDataStore.edit { it[key] = value }

    private suspend fun markFailure(phase: CloudSyncPhase, message: String) = context.cloudSyncDataStore.edit {
        it[PHASE] = phase.name
        it[ERROR] = message
    }

    private fun decodeConflicts(value: String?): List<InitialSyncConflict> = value.orEmpty()
        .lineSequence()
        .mapNotNull { line ->
            val (typeName, entityId) = line.split('\t', limit = 2).takeIf { it.size == 2 }
                ?: return@mapNotNull null
            val type = runCatching {
                com.kixyu9527.kixyubook.core.common.repository.SyncEntityType.valueOf(typeName)
            }.getOrNull() ?: return@mapNotNull null
            InitialSyncConflict(type, entityId)
        }
        .distinct()
        .toList()

    private companion object {
        val ACCOUNT_SUBJECT = stringPreferencesKey("account_subject")
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        val ACCOUNT_NAME = stringPreferencesKey("account_name")
        val ACCOUNT_AVATAR = stringPreferencesKey("account_avatar")
        val ENABLED = booleanPreferencesKey("enabled")
        val SYNC_ORIGINALS = booleanPreferencesKey("sync_originals")
        val SYNC_FONTS = booleanPreferencesKey("sync_fonts")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val PAGE_TOKEN = stringPreferencesKey("page_token")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val PHASE = stringPreferencesKey("phase")
        val ERROR = stringPreferencesKey("error")
        val INITIAL_MERGE = booleanPreferencesKey("initial_merge_complete")
        val INITIAL_SYNC_APPROVED = booleanPreferencesKey("initial_sync_approved")
        val SYNC_CONFLICTS = stringPreferencesKey("sync_conflicts")
        val PREFER_LOCAL_CONFLICTS_UNTIL = longPreferencesKey("prefer_local_conflicts_until")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
