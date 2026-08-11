package com.kixyu9527.kixyubook.core.sync

import android.app.Activity
import android.content.Intent
import com.kixyu9527.kixyubook.core.common.repository.CloudSyncCoordinator
import com.kixyu9527.kixyubook.core.common.repository.PriorityBookSyncPhase
import com.kixyu9527.kixyubook.core.common.repository.PriorityBookSyncState
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveCloudSyncManager @Inject constructor(
    private val preferences: SyncPreferencesStore,
    private val syncDao: SyncDao,
    private val accounts: GoogleAccountClient,
    private val drive: DriveAppDataClient,
    private val engine: CloudSyncEngine,
    private val scheduler: CloudSyncScheduler,
    private val notifications: LocalNotificationManager,
) : CloudSyncManager, CloudSyncCoordinator {
    @Volatile private var accountSwitchPending = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initialSyncDecision = MutableStateFlow<InitialSyncDecision?>(null)
    private val inspectingInitialSync = MutableStateFlow(false)
    private val initialSyncMutex = Mutex()
    private data class AccountStorageQuota(
        val accountSubject: String? = null,
        val state: DriveStorageQuotaState = DriveStorageQuotaState(),
        val refreshedAt: Long = 0L,
    )
    private val storageQuota = MutableStateFlow(AccountStorageQuota())
    private var storageQuotaJob: Job? = null
    private val _priorityBookSync = MutableStateFlow(PriorityBookSyncState())
    override val priorityBookSync = _priorityBookSync
    private var priorityBookJob: Job? = null
    private val priorityRefresh = Channel<String>(Channel.CONFLATED)
    @Volatile private var activeBookUuid: String? = null
    @Volatile private var lastReaderBookUuid: String? = null
    override val state = combine(
        preferences.state,
        syncDao.observePendingCount(),
        initialSyncDecision,
        inspectingInitialSync,
        storageQuota,
    ) { persisted, pending, decision, inspecting, storage ->
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
            initialSyncDecision = decision ?: persisted.conflicts.takeIf { it.isNotEmpty() }?.let { conflicts ->
                InitialSyncDecision(
                    localBookCount = 0,
                    cloudBookCount = 0,
                    conflicts = conflicts,
                )
            },
            inspectingInitialSync = inspecting,
            storageQuota = storage.state.takeIf {
                storage.accountSubject == persisted.account?.subject
            } ?: DriveStorageQuotaState(),
        )
    }.stateIn(scope, SharingStarted.Eagerly, CloudSyncState())

    init {
        scope.launch { clearStaleSyncPhase() }
        scope.launch {
            preferences.state
                .map { it.account?.subject to it.lastSyncTime }
                .distinctUntilChanged()
                .collect { (accountSubject, _) ->
                    if (accountSubject == null) {
                        storageQuotaJob?.cancel()
                        storageQuota.value = AccountStorageQuota()
                    } else {
                        refreshStorageQuota()
                    }
                }
        }
        scope.launch {
            state.map { it.initialSyncDecision?.conflicts.orEmpty() }
                .distinctUntilChanged()
                .collectLatest { conflicts ->
                    if (conflicts.isEmpty()) {
                        notifications.clearSyncConflict()
                    } else {
                        notifications.showSyncConflict(conflicts.size, conflicts.fingerprint())
                    }
                }
        }
        scope.launch {
            preferences.state
                .map { Triple(it.account?.subject, it.initialSyncApproved, it.enabled) }
                .distinctUntilChanged()
                .collect { (accountSubject, approved, _) ->
                    if (accountSubject != null && !approved) prepareInitialSync()
                }
        }
    }

    override suspend fun connect(activity: Activity): GoogleConnectResult {
        preferences.markAuthorizing()
        return accounts.connect(activity).also { handleAuthorizationResult(it) }
    }

    override suspend fun switchAccount(activity: Activity): GoogleConnectResult {
        accountSwitchPending = true
        preferences.markAuthorizing()
        return accounts.switchAccount(activity).also { result ->
            handleAuthorizationResult(result, preserveAccountOnFailure = true)
            if (result !is GoogleConnectResult.NeedsAuthorization) accountSwitchPending = false
        }
    }

    override suspend fun finishAuthorization(activity: Activity, resultData: Intent?): GoogleConnectResult {
        preferences.markAuthorizing()
        val preserveAccountOnFailure = accountSwitchPending
        return accounts.finishAuthorization(activity, resultData).also { result ->
            handleAuthorizationResult(result, preserveAccountOnFailure)
            if (result !is GoogleConnectResult.NeedsAuthorization) accountSwitchPending = false
        }
    }

    override suspend fun disconnect() {
        scheduler.cancel()
        initialSyncDecision.value = null
        inspectingInitialSync.value = false
        accounts.disconnect()
        notifications.clearAuthorizationFailure()
        notifications.clearSyncConflict()
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

    override suspend fun resolveInitialSync(choice: InitialSyncChoice): Result<Unit> {
        val decision = initialSyncDecision.value
        val conflicts = decision?.conflicts ?: preferences.current().conflicts
        if (conflicts.isEmpty()) return Result.success(Unit)
        inspectingInitialSync.value = true
        return try {
            runCatching {
                if (choice == InitialSyncChoice.USE_CLOUD_CHANGES) {
                    engine.discardLocalChanges(conflicts)
                    preferences.clearLocalConflictPreference()
                } else {
                    engine.acceptLocalChanges(conflicts)
                    preferences.preferLocalConflictsFor(LOCAL_CONFLICT_PREFERENCE_MILLIS)
                }
                preferences.approveInitialSync()
                initialSyncDecision.value = null
                notifications.clearSyncConflict()
                scheduler.ensurePeriodic()
                scheduler.requestImmediate()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                preferences.markError(error.message ?: "同步冲突处理失败")
            }
        } finally {
            // StateFlow assignment is non-suspending, so cancellation cannot strand the sheet in
            // its inspecting state.
            inspectingInitialSync.value = false
        }
    }

    override fun syncNow() = scheduler.requestImmediate()

    override fun refreshStorageQuota(force: Boolean) {
        if (storageQuotaJob?.isActive == true) return
        storageQuotaJob = scope.launch(Dispatchers.IO) {
            val persisted = preferences.current()
            val accountSubject = persisted.account?.subject ?: return@launch
            val previous = storageQuota.value.takeIf { it.accountSubject == accountSubject }
            val now = System.currentTimeMillis()
            if (
                !force &&
                previous?.state?.quota != null &&
                previous.state.errorMessage == null &&
                previous.refreshedAt >= now - STORAGE_QUOTA_CACHE_MILLIS
            ) return@launch
            storageQuota.value = AccountStorageQuota(
                accountSubject = accountSubject,
                state = DriveStorageQuotaState(
                    quota = previous?.state?.quota,
                    refreshing = true,
                ),
                refreshedAt = previous?.refreshedAt ?: 0L,
            )
            try {
                val token = accounts.accessToken()
                    ?: error("需要重新授权 Google Drive")
                val quota = drive.storageQuota(token)
                if (preferences.current().account?.subject == accountSubject) {
                    storageQuota.value = AccountStorageQuota(
                        accountSubject = accountSubject,
                        state = DriveStorageQuotaState(quota = quota),
                        refreshedAt = System.currentTimeMillis(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (preferences.current().account?.subject == accountSubject) {
                    storageQuota.value = AccountStorageQuota(
                        accountSubject = accountSubject,
                        state = DriveStorageQuotaState(
                            quota = previous?.state?.quota,
                            errorMessage = "暂时无法获取云空间",
                        ),
                        refreshedAt = previous?.refreshedAt ?: 0L,
                    )
                }
            }
        }
    }

    override fun onAppForeground() {
        notifications.onAppForeground()
        scope.launch {
            clearStaleSyncPhase()
            var persisted = preferences.current()
            if (persisted.phase == CloudSyncPhase.AUTH_REQUIRED && persisted.account != null) {
                val recovered = runCatching { accounts.accessToken() }.getOrNull() != null
                if (!recovered) return@launch
                preferences.markIdle()
                persisted = preferences.current()
            }
            if (!persisted.enabled || persisted.account == null || !persisted.initialSyncApproved) return@launch
            scheduler.ensurePeriodic()
            val hasPendingChanges = syncDao.pending(limit = 1).isNotEmpty()
            val remoteCheckDue = System.currentTimeMillis() - persisted.lastSyncTime >=
                FOREGROUND_REMOTE_CHECK_INTERVAL_MILLIS
            if (activeBookUuid == null && (hasPendingChanges || remoteCheckDue)) {
                scheduler.requestImmediate(lastReaderBookUuid)
            }
        }
    }

    override fun onAppBackground() {
        notifications.onAppBackground()
        scope.launch {
            val persisted = preferences.current()
            persisted.conflicts.takeIf { it.isNotEmpty() }?.let { conflicts ->
                notifications.showSyncConflict(conflicts.size, conflicts.fingerprint())
            }
            if (!persisted.enabled || persisted.account == null || !persisted.initialSyncApproved) return@launch
            if (syncDao.pending(limit = 1).isNotEmpty()) {
                scheduler.requestBackgroundFlush(activeBookUuid ?: lastReaderBookUuid)
            }
        }
    }

    override fun prioritizeBook(bookUuid: String) {
        activeBookUuid = bookUuid
        lastReaderBookUuid = bookUuid
        scheduler.setActivePriorityBook(bookUuid)
        if (
            priorityBookJob?.isActive == true &&
            _priorityBookSync.value.bookUuid == bookUuid
        ) {
            if (_priorityBookSync.value.phase != PriorityBookSyncPhase.PULLING) {
                priorityRefresh.trySend(bookUuid)
            }
            return
        }
        priorityBookJob?.cancel()
        _priorityBookSync.value = PriorityBookSyncState(bookUuid, PriorityBookSyncPhase.PULLING)
        scheduler.ensurePeriodic()
        priorityBookJob = scope.launch(Dispatchers.IO) {
            val persisted = preferences.current()
            if (!persisted.enabled || persisted.account == null || !persisted.initialSyncApproved) {
                _priorityBookSync.value = PriorityBookSyncState(bookUuid, PriorityBookSyncPhase.READY)
                return@launch
            }
            var pullRemote = true
            while (isActive && activeBookUuid == bookUuid) {
                var succeeded = false
                engine.synchronizePriorityBook(
                    preferredBookUuid = bookUuid,
                    pullRemote = pullRemote,
                ).fold(
                    onSuccess = {
                        succeeded = true
                        if (activeBookUuid == bookUuid) {
                            _priorityBookSync.value = PriorityBookSyncState(bookUuid, PriorityBookSyncPhase.READY)
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        if (pullRemote && activeBookUuid == bookUuid) {
                            _priorityBookSync.value = PriorityBookSyncState(
                                bookUuid,
                                PriorityBookSyncPhase.ERROR,
                                error.message ?: "当前书籍同步失败",
                            )
                        }
                    },
                )
                if (succeeded) pullRemote = false
                var requestedBook: String
                do {
                    requestedBook = priorityRefresh.receive()
                } while (requestedBook != bookUuid)
                // Coalesce the page-settle writes generated by one gesture without making the
                // user-visible pull wait for WorkManager's debounce window.
                delay(PRIORITY_WRITE_COALESCE_MILLIS)
                while (priorityRefresh.tryReceive().getOrNull() != null) {
                    // Drain writes already covered by the coalescing window.
                }
            }
        }
    }

    override fun releaseBook(bookUuid: String) {
        if (activeBookUuid != bookUuid) return
        activeBookUuid = null
        scheduler.setActivePriorityBook(null)
        lastReaderBookUuid = bookUuid
        priorityBookJob?.cancel()
        priorityBookJob = null
        if (_priorityBookSync.value.bookUuid == bookUuid) {
            _priorityBookSync.value = PriorityBookSyncState()
        }
        scope.launch {
            val persisted = preferences.current()
            if (
                persisted.enabled &&
                persisted.account != null &&
                persisted.initialSyncApproved &&
                syncDao.pending(limit = 1).isNotEmpty()
            ) {
                scheduler.ensurePeriodic()
                scheduler.requestBackgroundFlush(bookUuid)
            }
        }
    }

    override suspend fun deleteCloudData(activity: Activity): Result<Unit> = runCatching {
        val authorization = accounts.authorize(activity)
        require(authorization is GoogleConnectResult.Connected) { "需要先完成 Google Drive 授权" }
        val token = accounts.accessToken() ?: error("需要重新授权 Google Drive")
        engine.deleteAllCloudData(token)
        preferences.setEnabled(false)
        scheduler.cancel()
        storageQuota.value = storageQuota.value.copy(refreshedAt = 0L)
        refreshStorageQuota(force = true)
    }

    private suspend fun handleAuthorizationResult(
        result: GoogleConnectResult,
        preserveAccountOnFailure: Boolean = false,
    ) {
        when (result) {
            GoogleConnectResult.Connected -> {
                notifications.clearAuthorizationFailure()
                refreshStorageQuota(force = true)
                val persisted = preferences.current()
                if (persisted.initialSyncApproved) {
                    // Explicit sign-in means syncing should be active. This also repairs an older
                    // state where an approved account was persisted while automatic sync was off.
                    if (!persisted.enabled) preferences.setEnabled(true)
                    scheduler.ensurePeriodic()
                    scheduler.requestImmediate()
                } else {
                    prepareInitialSync()
                }
            }
            is GoogleConnectResult.NeedsAuthorization -> Unit
            is GoogleConnectResult.Failed -> {
                if (preserveAccountOnFailure && preferences.current().account != null) {
                    preferences.markIdle()
                } else {
                    preferences.markAuthRequired()
                }
            }
        }
    }

    private suspend fun prepareInitialSync() = initialSyncMutex.withLock {
        val persisted = preferences.current()
        if (
            persisted.account == null ||
            persisted.initialSyncApproved ||
            persisted.conflicts.isNotEmpty() ||
            initialSyncDecision.value != null
        ) return@withLock
        val accountSubject = persisted.account.subject
        inspectingInitialSync.value = true
        try {
            val snapshot = engine.inspectInitialSync()
            // Disconnecting while the remote inspection is running must not re-enable sync for
            // an account that is no longer connected.
            if (preferences.current().account?.subject != accountSubject) return@withLock
            // An empty local library on an unapproved installation is the normal new-device
            // case. Clear any stale local sync bookkeeping and restore the existing cloud
            // library automatically; an empty client must never offer to erase cloud data.
            when {
                snapshot.shouldRestoreFromCloud() -> {
                    engine.prepareCloudRestore()
                    approveAndSchedule()
                }
                snapshot.requiresUserDecision() -> {
                    preferences.setConflicts(snapshot.conflicts)
                    initialSyncDecision.value = snapshot
                    notifications.showSyncConflict(snapshot.conflicts.size, snapshot.conflicts.fingerprint())
                }
                else -> approveAndSchedule()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (preferences.current().account?.subject == accountSubject) {
                if (
                    error is CloudSyncEngine.AuthorizationRequiredException ||
                    (error is DriveHttpException && error.statusCode == 401)
                ) {
                    preferences.markAuthRequired()
                } else {
                    preferences.markError(error.message ?: "无法检查云端书库")
                }
            }
        } finally {
            inspectingInitialSync.value = false
        }
    }

    private suspend fun approveAndSchedule() {
        preferences.approveInitialSync()
        initialSyncDecision.value = null
        scheduler.ensurePeriodic()
        scheduler.requestImmediate()
    }

    private suspend fun clearStaleSyncPhase() {
        val persisted = preferences.current()
        if (persisted.phase != CloudSyncPhase.SYNCING) return
        if (
            shouldClearStaleSyncPhase(
                phase = persisted.phase,
                fullSyncRunning = scheduler.isFullSyncRunning(),
            )
        ) {
            preferences.markInterrupted()
        }
    }

    private companion object {
        const val LOCAL_CONFLICT_PREFERENCE_MILLIS = 5 * 60_000L
        const val FOREGROUND_REMOTE_CHECK_INTERVAL_MILLIS = 5 * 60_000L
        const val PRIORITY_WRITE_COALESCE_MILLIS = 600L
        const val STORAGE_QUOTA_CACHE_MILLIS = 60_000L
    }
}

private fun List<InitialSyncConflict>.fingerprint(): String =
    sortedWith(compareBy({ it.entityType.name }, { it.entityId }))
        .joinToString("|") { "${it.entityType.name}:${it.entityId}" }

internal fun shouldClearStaleSyncPhase(
    phase: CloudSyncPhase,
    fullSyncRunning: Boolean,
): Boolean = phase == CloudSyncPhase.SYNCING && !fullSyncRunning
