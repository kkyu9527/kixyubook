package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.diagnostics.toDiagnosticFailure
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncObjectStateEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** File network I/O never holds the metadata/account mutex. Only planning and commits do. */
internal class CloudBookTransferService(
    private val context: Context,
    private val database: KixyuDatabase,
    private val repository: BookRepository,
    private val mutations: RoomSyncMutationRecorder,
    private val preferences: SyncPreferencesStore,
    accounts: GoogleAccountClient,
    private val gate: CloudSyncAccountGate,
    drive: DriveAppDataClient,
    private val remoteState: CloudRemoteObjectApplier,
    private val foreground: CloudForegroundRequests,
    private val transport: CloudBookTransferTransport = DriveBookTransferTransport(accounts, drive),
) {
    private val books = database.bookDao()
    private val dao = database.syncDao()
    private val locks = mutableMapOf<String, Pair<Mutex, Int>>()
    private val live = MutableStateFlow<Map<String, BookPreparationProgress>>(emptyMap())
    val progress: StateFlow<Map<String, BookPreparationProgress>> = live.asStateFlow()

    suspend fun ensure(uuid: String, background: Boolean, onProgress: (BookPreparationProgress) -> Unit = {}): Result<Unit> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val updates = launch { live.map { it[uuid] }.filterNotNull().distinctUntilChanged().collect(onProgress) }
                try {
                    val result = withBookLock(uuid) {
                        traceCloudBookOpen(uuid, { value -> live.update { it + (uuid to value) } }, background) {
                            runCatching { prepare(uuid, background) }.onFailure { if (it is CancellationException) throw it }
                        }
                    }
                    // A single ordered delivery lane prevents an older queued progress callback
                    // from overwriting a newer direct callback. READY is delivered after it drains.
                    updates.cancelAndJoin()
                    if (result.isSuccess) onProgress(BookPreparationProgress(BookPreparationStage.READY))
                    result
                } finally { updates.cancel() }
            }
        }

    private suspend fun <T> withBookLock(uuid: String, block: suspend () -> T): T {
        val mutex = synchronized(locks) {
            val entry = locks[uuid] ?: (Mutex() to 0)
            locks[uuid] = entry.first to entry.second + 1
            entry.first
        }
        try { return mutex.withLock { block() } } finally {
            synchronized(locks) {
                val entry = locks.getValue(uuid)
                if (entry.second == 1) { locks.remove(uuid); live.update { it - uuid } }
                else locks[uuid] = mutex to entry.second - 1
            }
        }
    }

    private suspend fun prepare(uuid: String, background: Boolean) {
        requireCanonicalSyncUuid(uuid, "book uuid")
        val key = "books/$uuid/source"
        val cached = books.getBook(uuid) ?: error(context.getString(R.string.sync_object_missing))
        val file = cached.storagePath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
        reportBookSource(BookFormat.valueOf(cached.format), file?.length() ?: dao.receivedObjectSize(key) ?: 0,
            file != null, books.getChapterCount(uuid))
        if (file != null && books.getChapterCount(uuid) > 0 && !dao.hasReceivedObject(key)) {
            reportBookPreparation(BookPreparationStage.READY)
            return
        }
        // A partially installed local file can be repaired without authorization or any network.
        val lease = gate.mutex.withLock {
            gate.finishPendingReset()
            if (file != null && books.getChapterCount(uuid) == 0) {
                check(mutations.withoutRecording { repository.hydrateSyncedBook(cached.toSyncedBook(), file.path) })
                check(books.getChapterCount(uuid) > 0) { context.getString(R.string.sync_local_data_failed) }
            }
            if (file != null && !dao.hasReceivedObject(key)) null else {
                if (preferences.current().account == null) throw CloudSyncEngine.AuthorizationRequiredException(
                    context.getString(R.string.sync_reauthorize))
                gate.transferLease()
            }
        }
        if (lease == null) { reportBookPreparation(BookPreparationStage.READY); return }
        val checkpoint = BackgroundSyncCheckpoint { if (background) foreground.checkpoint(uuid) }
        withContext(lease + checkpoint) {
            val destination = File(context.cacheDir, "cloud-sync/requested-$uuid").also { it.parentFile?.mkdirs() }
            try {
                reportBookPreparation(BookPreparationStage.AUTHORIZATION)
                val token = transport.authorize()
                    ?: throw CloudSyncEngine.AuthorizationRequiredException(context.getString(R.string.sync_reauthorize))
                lease.check()
                reportBookPreparation(BookPreparationStage.LOCATING_SOURCE)
                val received = gate.mutex.withLock { lease.check(); CloudRemoteInbox(database, dao).pending() }
                val source = received[key] ?: transport.find(token, key)
                    ?: error(context.getString(R.string.sync_object_missing))
                gate.mutex.withLock {
                    lease.check()
                    CloudRemoteInbox(database, dao).receive(mapOf(key to source))
                    dao.updateBookTransfer(key, "RUNNING")
                }
                reportBookSource(BookFormat.valueOf(cached.format), source.size, file != null, books.getChapterCount(uuid))
                // Position/bookmark snapshots are normally already received by the small-data
                // worker. Any missing snapshots are fetched beside, not after, the binary.
                val anchors = coroutineScope {
                    val anchors = async { runCatching { prefetchAnchors(uuid, token, lease) }
                        .onFailure { if (it is CancellationException || it is CloudSyncYieldedException) throw it } }
                    if (file == null) {
                        reportBookPreparation(BookPreparationStage.DOWNLOADING)
                        val observer = currentCoroutineContext()[BookPreparationObserver]
                        transport.download(token, source, destination) { bytes ->
                            observer?.report?.invoke(BookPreparationProgress(BookPreparationStage.DOWNLOADING, bytes, source.size))
                        }
                    }
                    anchors.await()
                }
                gate.mutex.withLock {
                    lease.check()
                    check(preferences.current().account?.subject == lease.accountSubject)
                    val current = books.getBook(uuid) ?: error(context.getString(R.string.sync_object_missing))
                    check(mutations.withoutRecording {
                        repository.hydrateSyncedBook(current.toSyncedBook(), file?.path ?: destination.path)
                    }) { context.getString(R.string.sync_object_missing) }
                    check(books.getChapterCount(uuid) > 0) { context.getString(R.string.sync_local_data_failed) }
                    reportBookSource(BookFormat.valueOf(current.format),
                        books.getBook(uuid)?.storagePath?.let(::File)?.length() ?: source.size, true, books.getChapterCount(uuid))
                }
                // A small-data failure must not throw away a binary that finished downloading.
                // Keep the installed file, but do not mark it ready at an unknown reading position.
                anchors.getOrThrow()
                reportBookPreparation(BookPreparationStage.RESTORING_PROGRESS)
                var applied = false
                for (attempt in 0..2) {
                    applied = gate.mutex.withLock {
                        lease.check()
                        val latestProgress = dao.receivedObject("progress/$uuid")
                        if (latestProgress != null && latestProgress.payloadJson == null &&
                            !hasPendingLocalChange("progress/$uuid", dao.allPending())) return@withLock false
                        applyCachedAnchors(uuid, token)
                        database.withTransaction {
                            val previous = dao.objectState(key)
                            dao.upsertObjectState(SyncObjectStateEntity(key, source.id, cached.contentHash,
                                previous?.localChangedAt ?: 0, source.modifiedAt, source.version))
                            if (dao.receivedObject(key)?.matches(source) == true) dao.consumeRemoteChange(key)
                        }
                        true
                    }
                    if (applied) break
                    prefetchAnchors(uuid, token, lease)
                }
                check(applied) { context.getString(R.string.sync_reading_position_changed) }
                reportBookPreparation(BookPreparationStage.READY)
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    gate.mutex.withLock {
                        // A stale transfer must never write an error into the next account's queue.
                        if (runCatching { lease.check() }.isSuccess) {
                            dao.updateBookTransfer(key,
                                if (error is CancellationException || error is CloudSyncYieldedException) "PENDING" else "FAILED",
                                error.toDiagnosticFailure().outcome.takeUnless { error is CancellationException || error is CloudSyncYieldedException },
                                if (error is CancellationException || error is CloudSyncYieldedException) 0 else 1)
                        }
                    }
                }
                throw error
            } finally { destination.delete() }
        }
    }

    private suspend fun prefetchAnchors(uuid: String, token: String, lease: CloudTransferLease) {
        for (key in listOf("progress/$uuid", "bookmarks/$uuid")) {
            val pending = gate.mutex.withLock { lease.check(); dao.receivedObject(key) }
            if (pending?.payloadJson != null) continue
            val info = pending?.let { DriveObject(it.driveFileId, it.name, it.objectKey, it.mimeType, it.modifiedAt, it.version, it.size, it.md5) }
                ?: transport.find(token, key) ?: continue
            val jsonFile = File(context.cacheDir, "cloud-sync/anchor-${java.util.UUID.randomUUID()}")
            try {
                transport.download(token, info, jsonFile) {}
                val json = JSONObject(jsonFile.readText()).toString()
                gate.mutex.withLock {
                    lease.check()
                    val current = dao.receivedObject(key)
                    if (current == null) CloudRemoteInbox(database, dao).receive(mapOf(key to info))
                    dao.cacheReceivedPayload(key, info.id, info.version, info.modifiedAt, json)
                }
            } finally { jsonFile.delete() }
        }
    }

    /** Only cached JSON enters this section: no RPC may hold the account/metadata mutex. */
    private suspend fun applyCachedAnchors(uuid: String, token: String) {
        for (key in listOf("progress/$uuid", "bookmarks/$uuid")) {
            if (hasPendingLocalChange(key, dao.allPending())) continue
            val pending = dao.receivedObject(key)?.takeIf { it.payloadJson != null } ?: continue
            val info = DriveObject(pending.driveFileId, pending.name, key, pending.mimeType,
                pending.modifiedAt, pending.version, pending.size, pending.md5)
            val applied = if (key.startsWith("progress/")) remoteState.applyProgress(token, info) else remoteState.applyBookmarks(token, info)
            if (applied) database.withTransaction {
                val previous = dao.objectState(key)
                dao.upsertObjectState(SyncObjectStateEntity(key, info.id, previous?.localHash,
                    previous?.localChangedAt ?: 0, info.modifiedAt, info.version))
                dao.consumeRemoteChange(key)
            }
        }
    }
}

internal interface CloudBookTransferTransport {
    suspend fun authorize(): String?
    suspend fun find(token: String, key: String): DriveObject?
    suspend fun download(token: String, info: DriveObject, target: File, onProgress: (Long) -> Unit)
}

private class DriveBookTransferTransport(private val accounts: GoogleAccountClient, private val drive: DriveAppDataClient) : CloudBookTransferTransport {
    override suspend fun authorize(): String? = accounts.accessToken()
    override suspend fun find(token: String, key: String): DriveObject? = drive.findByObjectKey(token, key)
    override suspend fun download(token: String, info: DriveObject, target: File, onProgress: (Long) -> Unit) =
        drive.download(token, info.id, target, onProgress, info.size, info.version)
}

private fun BookEntity.toSyncedBook() = SyncedBook(uuid, title, author, description,
    BookFormat.valueOf(format), createdTime, contentHash, category, originalDisplayName,
    titleSort, seriesName, seriesIndex, BookMetadataOwnership(userEditedTitle, userEditedAuthor, userEditedDescription))
