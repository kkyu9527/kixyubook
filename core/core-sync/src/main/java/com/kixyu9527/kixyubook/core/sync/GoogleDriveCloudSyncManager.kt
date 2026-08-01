package com.kixyu9527.kixyubook.core.sync

import android.app.Activity
import android.content.Intent
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveCloudSyncManager @Inject constructor(
    private val preferences: SyncPreferencesStore,
    syncDao: SyncDao,
    private val accounts: GoogleAccountClient,
    private val engine: CloudSyncEngine,
    private val scheduler: CloudSyncScheduler,
) : CloudSyncManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val state = combine(preferences.state, syncDao.observePendingCount()) { persisted, pending ->
        CloudSyncState(
            account = persisted.account,
            enabled = persisted.enabled,
            syncOriginalFiles = persisted.syncOriginalFiles,
            syncFonts = persisted.syncFonts,
            wifiOnlyForLargeFiles = persisted.wifiOnlyForLargeFiles,
            phase = persisted.phase,
            lastSyncTime = persisted.lastSyncTime,
            pendingCount = pending,
            errorMessage = persisted.error,
        )
    }.stateIn(scope, SharingStarted.Eagerly, CloudSyncState())

    override suspend fun connect(activity: Activity): GoogleConnectResult {
        preferences.markAuthorizing()
        return accounts.connect(activity).also { handleAuthorizationResult(it) }
    }

    override suspend fun finishAuthorization(activity: Activity, resultData: Intent?): GoogleConnectResult {
        preferences.markAuthorizing()
        return accounts.finishAuthorization(activity, resultData).also { handleAuthorizationResult(it) }
    }

    override suspend fun disconnect() {
        scheduler.cancel()
        accounts.disconnect()
    }

    override suspend fun setEnabled(enabled: Boolean) {
        preferences.setEnabled(enabled)
        if (enabled) { scheduler.ensurePeriodic(); scheduler.requestImmediate() } else scheduler.cancel()
    }

    override suspend fun setSyncOriginalFiles(enabled: Boolean) {
        preferences.setSyncOriginals(enabled)
        if (enabled) engine.enqueueAllCurrentState()
        scheduler.requestDebounced()
    }

    override suspend fun setSyncFonts(enabled: Boolean) {
        preferences.setSyncFonts(enabled)
        if (enabled) engine.enqueueAllCurrentState()
        scheduler.requestDebounced()
    }

    override suspend fun setWifiOnlyForLargeFiles(enabled: Boolean) {
        preferences.setWifiOnly(enabled)
        scheduler.requestDebounced()
    }

    override fun syncNow() = scheduler.requestImmediate()

    override suspend fun deleteCloudData(activity: Activity): Result<Unit> = runCatching {
        val authorization = accounts.authorize(activity)
        require(authorization is GoogleConnectResult.Connected) { "需要先完成 Google Drive 授权" }
        val token = accounts.accessToken() ?: error("需要重新授权 Google Drive")
        engine.deleteAllCloudData(token)
        preferences.setEnabled(false)
        scheduler.cancel()
    }

    private suspend fun handleAuthorizationResult(result: GoogleConnectResult) {
        when (result) {
            GoogleConnectResult.Connected -> scheduler.requestImmediate()
            is GoogleConnectResult.NeedsAuthorization -> Unit
            is GoogleConnectResult.Failed -> preferences.markAuthRequired(result.message)
        }
    }
}
