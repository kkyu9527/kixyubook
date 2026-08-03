package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.withTransaction
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private data class LocalCloudObject(
    val key: String,
    val name: String,
    val mimeType: String,
    val file: File,
    val temporary: Boolean = false,
)

private data class RemoteSnapshot(
    val known: MutableMap<String, DriveObject>,
    val changed: MutableMap<String, DriveObject>,
    val nextPageToken: String?,
)

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
) {
    private val syncMutex = Mutex()

    suspend fun inspectInitialSync(): InitialSyncDecision = withContext(Dispatchers.IO) {
        val token = accountClient.accessToken()
            ?: throw AuthorizationRequiredException("需要重新授权 Google Drive")
        val remote = drive.listAll(token).associateBy(DriveObject::objectKey)
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

    suspend fun synchronize(preferredBookUuid: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock { runCatching {
            val persisted = preferences.current()
            if (!persisted.enabled || persisted.account == null || !persisted.initialSyncApproved) {
                return@runCatching
            }
            if (persisted.conflicts.isNotEmpty()) return@runCatching
            preferences.markRunning()
            val token = accountClient.accessToken()
                ?: throw AuthorizationRequiredException("需要重新授权 Google Drive")
            val snapshot = loadRemoteSnapshot(token, persisted)
            var remote = snapshot.known

            applyRemoteTombstones(token, snapshot.changed)
            resolveDirtyProgress(token, remote)
            val conflicts = findProvenConflicts(remote)
            if (conflicts.isNotEmpty()) {
                preferences.setConflicts(conflicts)
                return@runCatching
            }
            applyRemoteChanges(
                token = token,
                changedRemote = snapshot.changed,
                knownRemote = remote,
                initialMergeComplete = persisted.initialMergeComplete,
                preferredBookUuid = preferredBookUuid,
            )

            if (!persisted.initialMergeComplete) seedInitialOutbox()
            val pending = syncDao.pending().sortedWith(
                compareBy<SyncOutboxEntity> {
                    when {
                        it.entityType == SyncEntityType.PROGRESS.name && it.entityId == preferredBookUuid -> 0
                        it.entityType == SyncEntityType.PROGRESS.name -> 1
                        it.entityType == SyncEntityType.BOOK.name || it.entityType == SyncEntityType.FONT.name -> 3
                        else -> 2
                    }
                }.thenBy { it.changedAt },
            )
            pending.forEach { mutation ->
                try {
                    if (mutation.operation == SyncMutationOperation.DELETE.name) {
                        remote = pushDeletion(token, mutation, remote)
                    } else {
                        val deferredLargePayload = shouldDeferLargePayload(mutation)
                        materialize(mutation, includeLargePayload = !deferredLargePayload).forEach { local ->
                            try {
                                val hash = local.file.sha256()
                                val known = remote[local.key]
                                val uploaded = drive.upload(
                                    token = token,
                                    name = local.name,
                                    objectKey = local.key,
                                    mimeType = local.mimeType,
                                    source = local.file,
                                    existingFileId = known?.id,
                                )
                                remote[local.key] = uploaded
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
                            } finally {
                                if (local.temporary) local.file.delete()
                            }
                        }
                        if (!deferredLargePayload) syncDao.removeOutbox(listOf(mutation.uuid))
                    }
                } catch (error: Throwable) {
                    if (error !is CancellationException) syncDao.markAttempts(listOf(mutation.uuid))
                    throw error
                }
            }
            preferences.markSuccess(snapshot.nextPageToken ?: persisted.pageToken)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            if (error is AuthorizationRequiredException || (error is DriveHttpException && error.statusCode == 401)) {
                if (error is DriveHttpException) accountClient.invalidateAccessToken()
                preferences.markAuthRequired(error.message ?: "需要重新授权 Google Drive")
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
    suspend fun synchronizePriorityBook(preferredBookUuid: String?): Result<Unit> = withContext(Dispatchers.IO) {
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
            preferences.markRunning()
            val token = accountClient.accessToken()
                ?: throw AuthorizationRequiredException("需要重新授权 Google Drive")
            val key = "progress/$bookUuid"
            var remote = findRemoteObject(token, key, "progress-$bookUuid.json")

            var cloudTime = Long.MIN_VALUE
            if (remote != null) {
                try {
                    withJsonDownload(token, requireNotNull(remote)) { json ->
                        cloudTime = json.optLong("updatedTime")
                        val localTime = books.getProgress(bookUuid)?.updatedTime ?: Long.MIN_VALUE
                        if (cloudTime > localTime && applyRemoteProgressJson(json)) {
                            syncDao.removeOutbox(SyncEntityType.PROGRESS.name, bookUuid)
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
                            if (cloudTime > localTime && applyRemoteProgressJson(json)) {
                                syncDao.removeOutbox(SyncEntityType.PROGRESS.name, bookUuid)
                            }
                        }
                    }
                }
                remote?.let { currentRemote ->
                    rememberRemote(key, currentRemote)
                }
            }

            // Bookmarks and reader settings are small JSON objects and belong to the same
            // latency-sensitive reader transaction. Only changed Drive versions are downloaded;
            // original books/fonts remain paused for the durable background channel.
            pullPriorityJson(
                token = token,
                key = "bookmarks/$bookUuid",
                name = "bookmarks-$bookUuid.json",
                type = SyncEntityType.BOOKMARKS,
                entityId = bookUuid,
                apply = ::applyRemoteBookmarksJson,
            )
            pullPriorityJson(
                token = token,
                key = "settings/global",
                name = "settings-global.json",
                type = SyncEntityType.SETTINGS,
                entityId = "global",
                apply = ::applyRemoteSettingsJson,
            )

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
                        syncDao.removeOutbox(SyncEntityType.PROGRESS.name, bookUuid)
                    } finally {
                        if (local.temporary) local.file.delete()
                    }
                }
            }
            preferences.markPrioritySuccess()
        }.onFailure { error ->
            if (error is CancellationException) throw error
            if (error is AuthorizationRequiredException || (error is DriveHttpException && error.statusCode == 401)) {
                if (error is DriveHttpException) accountClient.invalidateAccessToken()
                preferences.markAuthRequired(error.message ?: "需要重新授权 Google Drive")
            } else {
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
        if (localMutation != null && localMutation.changedAt >= remote.modifiedAt) return
        try {
            withJsonDownload(token, remote) { json -> apply(json) }
        } catch (error: DriveHttpException) {
            if (error.statusCode != 404) throw error
            syncDao.removeObjectState(key)
            remote = drive.findByObjectKey(token, key) ?: return
            withJsonDownload(token, remote) { json -> apply(json) }
        }
        syncDao.removeOutbox(type.name, entityId)
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
                syncDao.removeOutbox(SyncEntityType.PROGRESS.name, mutation.entityId)
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
    ) {
        val dirty = syncDao.pending().flatMap(::keysForMutation).toSet()
        val localStates = syncDao.allObjectStates().associateBy { it.objectKey }
        val candidates = changedRemote.filter { (key, value) ->
            !key.startsWith("tombstones/") && key !in dirty &&
                (!initialMergeComplete || value.modifiedAt > (localStates[key]?.remoteModifiedAt ?: 0))
        }

        // Existing-book progress is the latency-sensitive path. Apply it before metadata/source
        // restoration so entering a book never waits behind unrelated EPUB downloads.
        candidates.filterKeys { key ->
            key.startsWith("progress/") &&
                key.substringAfter("progress/") == preferredBookUuid
        }.forEach { (_, info) -> applyRemoteProgress(token, info) }

        // Treat metadata + source as one logical book. They are uploaded sequentially and may
        // therefore arrive in two Drive change pages; either half must complete the restoration.
        val changedBookUuids = candidates.keys.asSequence()
            .filter { it.startsWith("books/") }
            .mapNotNull { it.split('/').getOrNull(1) }
            .distinct()
            .filter { uuid ->
                "books/$uuid/metadata" !in dirty && "books/$uuid/source" !in dirty
            }
        changedBookUuids.forEach { uuid ->
            val key = "books/$uuid/metadata"
            val metadata = knownRemote[key] ?: return@forEach
            val sourceInfo = knownRemote["books/$uuid/source"] ?: return@forEach
            if (!books.bookExists(uuid)) {
                val metaFile = tempFile("book-meta")
                val sourceFile = tempFile("book-source")
                try {
                    drive.download(token, metadata.id, metaFile)
                    drive.download(token, sourceInfo.id, sourceFile)
                    val book = parseBook(JSONObject(metaFile.readText()))
                    mutations.withoutRecording { bookRepository.restoreSyncedBook(book, sourceFile.absolutePath) }
                } finally {
                    metaFile.delete(); sourceFile.delete()
                }
            } else {
                val temp = tempFile("book-meta")
                try {
                    drive.download(token, metadata.id, temp)
                    val book = parseBook(JSONObject(temp.readText()))
                    mutations.withoutRecording {
                        bookRepository.updateBookMetadata(book.uuid, book.title, book.author, book.description)
                        bookRepository.setCategory(book.uuid, book.category)
                    }
                } finally { temp.delete() }
            }
            rememberRemote(key, metadata)
            rememberRemote("books/$uuid/source", sourceInfo)
        }

        candidates.forEach { (key, info) ->
            when {
                key.startsWith("progress/") && key.substringAfter("progress/") != preferredBookUuid ->
                    applyRemoteProgress(token, info)
                key.startsWith("bookmarks/") -> applyRemoteBookmarks(token, info)
                key == "settings/global" -> applyRemoteSettings(token, info)
                key.startsWith("sessions/") -> applyRemoteSession(token, info)
                key.startsWith("fonts/") && (key.endsWith("/metadata") || key.endsWith("/source")) -> {
                    val uuid = key.split('/').getOrNull(1) ?: return@forEach
                    val metadata = knownRemote["fonts/$uuid/metadata"] ?: return@forEach
                    knownRemote["fonts/$uuid/source"]?.let { applyRemoteFont(token, metadata, it) }
                }
            }
            rememberRemote(key, info)
        }
    }

    private suspend fun applyRemoteProgress(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        applyRemoteProgressJson(json)
    }

    private suspend fun applyRemoteProgressJson(json: JSONObject): Boolean {
        val bookUuid = json.getString("bookUuid")
        if (!books.bookExists(bookUuid)) return false
        val chapterKey = json.optString("chapterKey")
        val chapter = books.getChapterByKey(bookUuid, chapterKey)
            ?: books.getChapter(bookUuid, json.optInt("chapterIndex"))
            ?: return false
        val remoteTime = json.optLong("updatedTime")
        val local = books.getProgress(bookUuid)
        if (local != null && local.updatedTime > remoteTime) return false
        val remoteFraction = json.optDouble("progression").toFloat()
        // A newer timestamp can represent a reread on another device. Never move the visible
        // device backwards automatically; the current device's explicit movement is uploaded as
        // a new latest state instead.
        if (local != null && remoteFraction + PROGRESS_EPSILON < local.fraction) return false
        mutations.withoutRecording {
            bookRepository.saveProgress(
                ReadingProgress(
                    bookUuid = bookUuid,
                    chapterId = chapter.id,
                    position = json.optInt("paragraphIndex"),
                    offset = json.optInt("charOffset"),
                    updatedTime = remoteTime,
                    fraction = remoteFraction,
                    chapterKey = chapter.chapterKey,
                    paragraphIndex = json.optInt("paragraphIndex"),
                    charOffset = json.optInt("charOffset"),
                    quoteAnchor = json.optString("quoteAnchor"),
                ),
            )
        }
        return true
    }

    private suspend fun applyRemoteBookmarks(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        applyRemoteBookmarksJson(json)
    }

    private suspend fun applyRemoteBookmarksJson(json: JSONObject) {
        val bookUuid = json.getString("bookUuid")
        if (!books.bookExists(bookUuid)) return
        mutations.withoutRecording {
            database.withTransaction {
                books.deleteBookmarksForBook(bookUuid)
                val items = json.optJSONArray("items") ?: JSONArray()
                for (index in 0 until items.length()) {
                    val value = items.getJSONObject(index)
                    val chapter = books.getChapterByKey(bookUuid, value.optString("chapterKey"))
                        ?: books.getChapter(bookUuid, value.optInt("chapterIndex"))
                        ?: continue
                    books.insertBookmark(
                        BookmarkEntity(
                            uuid = value.getString("uuid"),
                            bookUuid = bookUuid,
                            chapterId = chapter.id,
                            position = value.optInt("paragraphIndex"),
                            preview = value.optString("preview"),
                            createdTime = value.optLong("createdTime"),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun applyRemoteSettings(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        applyRemoteSettingsJson(json)
    }

    private suspend fun applyRemoteSettingsJson(json: JSONObject) {
        val remote = jsonToSettings(json.getJSONObject("reader"))
        val goal = json.optInt("readingGoalMinutes", 30)
        mutations.withoutRecording {
            settingsRepository.update { remote }
            settingsRepository.setReadingGoalMinutes(goal)
        }
    }

    private suspend fun applyRemoteSession(token: String, info: DriveObject) = withJsonDownload(token, info) { json ->
        val uuid = json.getString("uuid")
        if (books.getSessionBySyncUuid(uuid) == null) {
            books.insertSession(
                ReadingSessionEntity(
                    bookUuid = json.getString("bookUuid"),
                    startedTime = json.optLong("startedTime"),
                    durationMillis = json.optLong("durationMillis"),
                    epochDay = json.optLong("epochDay"),
                    syncUuid = uuid,
                ),
            )
        }
    }

    private suspend fun applyRemoteFont(token: String, metadata: DriveObject, source: DriveObject) {
        val uuid = metadata.objectKey.split('/').getOrNull(1) ?: return
        if (fonts.getFont(uuid) != null || !preferences.current().syncFonts) return
        val metaFile = tempFile("font-meta")
        val sourceFile = File(context.filesDir, "fonts/$uuid.ttf")
        try {
            drive.download(token, metadata.id, metaFile)
            drive.download(token, source.id, sourceFile)
            val json = JSONObject(metaFile.readText())
            fonts.insert(UserFontEntity(uuid, json.optString("name", "云端字体"), sourceFile.absolutePath, json.optLong("createdTime")))
        } catch (error: Throwable) {
            sourceFile.delete()
            throw error
        } finally { metaFile.delete() }
    }

    private suspend fun settingsJson(): JSONObject = JSONObject()
        .put("schema", 1)
        .put("updatedAt", System.currentTimeMillis())
        .put("reader", settingsToJson(settingsRepository.settings.first()))
        .put("readingGoalMinutes", settingsRepository.readingGoalMinutes.first())

    private fun bookMetadataJson(book: BookEntity) = JSONObject()
        .put("schema", 1).put("uuid", book.uuid).put("title", book.title).put("author", book.author)
        .put("description", book.description).put("format", book.format).put("createdTime", book.createdTime)
        .put("contentHash", book.contentHash).put("category", book.category)

    private fun progressJson(progress: ReadingProgressEntity, chapterKey: String) = JSONObject()
        .put("schema", 1).put("bookUuid", progress.bookUuid).put("chapterKey", chapterKey)
        .put("paragraphIndex", progress.paragraphIndex).put("charOffset", progress.charOffset)
        .put("progression", progress.fraction).put("quoteAnchor", progress.quoteAnchor)
        .put("updatedTime", progress.updatedTime)

    private fun bookmarksJson(bookUuid: String, values: List<BookmarkRow>, chapters: Map<Long, ChapterEntity>): JSONObject = JSONObject()
        .put("schema", 1)
        .put("bookUuid", bookUuid)
        .put("updatedAt", System.currentTimeMillis())
        .put("items", JSONArray().apply { values.forEach { value ->
            put(JSONObject().put("uuid", value.uuid).put("chapterKey", chapters[value.chapterId]?.chapterKey.orEmpty())
                .put("chapterIndex", value.chapterIndex).put("paragraphIndex", value.position)
                .put("preview", value.preview).put("createdTime", value.createdTime))
        } })

    private fun sessionJson(value: ReadingSessionEntity) = JSONObject()
        .put("schema", 1).put("uuid", value.syncUuid).put("bookUuid", value.bookUuid)
        .put("startedTime", value.startedTime).put("durationMillis", value.durationMillis).put("epochDay", value.epochDay)

    private fun fontJson(value: UserFontEntity) = JSONObject()
        .put("schema", 1).put("uuid", value.uuid).put("name", value.name).put("createdTime", value.createdTime)

    private fun settingsToJson(value: ReaderSettings) = JSONObject()
        .put("fontSize", value.fontSize).put("lineHeight", value.lineHeight).put("letterSpacing", value.letterSpacing)
        .put("margin", value.margin).put("theme", value.theme.name).put("pageMode", value.pageMode.name)
        .put("customThemeEnabled", value.customThemeEnabled).put("customDayTheme", customThemeJson(value.customDayTheme))
        .put("customNightTheme", customThemeJson(value.customNightTheme)).put("fontUuid", value.fontUuid)
        .put("appColorTheme", value.appColorTheme.name).put("appUiStyle", value.appUiStyle.name)
        .put("showStatusBar", value.showStatusBar).put("hideNavigationBar", value.hideNavigationBar)
        .put("showPageNumber", value.showPageNumber)
        .put("volumeKeyPageTurn", value.volumeKeyPageTurn).put("keepScreenOn", value.keepScreenOn)
        .put("showChapterTitle", value.showChapterTitle)

    private fun jsonToSettings(value: JSONObject) = ReaderSettings(
        fontSize = value.optDouble("fontSize", 19.0).toFloat(),
        lineHeight = value.optDouble("lineHeight", 1.72).toFloat(),
        letterSpacing = value.optDouble("letterSpacing", .01).toFloat(),
        margin = value.optDouble("margin", 24.0).toFloat(),
        theme = enumValue(value, "theme", ReaderTheme.SYSTEM),
        pageMode = enumValue(value, "pageMode", PageMode.SCROLL),
        customThemeEnabled = value.optBoolean("customThemeEnabled"),
        customDayTheme = jsonToCustomTheme(value.optJSONObject("customDayTheme"), CustomReaderTheme()),
        customNightTheme = jsonToCustomTheme(value.optJSONObject("customNightTheme"), ReaderSettings().customNightTheme),
        fontUuid = value.optString("fontUuid").takeIf { it.isNotBlank() && it != "null" },
        appColorTheme = enumValue(value, "appColorTheme", AppColorTheme.DEFAULT),
        appUiStyle = enumValue(value, "appUiStyle", AppUiStyle.MATERIAL),
        showStatusBar = value.optBoolean("showStatusBar", true),
        hideNavigationBar = value.optBoolean("hideNavigationBar", true),
        showPageNumber = value.optBoolean("showPageNumber", true),
        volumeKeyPageTurn = value.optBoolean("volumeKeyPageTurn"),
        keepScreenOn = value.optBoolean("keepScreenOn", true),
        showChapterTitle = value.optBoolean("showChapterTitle", true),
    )

    private fun customThemeJson(value: CustomReaderTheme) = JSONObject()
        .put("background", value.backgroundHex).put("body", value.bodyHex).put("title", value.titleHex).put("accent", value.accentHex)

    private fun jsonToCustomTheme(value: JSONObject?, fallback: CustomReaderTheme) = value?.let {
        CustomReaderTheme(it.optString("background", fallback.backgroundHex), it.optString("body", fallback.bodyHex),
            it.optString("title", fallback.titleHex), it.optString("accent", fallback.accentHex))
    } ?: fallback

    private inline fun <reified T : Enum<T>> enumValue(json: JSONObject, key: String, fallback: T): T =
        runCatching { enumValueOf<T>(json.optString(key)) }.getOrDefault(fallback)

    private fun parseBook(json: JSONObject) = SyncedBook(
        uuid = json.getString("uuid"), title = json.optString("title", "未命名书籍"),
        author = json.optString("author", "未知作者"), description = json.optString("description"),
        format = enumValue(json, "format", BookFormat.TXT), createdTime = json.optLong("createdTime"),
        contentHash = json.getString("contentHash"), category = json.optString("category", "未分类"),
    )

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

    private fun keysForMutation(value: SyncOutboxEntity): List<String> = when (SyncEntityType.valueOf(value.entityType)) {
        SyncEntityType.BOOK -> listOf("books/${value.entityId}/metadata", "books/${value.entityId}/source")
        SyncEntityType.PROGRESS -> listOf("progress/${value.entityId}")
        SyncEntityType.BOOKMARKS -> listOf("bookmarks/${value.entityId}")
        SyncEntityType.SETTINGS -> listOf("settings/global")
        SyncEntityType.SESSION -> listOf("sessions/${value.entityId}")
        SyncEntityType.FONT -> listOf("fonts/${value.entityId}/metadata", "fonts/${value.entityId}/source")
    }

    private fun tempFile(prefix: String) = File(context.cacheDir, "cloud-sync/$prefix-${UUID.randomUUID()}")
        .also { it.parentFile?.mkdirs() }

    private fun File.sha256(): String = inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

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

    private data class BookMarkOwner(val bookUuid: String) {
        companion object { fun from(value: BookmarkEntity) = BookMarkOwner(value.bookUuid) }
    }

    private class AuthorizationRequiredException(message: String) : Exception(message)

    private companion object {
        const val PERMANENT_TOMBSTONE_EXPIRY = Long.MAX_VALUE
        const val PROGRESS_EPSILON = 0.000_001f
    }
}
