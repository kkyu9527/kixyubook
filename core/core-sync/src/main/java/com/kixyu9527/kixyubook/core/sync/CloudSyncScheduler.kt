package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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

    fun requestImmediate() {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(connected)
            .build()
        workManager.enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun requestDebounced() {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setConstraints(connected)
            .build()
        workManager.enqueueUniqueWork(DEBOUNCED_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(connected)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(IMMEDIATE_WORK)
        workManager.cancelUniqueWork(DEBOUNCED_WORK)
        workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    private companion object {
        const val IMMEDIATE_WORK = "google-cloud-sync-now"
        const val DEBOUNCED_WORK = "google-cloud-sync-debounced"
        const val PERIODIC_WORK = "google-cloud-sync-periodic"
    }
}

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val engine = EntryPointAccessors.fromApplication(
            applicationContext,
            CloudSyncWorkerEntryPoint::class.java,
        ).engine()
        return engine.synchronize().fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                if (error is DriveHttpException && error.statusCode in 400..499) Result.failure()
                else Result.retry()
            },
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudSyncWorkerEntryPoint {
    fun engine(): CloudSyncEngine
}
