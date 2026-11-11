package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncRemoteInboxEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shelf/binary transfers are network-bound; installs still serialize on the metadata gate.
 * Eight books times the downloader's eight ranges is the practical ceiling: HttpURLConnection is
 * blocking and Dispatchers.IO tops out at 64 threads, so more lanes would only queue and invite
 * Drive rate limiting.
 */
internal const val MAX_PARALLEL_BOOK_DOWNLOADS = 8

/** Shelf first, then automatic bodies. Receipts live in Room until installation succeeds. */
internal class CloudBookSourceQueue(
    private val books: BookDao,
    private val syncDao: SyncDao,
    private val checkpoint: () -> Unit,
    private val prepare: suspend (String) -> Unit,
    private val withLedger: suspend (suspend () -> List<BookEntity>) -> List<BookEntity> = { it() },
) {
    /** A failed book must not prevent the other queued books or small sync objects from finishing. */
    suspend fun download(
        remote: Map<String, DriveObject>,
        preferredBookUuid: String?,
        enabled: Boolean,
        networkAllowed: Boolean,
        onNetworkDeferred: suspend () -> Unit = {},
        onProgress: suspend (Int, Int) -> Unit,
    ): Throwable? {
        val queue = withLedger {
            val received = syncDao.remoteInbox().map { it.objectKey }.toSet()
            val dirty = syncDao.allPending().flatMap(::keysForMutationOrEmpty).toSet()
            val queue = books.getAllBooks().filter { book ->
                val key = "books/${book.uuid}/source"
                key in remote && key !in dirty && "books/${book.uuid}/metadata" !in dirty &&
                    (key in received || book.storagePath.isBlank() || !File(book.storagePath).isFile ||
                        books.getChapterCount(book.uuid) == 0)
            }.sortedWith(compareByDescending<BookEntity> {
                it.uuid == preferredBookUuid
            }.thenByDescending {
                maxOf(it.lastOpenedTime, remote["progress/${it.uuid}"]?.modifiedAt ?: 0)
            }.thenBy { remote["books/${it.uuid}/source"]?.size ?: Long.MAX_VALUE })
            if (queue.isEmpty()) return@withLedger emptyList()
            // Also repair a missing local file whose remote version was applied in an earlier run.
            // Persist the work even when network policy currently prevents the automatic transfer.
            syncDao.retainRemoteChanges(queue.mapNotNull { book ->
                val key = "books/${book.uuid}/source"
                if (key in received) null else remote.getValue(key).let {
                    SyncRemoteInboxEntity(it.objectKey, it.id, it.name, it.mimeType, it.modifiedAt, it.version, it.size, it.md5)
                }
            })
            if (enabled && !networkAllowed) queue.forEach {
                syncDao.updateBookTransfer("books/${it.uuid}/source", "WAITING_NETWORK")
            }
            queue
        }
        if (queue.isEmpty()) return null
        if (!enabled) return null
        if (!networkAllowed) { onNetworkDeferred(); return null }
        val progressMutex = Mutex()
        var completed = 0
        var firstFailure: Throwable? = null
        onProgress(0, queue.size)
        val next = AtomicInteger()
        coroutineScope {
            List(minOf(MAX_PARALLEL_BOOK_DOWNLOADS, queue.size)) { async {
                while (true) {
                    val book = queue.getOrNull(next.getAndIncrement()) ?: break
                    try {
                        checkpoint()
                        prepare(book.uuid)
                        progressMutex.withLock { onProgress(++completed, queue.size) }
                    } catch (error: Exception) {
                        // Authorization and foreground handoffs affect the whole lane. Ordinary
                        // per-book failures retain their receipt and allow healthy siblings ahead.
                        if (error is CancellationException ||
                            error is CloudSyncEngine.AuthorizationRequiredException ||
                            error is DriveHttpException && error.statusCode == 401) throw error
                        progressMutex.withLock { if (firstFailure == null) firstFailure = error }
                        if (error is CloudSyncYieldedException) break
                    }
                }
            } }.awaitAll()
        }
        return firstFailure
    }
}

internal fun isUnmeteredNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    return manager.getNetworkCapabilities(manager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
}
