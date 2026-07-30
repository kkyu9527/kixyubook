package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class EpubIndexWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            EpubIndexWorkerEntryPoint::class.java,
        ).repository()
        return try {
            repository.continueAllEpubIndexes()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "epub-full-library-index"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface EpubIndexWorkerEntryPoint {
    fun repository(): LocalBookRepository
}
