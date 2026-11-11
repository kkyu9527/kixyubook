package com.kixyu9527.kixyubook.core.sync

import android.os.SystemClock
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.toDiagnosticFailure
import com.kixyu9527.kixyubook.core.common.model.*
import kotlinx.coroutines.*
import java.util.UUID

/** Bounded progress/heartbeat logging. Never includes names, paths, contents, tokens or accounts. */
internal class CloudBookOpenTrace(
    private val bookUuid: String,
    private val onProgress: (BookPreparationProgress) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val background: Boolean = false,
    private val record: (Long, String?, Map<String, Any?>) -> Unit = { elapsed, outcome, details ->
        DiagnosticLog.record(DiagnosticLog.Category.SYNC, "cloud_book_open", elapsed, outcome, details)
    },
) {
    private val run = UUID.randomUUID().toString().take(8)
    private var progress = BookPreparationProgress(BookPreparationStage.WAITING_SYNC)
    private var started = clock()
    private var lastPublished = Long.MIN_VALUE
    private var format: BookFormat? = null
    private var sourceBytes = 0L
    private var localFile = false
    private var directoryChapters = 0

    init { emit(null); onProgress(progress) }

    @Synchronized fun update(value: BookPreparationProgress) {
        val now = clock()
        if (value.stage != progress.stage) {
            emit("success")
            started = now
            progress = value
            emit(null)
            lastPublished = now
            onProgress(value)
        } else {
            progress = value
            if (lastPublished == Long.MIN_VALUE || now - lastPublished >= 250) {
                lastPublished = now
                onProgress(value)
            }
        }
    }

    @Synchronized fun heartbeat() = emit(null)

    @Synchronized fun describe(format: BookFormat, bytes: Long, localFile: Boolean, directoryChapters: Int) {
        this.format = format
        this.sourceBytes = bytes.coerceAtLeast(0)
        this.localFile = localFile
        this.directoryChapters = directoryChapters
        emit(null)
    }

    @Synchronized fun retry(attempt: Int, statusCode: Int?) =
        emit(null, mapOf("attempt" to attempt, "statusCode" to statusCode))

    @Synchronized fun finish(error: Throwable?) {
        val outcome = when (error) {
            null -> "success"
            is CancellationException -> "interrupted"
            is DriveHttpException -> "drive_http_error"
            else -> error.toDiagnosticFailure().outcome
        }
        val extra = buildMap<String, Any?> {
            if (error != null && error !is CancellationException) put("reason", error.toDiagnosticFailure().reason)
            if (error is DriveHttpException) put("statusCode", error.statusCode)
        }
        emit(outcome, extra)
    }

    private fun emit(outcome: String?, extra: Map<String, Any?> = emptyMap()) {
        record((clock() - started).coerceAtLeast(0), outcome, mapOf(
            "book" to bookUuid.take(8), "run" to run, "openStage" to progress.stage.name,
            "receivedBytes" to progress.downloadedBytes, "totalBytes" to maxOf(progress.totalBytes, sourceBytes),
            "format" to format?.name, "localFile" to localFile, "directoryChapters" to directoryChapters,
            "background" to background,
        ) + extra)
    }
}

internal suspend fun traceCloudBookOpen(
    bookUuid: String,
    onProgress: (BookPreparationProgress) -> Unit,
    background: Boolean = false,
    block: suspend () -> Result<Unit>,
): Result<Unit> = coroutineScope {
    val trace = CloudBookOpenTrace(bookUuid, onProgress, background = background)
    val heartbeat = launch(Dispatchers.IO) { while (isActive) { delay(5_000); trace.heartbeat() } }
    try {
        val result = withContext(BookPreparationObserver(trace::update, trace::retry, trace::describe)) { block() }
        trace.finish(result.exceptionOrNull())
        result
    } catch (error: CancellationException) {
        trace.finish(error)
        throw error
    } catch (error: Exception) {
        trace.finish(error)
        Result.failure(error)
    } finally { heartbeat.cancel() }
}
