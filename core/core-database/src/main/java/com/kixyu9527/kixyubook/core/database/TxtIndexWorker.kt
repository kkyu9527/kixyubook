package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class TxtIndexWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val bookUuid = inputData.getString(KEY_BOOK_UUID) ?: return Result.failure()
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            TxtIndexWorkerEntryPoint::class.java,
        ).repository()
        return try {
            repository.continueTxtIndex(
                bookUuid = bookUuid,
                runId = inputData.getString(KEY_RUN_ID),
                sourceId = inputData.getString(KEY_SOURCE_ID),
                displayName = inputData.getString(KEY_DISPLAY_NAME).orEmpty(),
            )
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_BOOK_UUID = "book_uuid"
        const val KEY_RUN_ID = "run_id"
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_DISPLAY_NAME = "display_name"

        fun uniqueName(bookUuid: String) = "txt-index-$bookUuid"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface TxtIndexWorkerEntryPoint {
    fun repository(): LocalBookRepository
}
