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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveCloudSyncManager @Inject constructor(
    private val preferences: SyncPreferencesStore,
    private val syncDao: SyncDao,
    private val accounts: GoogleAccountClient,
    private val engine: CloudSyncEngine,
    private val scheduler: CloudSyncScheduler,
) : CloudSyncManager, CloudSyncCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initialSyncDecision = MutableStateFlow<InitialSyncDecision?>(null)
    private val inspectingInitialSync = MutableStateFlow(false)
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
            initialSyncDecision = decision ?: persisted.conflicts.takeIf { it.isNotEmpty() }?.let { conflicts ->
                InitialSyncDecision(
                    localBookCount = 0,
                    cloudBookCount = 0,
                    conflicts = conflicts,
                )
            },
            inspectingInitialSync = inspecting,
        )
    }.stateIn(scope, SharingStarted.Eagerly, CloudSyncState())

    init {
        scope.launch { clearStaleSyncPhase() }
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

    override fun onAppForeground() {
        scope.launch {
            clearStaleSyncPhase()
            val persisted = preferences.current()
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
        scope.launch {
            val persisted = preferences.current()
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
        try {
            val snapshot = engine.inspectInitialSync()
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
                }
                else -> approveAndSchedule()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            preferences.markError(error.message ?: "无法检查云端书库")
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
    }
}

internal fun shouldClearStaleSyncPhase(
    phase: CloudSyncPhase,
    fullSyncRunning: Boolean,
): Boolean = phase == CloudSyncPhase.SYNCING && !fullSyncRunning
