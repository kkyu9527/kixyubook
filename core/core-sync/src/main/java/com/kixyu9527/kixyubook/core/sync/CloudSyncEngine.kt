package com.kixyu9527.kixyubook.core.sync

import android.os.SystemClock
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: KixyuDatabase,
    private val books: BookDao,
    private val fonts: FontDao,
    private val syncDao: SyncDao,
    private val bookRepository: BookRepository,
    private val fontRepository: FontRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val preferences: SyncPreferencesStore,
    private val mutations: RoomSyncMutationRecorder,
    private val drive: DriveAppDataClient,
    private val accountClient: GoogleAccountClient,
    private val notifications: LocalNotificationManager,
) {
    private val syncMutex = Mutex()

    private val remoteState = CloudRemoteStateApplier(
        context = context,
        database = database,
        books = books,
        fonts = fonts,
        syncDao = syncDao,
        bookRepository = bookRepository,
        settingsRepository = settingsRepository,
        preferences = preferences,
        mutations = mutations,
        drive = drive,
    )
    private val preparedSnapshotLock = Any()
    private var preparedInitialSnapshot: PreparedInitialSnapshot? = null

    suspend fun inspectInitialSync(): InitialSyncDecision = withContext(Dispatchers.IO) {
        val token = accountClient.accessToken()
            ?: throw AuthorizationRequiredException("需要重新授权 Google Drive")
        val (objects, pageToken) = coroutineScope {
            val objects = async { drive.listAll(token) }
            val pageToken = async { drive.startPageToken(token) }
            objects.await() to pageToken.await()
        }
        val remote = objects.associateBy(DriveObject::objectKey)
        preferences.current().account?.subject?.let { accountSubject ->
            synchronized(preparedSnapshotLock) {
                preparedInitialSnapshot = PreparedInitialSnapshot(
                    accountSubject = accountSubject,
                    snapshot = RemoteSnapshot(
                        known = remote.toMutableMap(),
                        changed = remote.toMutableMap(),
                        nextPageToken = pageToken,
                    ),
                    createdAt = System.currentTimeMillis(),
                )
            }
        }
        val restorableBooks = remote.keys.asSequence()
            .filter { it.startsWith("books/") && it.endsWith("/metadata") }
            .mapNotNull { it.split('/').getOrNull(1) }
            .filter { "books/$it/source" in remote }
            .distinct()
            .count()
        InitialSyncDecision(
            localBookCount = books.getAllBooks().size,
            cloudBookCount = restorableBooks,
            conflicts = findProvenConflicts(remote),
        )
    }

    suspend fun discardLocalChanges(conflicts: List<InitialSyncConflict>) = withContext(Dispatchers.IO) {
        conflicts.forEach { conflict ->
            syncDao.removeOutbox(conflict.entityType.name, conflict.entityId)
        }
    }

    suspend fun acceptLocalChanges(conflicts: List<InitialSyncConflict>) = withContext(Dispatchers.IO) {
        val token = accountClient.accessToken()
            ?: throw AuthorizationRequiredException("需要重新授权 Google Drive")
        val remote = drive.listAll(token).associateBy(DriveObject::objectKey)
        acceptLocalChanges(conflicts, remote)
    }

    private suspend fun acceptLocalChanges(
        conflicts: List<InitialSyncConflict>,
        remote: Map<String, DriveObject>,
    ) {
        conflicts.forEach { conflict ->
            val key = mutableKeyForConflict(conflict.entityType, conflict.entityId) ?: return@forEach
            val cloud = remote[key] ?: return@forEach
            val baseline = syncDao.objectState(key)
            syncDao.upsertObjectState(
                SyncObjectStateEntity(
                    objectKey = key,
                    driveFileId = cloud.id,
                    localHash = baseline?.localHash,
                    localChangedAt = baseline?.localChangedAt ?: 0,
                    remoteModifiedAt = cloud.modifiedAt,
                    remoteVersion = cloud.version,
                ),
            )
        }
    }

    suspend fun prepareCloudRestore() = withContext(Dispatchers.IO) {
        // A disconnected device may still contain queued deletions. Once the user explicitly
        // chooses cloud restore, those local mutations must not hide or delete remote books.
        syncDao.clearOutbox()
        syncDao.clearObjectStates()
    }

    suspend fun synchronize(
        preferredBookUuid: String? = null,
        onProgress: suspend (CloudSyncProgress) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val syncStartedAt = SystemClock.elapsedRealtime()
        DiagnosticLog.record(Category.SYNC, "full_sync_started", details = mapOf("preferredBook" to (preferredBookUuid != null)))
        syncMutex.withLock { runCatching {
            val persisted = preferences.current()
            if (!persisted.enabled || persisted.account == null || !persisted.initialSyncApproved) {
                DiagnosticLog.record(Category.SYNC, "full_sync_skipped", outcome = "not_ready")
                return@runCatching
            }
            if (persisted.conflicts.isNotEmpty()) {
                DiagnosticLog.record(Category.SYNC, "full_sync_skipped", outcome = "conflict_waiting", details = mapOf("count" to persisted.conflicts.size))
                return@runCatching
            }
            preferences.markRunning()
            val token = accountClient.accessToken()
                ?: throw AuthorizationRequiredException("需要重新授权 Google Drive")
            DiagnosticLog.record(Category.SYNC, "authorization_ready")
            val snapshot = loadRemoteSnapshot(token, persisted)
            DiagnosticLog.record(
                Category.SYNC,
                "remote_snapshot_loaded",
                elapsedMs = SystemClock.elapsedRealtime() - syncStartedAt,
                details = mapOf("known" to snapshot.known.size, "changed" to snapshot.changed.size),
            )
            var remote = snapshot.known

            applyRemoteTombstones(token, snapshot.changed)
            resolveDirtyProgress(token, remote)
            val conflicts = findProvenConflicts(remote)
            if (conflicts.isNotEmpty()) {
                val preferLocal = shouldPreferLocalConflicts(
                    preferLocalUntil = persisted.preferLocalConflictsUntil,
                    now = System.currentTimeMillis(),
                )
                if (preferLocal) {
                    // The user already chose local for this sync session. Advance the baseline to
                    // the latest Drive versions and let the dirty local objects upload normally.
                    acceptLocalChanges(conflicts, remote)
                } else {
                    preferences.clearLocalConflictPreference()
                    preferences.setConflicts(conflicts)
                    DiagnosticLog.record(Category.SYNC, "conflicts_waiting", outcome = "user_action", details = mapOf("count" to conflicts.size))
                    return@runCatching
                }
            }
            applyRemoteChanges(
                token = token,
                changedRemote = snapshot.changed,
                knownRemote = remote,
                initialMergeComplete = persisted.initialMergeComplete,
                preferredBookUuid = preferredBookUuid,
                onProgress = onProgress,
            )

            if (!persisted.initialMergeComplete) seedInitialOutbox()
            val pending = syncDao.pending().sortedWith(
                compareBy<SyncOutboxEntity> {
                    when {
                        it.entityType == SyncEntityType.SETTINGS.name -> 0
                        it.entityType == SyncEntityType.PROGRESS.name && it.entityId == preferredBookUuid -> 1
                        it.entityType == SyncEntityType.PROGRESS.name -> 2
                        it.entityType == SyncEntityType.BOOK.name || it.entityType == SyncEntityType.FONT.name -> 4
                        else -> 3
                    }
                }.thenBy { it.changedAt },
            )
            DiagnosticLog.record(Category.SYNC, "upload_queue_ready", details = mapOf("count" to pending.size))
            var uploadedCount = 0
            if (pending.isNotEmpty()) {
                onProgress(
                    CloudSyncProgress(
                        title = "正在上传云端更改",
                        text = "已处理 0/${pending.size} 项",
                        completed = 0,
                        total = pending.size,
                    ),
                )
            }
            val uploadBatch = mutableListOf<SyncOutboxEntity>()
            suspend fun flushUploadBatch() {
                if (uploadBatch.isEmpty()) return
                val batchSize = uploadBatch.size
                val knownRemote = remote.toMap()
                val uploaded = coroutineScope {
                    uploadBatch.map { mutation ->
                        async { pushMutation(token, mutation, knownRemote) }
                    }.awaitAll().flatten()
                }
                uploaded.forEach { (key, value) -> remote[key] = value }
                uploadBatch.clear()
                uploadedCount += batchSize
                onProgress(
                    CloudSyncProgress(
                        title = "正在上传云端更改",
                        text = "已处理 $uploadedCount/${pending.size} 项",
                        completed = uploadedCount,
                        total = pending.size,
                    ),
                )
            }
            pending.forEach { mutation ->
                if (mutation.operation == SyncMutationOperation.DELETE.name) {
                    flushUploadBatch()
                    try {
                        remote = pushDeletion(token, mutation, remote)
                        uploadedCount++
                        onProgress(
                            CloudSyncProgress(
                                title = "正在上传云端更改",
                                text = "已处理 $uploadedCount/${pending.size} 项",
                                completed = uploadedCount,
                                total = pending.size,
                            ),
                        )
                    } catch (error: Throwable) {
                        if (error !is CancellationException) syncDao.markAttempts(listOf(mutation.uuid))
                        throw error
                    }
                } else {
                    uploadBatch += mutation
                    if (uploadBatch.size >= MAX_CONCURRENT_UPLOADS) flushUploadBatch()
                }
            }
            flushUploadBatch()
            preferences.markSuccess(snapshot.nextPageToken ?: persisted.pageToken)
            notifications.clearAuthorizationFailure()
            DiagnosticLog.record(
                Category.SYNC,
                "full_sync_finished",
                elapsedMs = SystemClock.elapsedRealtime() - syncStartedAt,
                outcome = "success",
                details = mapOf("uploaded" to uploadedCount, "remoteChanged" to snapshot.changed.size),
            )
        }.onFailure { error ->
            DiagnosticLog.record(
                Category.SYNC,
                "full_sync_finished",
                elapsedMs = SystemClock.elapsedRealtime() - syncStartedAt,
                outcome = error::class.simpleName ?: "error",
            )
            if (error is CancellationException) {
                withContext(NonCancellable) { preferences.markInterrupted() }
                throw error
            }
            if (error is AuthorizationRequiredException || (error is DriveHttpException && error.statusCode == 401)) {
                if (error is DriveHttpException) accountClient.invalidateAccessToken()
                preferences.markAuthRequired()
            } else {
                preferences.markError(error.message ?: "同步失败")
            }
        } }
    }

    /**
     * Exchanges only the current (or most recently read) book's progress. It deliberately does
     * not touch the global Drive change cursor, allowing the following durable full worker to
     * reconcile metadata, deletions and binary files without losing changes.
     */
    suspend fun synchronizePriorityBook(
        preferredBookUuid: String?,
        followedByFullSync: Boolean = false,
        pullRemote: Boolean = true,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock { runCatching {
            val persisted = preferences.current()
            if (!persisted.enabled || persisted.account == null || !persisted.initialSyncApproved) {
                return@runCatching
            }
            if (persisted.conflicts.isNotEmpty()) return@runCatching
            val bookUuid = preferredBookUuid
                ?: books.getAllProgress().maxByOrNull(ReadingProgressEntity::updatedTime)?.bookUuid
                ?: return@runCatching
            if (!books.bookExists(bookUuid)) return@runCatching
            // This priority stage is intentionally silent. The following FULL worker owns the
            // persisted global phase; otherwise a cancelled/delayed continuation can leave the
            // app displaying SYNCING after this quick stage has already finished.
            val token = accountClient.accessToken()
                ?: throw AuthorizationRequiredException("需要重新授权 Google Drive")
            val key = "progress/$bookUuid"
            var remote = findRemoteObject(token, key, "progress-$bookUuid.json")
            val mutationBeforePull = syncDao.allPending().lastOrNull {
                it.entityType == SyncEntityType.PROGRESS.name && it.entityId == bookUuid
            }
            if (
                pullRemote && remote != null &&
                mutationBeforePull?.operation == SyncMutationOperation.DELETE.name
            ) {
                DiagnosticLog.record(
                    Category.SYNC,
                    "priority_pull_skipped",
                    outcome = "local_delete",
                    details = mapOf("entity" to "progress", "book" to bookUuid.take(8)),
                )
            }

            var cloudTime = Long.MIN_VALUE
            if (pullRemote && remote != null && shouldPullPriorityRemote(mutationBeforePull, remote.modifiedAt)) {
                try {
                    withJsonDownload(token, requireNotNull(remote)) { json ->
                        cloudTime = json.optLong("updatedTime")
                        val localTime = books.getProgress(bookUuid)?.updatedTime ?: Long.MIN_VALUE
                        if (cloudTime > localTime && remoteState.applyProgressJson(json)) {
                            mutationBeforePull?.let { syncDao.removeOutbox(listOf(it.uuid)) }
                        }
                    }
                } catch (error: DriveHttpException) {
                    if (error.statusCode != 404) throw error
                    syncDao.removeObjectState(key)
                    remote = drive.findByObjectKey(token, key)
                    remote?.let { refreshed ->
                        withJsonDownload(token, refreshed) { json ->
                            cloudTime = json.optLong("updatedTime")
                            val localTime = books.getProgress(bookUuid)?.updatedTime ?: Long.MIN_VALUE
                            if (cloudTime > localTime && remoteState.applyProgressJson(json)) {
                                mutationBeforePull?.let { syncDao.removeOutbox(listOf(it.uuid)) }
                            }
                        }
                    }
                }
                remote?.let { currentRemote ->
                    rememberRemote(key, currentRemote)
                }
            }

            if (pullRemote) {
                // Bookmarks and reader settings join the initial reader pull. Progress writes made
                // afterwards only upload their coalesced JSON and avoid three redundant Drive reads.
                pullPriorityJson(
                    token = token,
                    key = "bookmarks/$bookUuid",
                    name = "bookmarks-$bookUuid.json",
                    type = SyncEntityType.BOOKMARKS,
                    entityId = bookUuid,
                    apply = remoteState::applyBookmarksJson,
                )
                pullPriorityJson(
                    token = token,
                    key = "settings/global",
                    name = "settings-global.json",
                    type = SyncEntityType.SETTINGS,
                    entityId = "global",
                    apply = remoteState::applySettingsJson,
                )
            }

            val mutation = syncDao.allPending().lastOrNull {
                it.entityType == SyncEntityType.PROGRESS.name &&
                    it.entityId == bookUuid &&
                    it.operation == SyncMutationOperation.UPSERT.name
            }
            val localProgress = books.getProgress(bookUuid)
            if (mutation != null && localProgress != null && localProgress.updatedTime >= cloudTime) {
                val local = materialize(mutation, includeLargePayload = false).singleOrNull()
                if (local != null) {
                    try {
                        val uploaded = drive.upload(
                            token = token,
                            name = local.name,
                            objectKey = local.key,
                            mimeType = local.mimeType,
                            source = local.file,
                            existingFileId = remote?.id,
                        )
                        rememberRemote(key, uploaded)
                        // Delete only the row represented by this upload snapshot. A page turn
                        // that landed while Drive was responding has a new UUID and remains dirty.
                        syncDao.removeOutbox(listOf(mutation.uuid))
                    } finally {
                        if (local.temporary) local.file.delete()
                    }
                }
            }
            notifications.clearAuthorizationFailure()
        }.onFailure { error ->
            if (error is CancellationException) {
                if (followedByFullSync) {
                    withContext(NonCancellable) { preferences.markInterrupted() }
                }
                throw error
            }
            if (error is AuthorizationRequiredException || (error is DriveHttpException && error.statusCode == 401)) {
                if (error is DriveHttpException) accountClient.invalidateAccessToken()
                preferences.markAuthRequired()
            } else if (followedByFullSync) {
                preferences.markError(error.message ?: "同步失败")
            }
        } }
    }

    private suspend fun findRemoteObject(token: String, key: String, name: String): DriveObject? {
        val state = syncDao.objectState(key)
        return state?.driveFileId?.let { driveFileId ->
            DriveObject(
                id = driveFileId,
                name = name,
                objectKey = key,
                mimeType = "application/json",
                modifiedAt = state.remoteModifiedAt,
                version = state.remoteVersion,
                size = 0,
                md5 = null,
            )
        } ?: drive.findByObjectKey(token, key)
    }

    private suspend fun pullPriorityJson(
        token: String,
        key: String,
        name: String,
        type: SyncEntityType,
        entityId: String,
        apply: suspend (JSONObject) -> Unit,
    ) {
        val known = syncDao.objectState(key)
        var remote = drive.findByObjectKey(token, key) ?: return
        if (known != null && remote.version != 0L && remote.version <= known.remoteVersion) return
        val localMutation = syncDao.allPending().lastOrNull {
            it.entityType == type.name && it.entityId == entityId
        }
        if (!shouldPullPriorityRemote(localMutation, remote.modifiedAt)) {
            if (localMutation?.operation == SyncMutationOperation.DELETE.name) {
                DiagnosticLog.record(
                    Category.SYNC,
                    "priority_pull_skipped",
                    outcome = "local_delete",
                    details = mapOf("entity" to type.name.lowercase(), "book" to entityId.take(8)),
                )
            }
            return
        }
        try {
            withJsonDownload(token, remote) { json -> apply(json) }
        } catch (error: DriveHttpException) {
            if (error.statusCode != 404) throw error
            syncDao.removeObjectState(key)
            remote = drive.findByObjectKey(token, key) ?: return
            withJsonDownload(token, remote) { json -> apply(json) }
        }
        // Keep an edit created while the cloud object was being downloaded. Its replacement
        // outbox row carries another UUID and must be uploaded on the next flush.
        localMutation?.let { syncDao.removeOutbox(listOf(it.uuid)) }
        rememberRemote(key, remote)
    }

    suspend fun requiresLongRunningWorker(): Boolean = withContext(Dispatchers.IO) {
        val persisted = preferences.current()
        if (!persisted.enabled || persisted.account == null || !persisted.initialSyncApproved) return@withContext false
        if (!persisted.initialMergeComplete) return@withContext true
        syncDao.allPending().any { mutation ->
            when (runCatching { SyncEntityType.valueOf(mutation.entityType) }.getOrNull()) {
                SyncEntityType.BOOK -> persisted.syncOriginalFiles &&
                    books.getBook(mutation.entityId)?.storagePath?.let(::File)?.isFile == true
                SyncEntityType.FONT -> persisted.syncFonts &&
                    fonts.getFont(mutation.entityId)?.filePath?.let(::File)?.isFile == true
                else -> false
            }
        }
    }

    private suspend fun loadRemoteSnapshot(
        token: String,
        persisted: PersistedSyncState,
    ): RemoteSnapshot {
        if (!persisted.initialMergeComplete || persisted.pageToken == null) {
            takePreparedInitialSnapshot(persisted.account?.subject)?.let { return it }
            val all = drive.listAll(token).associateBy(DriveObject::objectKey).toMutableMap()
            return RemoteSnapshot(
                known = all.toMutableMap(),
                changed = all,
                nextPageToken = drive.startPageToken(token),
            )
        }

        val states = syncDao.allObjectStates()
        val known = states.mapNotNull { state ->
            val fileId = state.driveFileId ?: return@mapNotNull null
            state.objectKey to DriveObject(
                id = fileId,
                name = state.objectKey.substringAfterLast('/'),
                objectKey = state.objectKey,
                mimeType = if (state.objectKey.endsWith("/source")) {
                    "application/octet-stream"
                } else {
                    "application/json"
                },
                modifiedAt = state.remoteModifiedAt,
                version = state.remoteVersion,
                size = 0,
                md5 = null,
            )
        }.toMap().toMutableMap()

        val page = try {
            drive.listChanges(token, persisted.pageToken)
        } catch (error: DriveHttpException) {
            if (error.statusCode != 410) throw error
            val all = drive.listAll(token).associateBy(DriveObject::objectKey).toMutableMap()
            return RemoteSnapshot(all.toMutableMap(), all, drive.startPageToken(token))
        }
        val changed = mutableMapOf<String, DriveObject>()
        val statesByFileId = states.mapNotNull { state -> state.driveFileId?.let { it to state } }.toMap()
        page.changes.forEach { change ->
            if (change.removed) {
                statesByFileId[change.fileId]?.let { state ->
                    known.remove(state.objectKey)
                    syncDao.removeObjectState(state.objectKey)
                }
            } else {
                change.file?.let { file ->
                    known[file.objectKey] = file
                    changed[file.objectKey] = file
                }
            }
        }
        return RemoteSnapshot(
            known = known,
            changed = changed,
            nextPageToken = page.newStartPageToken ?: persisted.pageToken,
        )
    }

    private fun takePreparedInitialSnapshot(accountSubject: String?): RemoteSnapshot? =
        synchronized(preparedSnapshotLock) {
            val prepared = preparedInitialSnapshot ?: return@synchronized null
            preparedInitialSnapshot = null
            if (
                accountSubject == null ||
                prepared.accountSubject != accountSubject ||
                System.currentTimeMillis() - prepared.createdAt > PREPARED_SNAPSHOT_MAX_AGE_MILLIS
            ) return@synchronized null
            RemoteSnapshot(
                known = prepared.snapshot.known.toMutableMap(),
                changed = prepared.snapshot.changed.toMutableMap(),
                nextPageToken = prepared.snapshot.nextPageToken,
            )
        }

    suspend fun deleteAllCloudData(token: String) = withContext(Dispatchers.IO) {
        drive.listAll(token).forEach { drive.delete(token, it.id) }
        syncDao.clearObjectStates()
        syncDao.clearOutbox()
        syncDao.clearTombstones()
    }

    suspend fun enqueueAllCurrentState() = withContext(Dispatchers.IO) { seedInitialOutbox() }

    private suspend fun findProvenConflicts(
        remote: Map<String, DriveObject>,
    ): List<InitialSyncConflict> {
        val states = syncDao.allObjectStates().associateBy(SyncObjectStateEntity::objectKey)
        return syncDao.allPending().mapNotNull { mutation ->
            if (mutation.operation == SyncMutationOperation.DELETE.name) return@mapNotNull null
            val type = runCatching { SyncEntityType.valueOf(mutation.entityType) }.getOrNull()
                ?: return@mapNotNull null
            val mutableKey = mutableKeyForConflict(type, mutation.entityId) ?: return@mapNotNull null
            val baseline = states[mutableKey] ?: return@mapNotNull null
            val cloud = remote[mutableKey] ?: return@mapNotNull null
            if (cloud.modifiedAt <= baseline.remoteModifiedAt) return@mapNotNull null
            InitialSyncConflict(type, mutation.entityId)
        }.distinct()
    }

    private suspend fun resolveDirtyProgress(
        token: String,
        remote: Map<String, DriveObject>,
    ) {
        syncDao.allPending().forEach { mutation ->
            if (
                mutation.entityType != SyncEntityType.PROGRESS.name ||
                mutation.operation != SyncMutationOperation.UPSERT.name
            ) return@forEach
            val cloud = remote["progress/${mutation.entityId}"] ?: return@forEach
            val localTime = books.getProgress(mutation.entityId)?.updatedTime ?: Long.MIN_VALUE
            var cloudTime = Long.MIN_VALUE
            withJsonDownload(token, cloud) { json -> cloudTime = json.optLong("updatedTime") }
            if (cloudTime > localTime) {
                syncDao.removeOutbox(listOf(mutation.uuid))
            }
        }
    }

    private fun mutableKeyForConflict(type: SyncEntityType, entityId: String): String? = when (type) {
        SyncEntityType.BOOK -> "books/$entityId/metadata"
        SyncEntityType.BOOKMARKS -> "bookmarks/$entityId"
        SyncEntityType.SETTINGS -> "settings/global"
        // Progress is resolved automatically by updatedTime, sessions are additive,
        // and source/font objects are immutable for a stable UUID.
        SyncEntityType.PROGRESS,
        SyncEntityType.SESSION,
        SyncEntityType.FONT,
        -> null
    }

    private suspend fun seedInitialOutbox() {
        books.getAllBooks().forEach { mutations.record(SyncEntityType.BOOK, it.uuid) }
        books.getAllProgress().forEach { mutations.record(SyncEntityType.PROGRESS, it.bookUuid) }
        books.getAllBookmarkEntities().map(BookMarkOwner::from).map(BookMarkOwner::bookUuid).distinct()
            .forEach { mutations.record(SyncEntityType.BOOKMARKS, it) }
        mutations.record(SyncEntityType.SETTINGS, "global")
        books.getAllSessions().forEach { session ->
            val syncUuid = session.syncUuid.ifBlank { "legacy-${session.id}" }
            mutations.record(SyncEntityType.SESSION, syncUuid)
        }
        if (preferences.current().syncFonts) {
            fonts.getAllFonts().forEach { mutations.record(SyncEntityType.FONT, it.uuid) }
        }
    }

    private suspend fun materialize(
        mutation: SyncOutboxEntity,
        includeLargePayload: Boolean,
    ): List<LocalCloudObject> = when (
        SyncEntityType.valueOf(mutation.entityType)
    ) {
        SyncEntityType.BOOK -> books.getBook(mutation.entityId)?.let { book ->
            buildList {
                add(jsonObject("books/${book.uuid}/metadata", bookMetadataJson(book)))
                if (preferences.current().syncOriginalFiles && includeLargePayload) {
                    val source = File(book.storagePath)
                    if (source.isFile) add(
                        LocalCloudObject(
                            key = "books/${book.uuid}/source",
                            name = "book-${book.uuid}.${book.format.lowercase()}",
                            mimeType = if (book.format == BookFormat.EPUB.name) "application/epub+zip" else "text/plain",
                            file = source,
                        ),
                    )
                }
            }
        }.orEmpty()
        SyncEntityType.PROGRESS -> books.getProgress(mutation.entityId)?.let { progress ->
            val chapterKey = progress.chapterKey.ifBlank {
                books.getChapters(progress.bookUuid).firstOrNull { it.id == progress.chapterId }?.chapterKey.orEmpty()
            }
            listOf(jsonObject("progress/${progress.bookUuid}", progressJson(progress, chapterKey)))
        }.orEmpty()
        SyncEntityType.BOOKMARKS -> {
            val chapters = books.getChapters(mutation.entityId).associateBy { it.id }
            val values = books.getBookmarks(mutation.entityId)
            listOf(jsonObject("bookmarks/${mutation.entityId}", bookmarksJson(mutation.entityId, values, chapters)))
        }
        SyncEntityType.SETTINGS -> listOf(jsonObject("settings/global", settingsJson()))
        SyncEntityType.SESSION -> books.getSessionBySyncUuid(mutation.entityId)?.let {
            listOf(jsonObject("sessions/${it.syncUuid}", sessionJson(it)))
        }.orEmpty()
        SyncEntityType.FONT -> if (preferences.current().syncFonts && includeLargePayload) {
            fonts.getFont(mutation.entityId)?.let { font ->
                listOf(
                    jsonObject("fonts/${font.uuid}/metadata", fontJson(font)),
                    LocalCloudObject(
                        key = "fonts/${font.uuid}/source",
                        name = "font-${font.uuid}.${File(font.filePath).extension.ifBlank { "ttf" }}",
                        mimeType = "application/octet-stream",
                        file = File(font.filePath),
                    ),
                )
            }.orEmpty()
        } else emptyList()
    }

    private suspend fun pushMutation(
        token: String,
        mutation: SyncOutboxEntity,
        knownRemote: Map<String, DriveObject>,
    ): List<Pair<String, DriveObject>> = try {
        val deferredLargePayload = shouldDeferLargePayload(mutation)
        val uploadedObjects = mutableListOf<Pair<String, DriveObject>>()
        materialize(mutation, includeLargePayload = !deferredLargePayload).forEach { local ->
            try {
                val hash = local.file.sha256()
                val known = knownRemote[local.key]
                val baseline = syncDao.objectState(local.key)
                val alreadySynced = baseline != null &&
                    known != null &&
                    baseline.localHash == hash &&
                    known.id == baseline.driveFileId &&
                    known.version == baseline.remoteVersion
                if (!alreadySynced) {
                    val uploaded = drive.upload(
                        token = token,
                        name = local.name,
                        objectKey = local.key,
                        mimeType = local.mimeType,
                        source = local.file,
                        existingFileId = known?.id,
                    )
                    uploadedObjects += local.key to uploaded
                    syncDao.upsertObjectState(
                        SyncObjectStateEntity(
                            objectKey = local.key,
                            driveFileId = uploaded.id,
                            localHash = hash,
                            localChangedAt = mutation.changedAt,
                            remoteModifiedAt = uploaded.modifiedAt,
                            remoteVersion = uploaded.version,
                        ),
                    )
                }
            } finally {
                if (local.temporary) local.file.delete()
            }
        }
        if (!deferredLargePayload) syncDao.removeOutbox(listOf(mutation.uuid))
        uploadedObjects
    } catch (error: Throwable) {
        if (error !is CancellationException) syncDao.markAttempts(listOf(mutation.uuid))
        throw error
    }

    private suspend fun pushDeletion(
        token: String,
        mutation: SyncOutboxEntity,
        currentRemote: MutableMap<String, DriveObject>,
    ): MutableMap<String, DriveObject> {
        val prefix = when (SyncEntityType.valueOf(mutation.entityType)) {
            SyncEntityType.BOOK -> "books/${mutation.entityId}/"
            SyncEntityType.FONT -> "fonts/${mutation.entityId}/"
            SyncEntityType.PROGRESS -> "progress/${mutation.entityId}"
            SyncEntityType.BOOKMARKS -> "bookmarks/${mutation.entityId}"
            SyncEntityType.SESSION -> "sessions/${mutation.entityId}"
            SyncEntityType.SETTINGS -> "settings/global"
        }
        currentRemote.filterKeys { it == prefix || it.startsWith(prefix) }.forEach { (key, file) ->
            drive.delete(token, file.id)
            currentRemote.remove(key)
            syncDao.removeObjectState(key)
        }
        val now = System.currentTimeMillis()
        val expiresAt = PERMANENT_TOMBSTONE_EXPIRY
        val tombstoneKey = "tombstones/${mutation.entityType.lowercase()}/${mutation.entityId}"
        val tombstone = jsonObject(
            tombstoneKey,
            JSONObject()
                .put("type", mutation.entityType)
                .put("entityId", mutation.entityId)
                .put("deletedAt", now)
                .put("deviceId", mutation.deviceId)
                .put("expiresAt", expiresAt),
        )
        val uploaded = try {
            drive.upload(token, tombstone.name, tombstone.key, tombstone.mimeType, tombstone.file, currentRemote[tombstoneKey]?.id)
        } finally {
            tombstone.file.delete()
        }
        currentRemote[tombstoneKey] = uploaded
        syncDao.upsertTombstone(SyncTombstoneEntity(tombstoneKey, now, mutation.deviceId, expiresAt))
        syncDao.removeOutbox(listOf(mutation.uuid))
        return currentRemote
    }

    private suspend fun applyRemoteTombstones(token: String, remote: MutableMap<String, DriveObject>) {
        remote.filterKeys { it.startsWith("tombstones/") }.forEach { (key, objectInfo) ->
            val temp = tempFile("tombstone")
            try {
                drive.download(token, objectInfo.id, temp)
                val json = JSONObject(temp.readText())
                // Keep the field for compatibility with already released clients, but use the
                // largest Long value so old versions never garbage-collect a permanent deletion.
                // Legacy 30-day tombstones are upgraded as soon as a current client sees them.
                if (json.optLong("expiresAt") != PERMANENT_TOMBSTONE_EXPIRY) {
                    json.put("expiresAt", PERMANENT_TOMBSTONE_EXPIRY)
                    val normalized = jsonObject(key, json)
                    try {
                        remote[key] = drive.upload(
                            token = token,
                            name = normalized.name,
                            objectKey = normalized.key,
                            mimeType = normalized.mimeType,
                            source = normalized.file,
                            existingFileId = objectInfo.id,
                        )
                    } finally {
                        normalized.file.delete()
                    }
                }
                val type = runCatching { SyncEntityType.valueOf(json.getString("type")) }.getOrNull() ?: return@forEach
                val id = json.getString("entityId")
                mutations.withoutRecording {
                    when (type) {
                        SyncEntityType.BOOK -> if (books.bookExists(id)) bookRepository.deleteBook(id)
                        SyncEntityType.FONT -> fontRepository.deleteFont(id)
                        else -> Unit
                    }
                }
                syncDao.removeOutbox(type.name, id)
                syncDao.upsertTombstone(
                    SyncTombstoneEntity(
                        objectKey = key,
                        deletedAt = json.optLong("deletedAt"),
                        deviceId = json.optString("deviceId"),
                        expiresAt = PERMANENT_TOMBSTONE_EXPIRY,
                    ),
                )
            } finally {
                temp.delete()
            }
        }
    }

    private suspend fun applyRemoteChanges(
        token: String,
        changedRemote: Map<String, DriveObject>,
        knownRemote: Map<String, DriveObject>,
        initialMergeComplete: Boolean,
        preferredBookUuid: String?,
        onProgress: suspend (CloudSyncProgress) -> Unit,
    ) {
        val dirty = syncDao.pending().flatMap(::keysForMutation).toSet()
        val localStates = syncDao.allObjectStates().associateBy { it.objectKey }
        val handledKeys = mutableSetOf<String>()
        val candidates = changedRemote.filter { (key, value) ->
            !key.startsWith("tombstones/") && key !in dirty &&
                (!initialMergeComplete || value.modifiedAt > (localStates[key]?.remoteModifiedAt ?: 0))
        }

        // Configuration changes affect the presentation and behavior of everything restored
        // afterwards. Apply them before progress and book data during both initial and incremental
        // synchronization, not only during the first shelf rebuild.
        candidates["settings/global"]?.let { info ->
            remoteState.applySettings(token, info)
            rememberRemote("settings/global", info)
            handledKeys += "settings/global"
        }

        // Existing-book progress is the latency-sensitive path. Apply it before metadata/source
        // restoration so entering a book never waits behind unrelated EPUB downloads.
        if (initialMergeComplete) {
            candidates.filterKeys { key ->
                key.startsWith("progress/") &&
                    key.substringAfter("progress/") == preferredBookUuid
            }.forEach { (key, info) ->
                remoteState.applyProgress(token, info)
                rememberRemote(key, info)
                handledKeys += key
            }
        }

        // Treat metadata + source as one logical book. They are uploaded sequentially and may
        // therefore arrive in two Drive change pages; either half must complete the restoration.
        val changedBookUuids = candidates.keys.asSequence()
            .filter { it.startsWith("books/") }
            .mapNotNull { it.split('/').getOrNull(1) }
            .distinct()
            .filter { uuid ->
                "books/$uuid/metadata" !in dirty && "books/$uuid/source" !in dirty
            }
            .toList()

        var restoredBooks = 0
        if (changedBookUuids.isNotEmpty()) {
            onProgress(
                CloudSyncProgress(
                    title = if (initialMergeComplete) "正在下载书籍" else "正在恢复云端书库",
                    text = "已恢复 0/${changedBookUuids.size} 本",
                    completed = 0,
                    total = changedBookUuids.size,
                ),
            )
        }
        suspend fun restoreBook(uuid: String) {
            remoteState.restoreBook(token, uuid, knownRemote)
            handledKeys += "books/$uuid/metadata"
            handledKeys += "books/$uuid/source"
            restoredBooks++
            onProgress(
                CloudSyncProgress(
                    title = if (initialMergeComplete) "正在下载书籍" else "正在恢复云端书库",
                    text = "已恢复 $restoredBooks/${changedBookUuids.size} 本",
                    completed = restoredBooks,
                    total = changedBookUuids.size,
                ),
            )
        }

        if (initialMergeComplete) {
            changedBookUuids.forEach { restoreBook(it) }
        } else {
            val restorePlan = planInitialRestore(changedBookUuids, knownRemote)
            restorePlan.priorityBookUuids.forEach { uuid ->
                restoreBook(uuid)
                candidates["progress/$uuid"]?.let { info ->
                    remoteState.applyProgress(token, info)
                    rememberRemote("progress/$uuid", info)
                    handledKeys += "progress/$uuid"
                }
                candidates["bookmarks/$uuid"]?.let { info ->
                    remoteState.applyBookmarks(token, info)
                    rememberRemote("bookmarks/$uuid", info)
                    handledKeys += "bookmarks/$uuid"
                }
            }

            // Put every other source-backed book on the shelf before restoring its secondary data.
            restorePlan.remainingBookUuids.forEach { restoreBook(it) }
        }

        // A font is represented by two Drive objects. Apply the pair once even when both objects
        // occur in the same change page; the previous per-key loop imported every font twice.
        candidates.keys.asSequence()
            .filter { it.startsWith("fonts/") }
            .mapNotNull { it.split('/').getOrNull(1) }
            .distinct()
            .forEach { uuid ->
                val metadataKey = "fonts/$uuid/metadata"
                val sourceKey = "fonts/$uuid/source"
                val metadata = knownRemote[metadataKey] ?: return@forEach
                val source = knownRemote[sourceKey] ?: return@forEach
                remoteState.applyFont(token, metadata, source)
                rememberRemote(metadataKey, metadata)
                rememberRemote(sourceKey, source)
                handledKeys += metadataKey
                handledKeys += sourceKey
            }

        candidates.forEach { (key, info) ->
            if (key in handledKeys) return@forEach
            when {
                key.startsWith("progress/") -> remoteState.applyProgress(token, info)
                key.startsWith("bookmarks/") -> remoteState.applyBookmarks(token, info)
                key == "settings/global" -> remoteState.applySettings(token, info)
                key.startsWith("sessions/") -> remoteState.applySession(token, info)
            }
            rememberRemote(key, info)
        }
    }

    private suspend fun settingsJson(): JSONObject = JSONObject()
        .put("schema", 1)
        .put("updatedAt", System.currentTimeMillis())
        .put("reader", settingsToJson(settingsRepository.settings.first()))
        .put("readingGoalMinutes", settingsRepository.readingGoalMinutes.first())


    private fun jsonObject(key: String, json: JSONObject): LocalCloudObject {
        val file = tempFile("payload").apply { writeText(json.toString()) }
        return LocalCloudObject(key, "${key.replace('/', '-')}.json", "application/json", file, true)
    }

    private suspend fun withJsonDownload(token: String, info: DriveObject, block: suspend (JSONObject) -> Unit) {
        val file = tempFile("json")
        try { drive.download(token, info.id, file); block(JSONObject(file.readText())) } finally { file.delete() }
    }

    private suspend fun rememberRemote(key: String, value: DriveObject) {
        val previous = syncDao.objectState(key)
        syncDao.upsertObjectState(
            SyncObjectStateEntity(key, value.id, previous?.localHash, previous?.localChangedAt ?: 0, value.modifiedAt, value.version),
        )
    }

    private fun tempFile(prefix: String) = File(context.cacheDir, "cloud-sync/$prefix-${UUID.randomUUID()}")
        .also { it.parentFile?.mkdirs() }

    private suspend fun shouldDeferLargePayload(mutation: SyncOutboxEntity): Boolean {
        val type = SyncEntityType.valueOf(mutation.entityType)
        val hasLargePayload = when (type) {
            SyncEntityType.BOOK -> preferences.current().syncOriginalFiles && books.getBook(mutation.entityId)?.storagePath?.let(::File)?.isFile == true
            SyncEntityType.FONT -> preferences.current().syncFonts && fonts.getFont(mutation.entityId)?.filePath?.let(::File)?.isFile == true
            else -> false
        }
        if (!hasLargePayload || !preferences.current().wifiOnlyForLargeFiles) return false
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
    }

    internal class AuthorizationRequiredException(message: String) : Exception(message)

    private companion object {
        const val MAX_CONCURRENT_UPLOADS = 4
        const val PREPARED_SNAPSHOT_MAX_AGE_MILLIS = 2 * 60_000L
        const val PERMANENT_TOMBSTONE_EXPIRY = Long.MAX_VALUE
    }
}
