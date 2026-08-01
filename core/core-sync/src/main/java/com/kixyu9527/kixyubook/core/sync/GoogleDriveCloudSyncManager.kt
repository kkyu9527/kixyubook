package com.kixyu9527.kixyubook.core.sync

import android.app.Activity
import android.content.Intent
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val initialSyncDecision = MutableStateFlow<InitialSyncDecision?>(null)
    private val inspectingInitialSync = MutableStateFlow(false)
    override val state = combine(
        preferences.state,
        syncDao.observePendingCount(),
        initialSyncDecision,
        inspectingInitialSync,
    ) { persisted, pending, decision, inspecting ->
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
            initialSyncDecision = decision,
            inspectingInitialSync = inspecting,
        )
    }.stateIn(scope, SharingStarted.Eagerly, CloudSyncState())

    init {
        scope.launch {
            preferences.state
                .map { Triple(it.account?.subject, it.initialSyncApproved, it.enabled) }
                .distinctUntilChanged()
                .collectLatest { (accountSubject, approved, _) ->
                    if (accountSubject != null && !approved) prepareInitialSync()
                }
        }
    }

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
        initialSyncDecision.value = null
        inspectingInitialSync.value = false
        accounts.disconnect()
    }

    override suspend fun setEnabled(enabled: Boolean) {
        if (enabled && !preferences.current().initialSyncApproved) {
            prepareInitialSync()
            return
        }
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

    override suspend fun resolveInitialSync(choice: InitialSyncChoice): Result<Unit> = runCatching {
        inspectingInitialSync.value = true
        if (choice == InitialSyncChoice.USE_LOCAL_LIBRARY) {
            engine.replaceCloudWithLocalLibrary()
        } else {
            engine.prepareCloudRestore()
        }
        preferences.approveInitialSync()
        initialSyncDecision.value = null
        scheduler.ensurePeriodic()
        scheduler.requestImmediate()
    }.onFailure { error ->
        preferences.markError(error.message ?: "首次同步准备失败")
    }.also {
        inspectingInitialSync.value = false
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
            GoogleConnectResult.Connected -> {
                if (preferences.current().initialSyncApproved) {
                    scheduler.ensurePeriodic()
                    scheduler.requestImmediate()
                } else {
                    prepareInitialSync()
                }
            }
            is GoogleConnectResult.NeedsAuthorization -> Unit
            is GoogleConnectResult.Failed -> preferences.markAuthRequired(result.message)
        }
    }

    private suspend fun prepareInitialSync() {
        if (inspectingInitialSync.value || preferences.current().account == null) return
        inspectingInitialSync.value = true
        runCatching { engine.inspectInitialSync() }
            .onSuccess { snapshot ->
                if (snapshot.requiresUserDecision()) {
                    initialSyncDecision.value = snapshot
                } else {
                    preferences.approveInitialSync()
                    initialSyncDecision.value = null
                    scheduler.ensurePeriodic()
                    scheduler.requestImmediate()
                }
            }
            .onFailure { error -> preferences.markError(error.message ?: "无法检查云端书库") }
        inspectingInitialSync.value = false
    }
}
