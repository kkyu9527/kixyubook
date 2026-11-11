package com.kixyu9527.kixyubook.core.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/** Bounded network concurrency, but individual durable acknowledgements, not one giant retry. */
internal suspend fun replayRemoteTombstones(
    entries: List<DriveObject>,
    needsPayload: suspend (DriveObject) -> Boolean,
    download: suspend (DriveObject) -> JSONObject,
    apply: suspend (DriveObject, JSONObject) -> Unit,
    acknowledge: suspend (DriveObject) -> Unit,
    onProgress: suspend (Int, Int) -> Unit = { _, _ -> },
) {
    var completed = 0
    // Checkpoints are written before the next network batch, so a failure near the end of a
    // first-device restore never restarts hundreds of already-applied deletions.
    entries.chunked(4).forEach { batch ->
        val downloads = coroutineScope {
            batch.map { info -> async {
                try {
                    info to Result.success(if (needsPayload(info)) download(info) else null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    info to Result.failure<JSONObject?>(error)
                }
            } }.awaitAll()
        }
        var failure: Throwable? = null
        downloads.forEach { (info, result) ->
            if (result.isFailure) {
                failure = failure ?: result.exceptionOrNull()
            } else {
                result.getOrNull()?.let { apply(info, it) }
                acknowledge(info)
                completed++
                onProgress(completed, entries.size)
            }
        }
        failure?.let { throw it }
    }
}
