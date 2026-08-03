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
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    @Volatile private var priorityBookActive = false

    /**
     * Replaces stale queued work with a latency-sensitive progress pull, then completes the full
     * reconciliation in the same unique chain. The quick stage never waits behind book uploads.
     */
    fun requestImmediate(preferredBookUuid: String? = null) {
        workManager.cancelUniqueWork(DEBOUNCE_TRIGGER_WORK)
        enqueuePriorityChain(preferredBookUuid, ExistingWorkPolicy.REPLACE)
    }

    /** Frees the global engine for an in-process visible-book pull. */
    fun pauseForPriorityBook() {
        priorityBookActive = true
        workManager.cancelUniqueWork(SYNC_CHAIN_WORK)
        workManager.cancelUniqueWork(DEBOUNCE_TRIGGER_WORK)
        workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    fun resumeAfterPriorityBook() {
        priorityBookActive = false
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
            .build()
        workManager.beginUniqueWork(SYNC_CHAIN_WORK, policy, quick)
            .then(full)
            .enqueue()
    }

    fun requestDebounced() {
        if (priorityBookActive) return
        val request = OneTimeWorkRequestBuilder<CloudSyncTriggerWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setConstraints(connected)
            .build()
        workManager.enqueueUniqueWork(DEBOUNCE_TRIGGER_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<CloudSyncTriggerWorker>(6, TimeUnit.HOURS)
            .setConstraints(connected)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel() {
        priorityBookActive = false
        workManager.cancelUniqueWork(SYNC_CHAIN_WORK)
        workManager.cancelUniqueWork(DEBOUNCE_TRIGGER_WORK)
        workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    private fun workerData(mode: SyncWorkerMode, preferredBookUuid: String?) = Data.Builder()
        .putString(CloudSyncWorker.KEY_MODE, mode.name)
        .apply { preferredBookUuid?.let { putString(CloudSyncWorker.KEY_BOOK_UUID, it) } }
        .build()

    private companion object {
        const val SYNC_CHAIN_WORK = "google-cloud-sync-chain"
        const val DEBOUNCE_TRIGGER_WORK = "google-cloud-sync-debounce-trigger"
        const val PERIODIC_WORK = "google-cloud-sync-periodic"
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
        ).scheduler().requestImmediate()
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
            SyncWorkerMode.PRIORITY_BOOK -> engine.synchronizePriorityBook(preferredBookUuid)
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
            .setSmallIcon(android.R.drawable.stat_sys_upload)
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
