package com.kixyu9527.kixyubook.core.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import kotlinx.coroutines.flow.StateFlow

data class SyncAccount(
    val subject: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
)

enum class CloudSyncPhase { IDLE, AUTHORIZING, SYNCING, SUCCESS, AUTH_REQUIRED, ERROR }

data class InitialSyncDecision(
    val localBookCount: Int,
    val cloudBookCount: Int,
    val conflicts: List<InitialSyncConflict> = emptyList(),
)

internal fun InitialSyncDecision.shouldRestoreFromCloud(): Boolean =
    localBookCount == 0 && cloudBookCount > 0

internal fun InitialSyncDecision.requiresUserDecision(): Boolean = conflicts.isNotEmpty()

data class InitialSyncConflict(
    val entityType: SyncEntityType,
    val entityId: String,
)

enum class InitialSyncChoice {
    KEEP_LOCAL_CHANGES,
    USE_CLOUD_CHANGES,
}

data class CloudSyncState(
    val account: SyncAccount? = null,
    val enabled: Boolean = false,
    val syncOriginalFiles: Boolean = true,
    val syncFonts: Boolean = true,
    val wifiOnlyForLargeFiles: Boolean = true,
    val phase: CloudSyncPhase = CloudSyncPhase.IDLE,
    val lastSyncTime: Long = 0,
    val pendingCount: Int = 0,
    val errorMessage: String? = null,
    val initialSyncDecision: InitialSyncDecision? = null,
    val inspectingInitialSync: Boolean = false,
)

sealed interface GoogleConnectResult {
    data object Connected : GoogleConnectResult
    data class NeedsAuthorization(val pendingIntent: PendingIntent) : GoogleConnectResult
    data class Failed(val message: String) : GoogleConnectResult
}

interface CloudSyncManager {
    val state: StateFlow<CloudSyncState>
    suspend fun connect(activity: Activity): GoogleConnectResult
    suspend fun finishAuthorization(activity: Activity, resultData: Intent?): GoogleConnectResult
    suspend fun disconnect()
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setSyncOriginalFiles(enabled: Boolean)
    suspend fun setSyncFonts(enabled: Boolean)
    suspend fun setWifiOnlyForLargeFiles(enabled: Boolean)
    suspend fun resolveInitialSync(choice: InitialSyncChoice): Result<Unit>
    fun syncNow()
    suspend fun deleteCloudData(activity: Activity): Result<Unit>
}
