package com.kixyu9527.kixyubook.core.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CloudSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    @Volatile private var activePriorityBookUuid: String? = null

    /**
     * Starts a latency-sensitive progress pull followed by a full reconciliation. KEEP is
     * intentional: frequent progress writes must never cancel an upload that is already running.
     */
    fun requestImmediate(preferredBookUuid: String? = null) {
        workManager.cancelUniqueWork(DEBOUNCE_TRIGGER_WORK)
        enqueuePriorityChain(preferredBookUuid, ExistingWorkPolicy.KEEP)
    }

    fun requestFromTrigger() {
        // The debounce/periodic worker must not cancel its own unique work while it is running.
        enqueuePriorityChain(preferredBookUuid = null, ExistingWorkPolicy.KEEP)
    }

    /** Appends a final progress flush without cancelling work that already survived backgrounding. */
    fun requestBackgroundFlush(preferredBookUuid: String? = null) {
        workManager.cancelUniqueWork(DEBOUNCE_TRIGGER_WORK)
        enqueuePriorityChain(preferredBookUuid, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueuePriorityChain(preferredBookUuid: String?, policy: ExistingWorkPolicy) {
        val quick = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(connected)
            .setInputData(workerData(SyncWorkerMode.PRIORITY_BOOK, preferredBookUuid))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        val full = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(connected)
            .setInputData(workerData(SyncWorkerMode.FULL, preferredBookUuid))
            .addTag(FULL_SYNC_WORK_TAG)
            .build()
        workManager.beginUniqueWork(SYNC_CHAIN_WORK, policy, quick)
            .then(full)
            .enqueue()
    }

    fun requestDebounced() {
        val request = OneTimeWorkRequestBuilder<CloudSyncTriggerWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setConstraints(connected)
            .build()
        workManager.enqueueUniqueWork(DEBOUNCE_TRIGGER_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun requestDebounced(type: SyncEntityType, entityId: String) {
        // Reader progress already has a low-latency in-process lane. Starting the full Drive chain
        // for every page would make that upload wait behind metadata and large-file reconciliation.
        if (type == SyncEntityType.PROGRESS && entityId == activePriorityBookUuid) return
        requestDebounced()
    }

    fun setActivePriorityBook(bookUuid: String?) {
        activePriorityBookUuid = bookUuid
    }

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<CloudSyncTriggerWorker>(6, TimeUnit.HOURS)
            .setConstraints(connected)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(SYNC_CHAIN_WORK)
        workManager.cancelUniqueWork(DEBOUNCE_TRIGGER_WORK)
        workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    /**
     * DataStore keeps the last public phase across process death, while WorkManager is the source
     * of truth for whether a full sync is currently executing. Fail closed on query errors so a
     * transient database issue never hides a real synchronization.
     */
    suspend fun isFullSyncRunning(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            workManager.getWorkInfosForUniqueWork(SYNC_CHAIN_WORK)
                .get(WORK_INFO_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .any { work ->
                    work.state == WorkInfo.State.RUNNING &&
                        FULL_SYNC_WORK_TAG in work.tags
                }
        }.getOrDefault(true)
    }

    private fun workerData(mode: SyncWorkerMode, preferredBookUuid: String?) = Data.Builder()
        .putString(CloudSyncWorker.KEY_MODE, mode.name)
        .apply { preferredBookUuid?.let { putString(CloudSyncWorker.KEY_BOOK_UUID, it) } }
        .build()

    private companion object {
        const val SYNC_CHAIN_WORK = "google-cloud-sync-chain"
        const val DEBOUNCE_TRIGGER_WORK = "google-cloud-sync-debounce-trigger"
        const val PERIODIC_WORK = "google-cloud-sync-periodic"
        const val FULL_SYNC_WORK_TAG = "google-cloud-sync-full"
        const val WORK_INFO_QUERY_TIMEOUT_SECONDS = 5L
    }
}

/** Periodic work only triggers the same unique one-shot lane used by every other sync source. */
class CloudSyncTriggerWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(
            applicationContext,
            CloudSyncWorkerEntryPoint::class.java,
        ).scheduler().requestFromTrigger()
        return Result.success()
    }
}

private enum class SyncWorkerMode { PRIORITY_BOOK, FULL }

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val engine = EntryPointAccessors.fromApplication(
            applicationContext,
            CloudSyncWorkerEntryPoint::class.java,
        ).engine()
        val mode = inputData.getString(KEY_MODE)
            ?.let { runCatching { SyncWorkerMode.valueOf(it) }.getOrNull() }
            ?: SyncWorkerMode.FULL
        val preferredBookUuid = inputData.getString(KEY_BOOK_UUID)
        if (mode == SyncWorkerMode.FULL && engine.requiresLongRunningWorker()) {
            setForeground(syncForegroundInfo())
        }
        val operation = when (mode) {
            SyncWorkerMode.PRIORITY_BOOK -> engine.synchronizePriorityBook(
                preferredBookUuid = preferredBookUuid,
                followedByFullSync = true,
            )
            SyncWorkerMode.FULL -> engine.synchronize(preferredBookUuid)
        }
        return operation.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                if (error is DriveHttpException && error.statusCode in 400..499) Result.failure()
                else Result.retry()
            },
        )
    }

    private fun syncForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Google Drive 同步",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "在后台恢复或上传书籍文件" },
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cloud_sync)
            .setContentTitle("正在同步书籍")
            .setContentText("可离开应用，完成后会自动更新")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_MODE = "sync_mode"
        const val KEY_BOOK_UUID = "preferred_book_uuid"
        private const val CHANNEL_ID = "cloud_sync"
        private const val NOTIFICATION_ID = 2107
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudSyncWorkerEntryPoint {
    fun engine(): CloudSyncEngine
    fun scheduler(): CloudSyncScheduler
}
