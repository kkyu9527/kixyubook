package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kixyu9527.kixyubook.core.common.repository.BackupRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class BackupOperationType { EXPORT, RESTORE }

enum class BackupTaskPhase { IDLE, ENQUEUED, RUNNING, SUCCEEDED, FAILED }

data class BackupTaskState(
    val workId: UUID? = null,
    val operation: BackupOperationType? = null,
    val phase: BackupTaskPhase = BackupTaskPhase.IDLE,
    val bookCount: Int? = null,
    val requiresRestart: Boolean = false,
    val error: String? = null,
) {
    val isActive: Boolean get() = phase == BackupTaskPhase.ENQUEUED || phase == BackupTaskPhase.RUNNING
}

@Singleton
class BackupWorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(BackupTaskState())
    val state: kotlinx.coroutines.flow.StateFlow<BackupTaskState> = _state

    fun enqueue(operation: BackupOperationType, uri: String) {
        if (_state.value.isActive) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(
                Data.Builder()
                    .putString(BackupWorker.KEY_OPERATION, operation.name)
                    .putString(BackupWorker.KEY_URI, uri)
                    .build(),
            )
            .build()
        _state.value = BackupTaskState(request.id, operation, BackupTaskPhase.ENQUEUED)
        // In-process state prevents accidental replacement. REPLACE also recovers cleanly when
        // the process was recreated while an older request is still registered in WorkManager.
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    internal fun markRunning(id: UUID, operation: BackupOperationType) {
        _state.value = BackupTaskState(id, operation, BackupTaskPhase.RUNNING)
    }

    internal fun markSucceeded(id: UUID, operation: BackupOperationType, bookCount: Int, requiresRestart: Boolean) {
        _state.value = BackupTaskState(
            workId = id,
            operation = operation,
            phase = BackupTaskPhase.SUCCEEDED,
            bookCount = bookCount,
            requiresRestart = requiresRestart,
        )
    }

    internal fun markFailed(id: UUID, operation: BackupOperationType, error: String) {
        _state.value = BackupTaskState(id, operation, BackupTaskPhase.FAILED, error = error)
    }

    internal fun markCancelled(id: UUID) {
        if (_state.value.workId == id) _state.value = BackupTaskState()
    }

    private companion object {
        const val WORK_NAME = "manual-full-backup"
    }
}

class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = coroutineScope {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            BackupWorkerEntryPoint::class.java,
        )
        val operation = inputData.getString(KEY_OPERATION)
            ?.let { runCatching { BackupOperationType.valueOf(it) }.getOrNull() }
            ?: return@coroutineScope Result.failure()
        val uri = inputData.getString(KEY_URI) ?: return@coroutineScope Result.failure()
        val scheduler = dependencies.backupScheduler()
        val notifications = dependencies.notifications()
        scheduler.markRunning(id, operation)

        var foregroundJob: Job? = launch {
            notifications.awaitBackgroundOrDelay(FOREGROUND_NOTIFICATION_DELAY_MILLIS)
            try {
                setForeground(foregroundInfo(notifications, operation))
            } catch (_: RuntimeException) {
                // The operation can still complete if Android rejects foreground promotion.
            }
        }
        try {
            val result = when (operation) {
                BackupOperationType.EXPORT -> dependencies.backups().exportTo(uri)
                BackupOperationType.RESTORE -> dependencies.backups().restoreFrom(uri)
            }
            result.fold(
                onSuccess = { backup ->
                    scheduler.markSucceeded(id, operation, backup.bookCount, backup.requiresRestart)
                    notifications.showBackupResult(operation, backup.bookCount, null)
                    Result.success()
                },
                onFailure = { error ->
                    val message = error.message ?: "完整备份任务失败"
                    scheduler.markFailed(id, operation, message)
                    notifications.showBackupResult(operation, null, message)
                    Result.failure()
                },
            )
        } catch (cancelled: CancellationException) {
            scheduler.markCancelled(id)
            throw cancelled
        } finally {
            foregroundJob?.cancel()
            foregroundJob = null
        }
    }

    private fun foregroundInfo(
        notifications: LocalNotificationManager,
        operation: BackupOperationType,
    ): ForegroundInfo {
        val notification = notifications.backupProgressNotification(operation, id)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                LocalNotificationManager.NOTIFICATION_BACKUP_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(LocalNotificationManager.NOTIFICATION_BACKUP_PROGRESS, notification)
        }
    }

    companion object {
        const val KEY_OPERATION = "backup_operation"
        const val KEY_URI = "backup_uri"
        private const val FOREGROUND_NOTIFICATION_DELAY_MILLIS = 10_000L
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupWorkerEntryPoint {
    fun backups(): BackupRepository
    fun backupScheduler(): BackupWorkScheduler
    fun notifications(): LocalNotificationManager
}
