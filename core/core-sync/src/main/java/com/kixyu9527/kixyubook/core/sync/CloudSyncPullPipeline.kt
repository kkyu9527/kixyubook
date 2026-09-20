package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.TextCorrectionRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderAnnotationRepository
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.runWithPostCommitFileCleanup
import com.kixyu9527.kixyubook.core.database.entity.SyncObjectStateEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncTombstoneEntity
import androidx.room.withTransaction
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal class CloudSyncPullPipeline(
    private val context: Context,
    private val database: KixyuDatabase,
    private val books: BookDao,
    private val syncDao: SyncDao,
    private val bookRepository: BookRepository,
    private val fontRepository: FontRepository,
    private val textCorrectionRepository: TextCorrectionRepository,
    private val readerAnnotationRepository: ReaderAnnotationRepository,
    private val mutations: RoomSyncMutationRecorder,
    private val drive: DriveAppDataClient,
    private val remoteState: CloudRemoteStateApplier,
) {
    suspend fun applyRemoteTombstones(token: String, remote: MutableMap<String, DriveObject>) {
        remote.filterKeys { it.startsWith("tombstones/") }.forEach { (key, objectInfo) ->
            val temp = tempFile("tombstone")
            try {
                drive.download(token, objectInfo.id, temp)
                val json = JSONObject(temp.readText())
                val type = runCatching { SyncEntityType.valueOf(json.getString("type")) }.getOrNull() ?: return@forEach
                val id = json.getString("entityId")
                val deletedAt = json.optLong("deletedAt")
                val tombstone = SyncTombstoneEntity(
                    objectKey = key,
                    deletedAt = deletedAt,
                    deviceId = json.optString("deviceId"),
                    expiresAt = PERMANENT_TOMBSTONE_EXPIRY,
                )
                // A tombstone older than a local re-upload of the same object is stale: the object
                // was restored on this device after the remote deletion and must not be removed.
                if (tombstoneSupersededByLocalUpload(syncDao, type, id, deletedAt)) {
                    syncDao.upsertTombstone(tombstone)
                    DiagnosticLog.record(
                        Category.SYNC,
                        "tombstone_superseded_by_local_upload",
                        outcome = "local_wins",
                        details = mapOf("entity" to type.name.lowercase()),
                    )
                    return@forEach
                }
                // A tombstone must not discard an edit this device has not uploaded yet. Keep the
                // local change (it will be pushed and re-create the object) instead of deleting it.
                val applied = applyRemoteTombstoneAtomically(
                    database = database,
                    syncDao = syncDao,
                    type = type,
                    id = id,
                    onConflict = { if (type == SyncEntityType.BOOK) reenqueueBookObjectsForSync(id) },
                    hasLocalConflict = {
                        hasRemoteTombstoneConflict(
                            type = type,
                            id = id,
                            syncDao = syncDao,
                            annotationUuidsForBook = { uuid ->
                                readerAnnotationRepository.getBookAnnotations(uuid).map { it.uuid }
                            },
                            correctionUuidsForBook = { uuid ->
                                textCorrectionRepository.getBookCorrections(uuid).map { it.uuid }
                            },
                        )
                    },
                    tombstone = tombstone,
                ) {
                    mutations.withoutRecording {
                        when (type) {
                            SyncEntityType.BOOK -> if (books.bookExists(id)) bookRepository.deleteBookRemote(id)
                            SyncEntityType.FONT -> fontRepository.deleteFontRemote(id)
                            SyncEntityType.CORRECTION -> textCorrectionRepository.deleteRemote(id)
                            SyncEntityType.ANNOTATION -> readerAnnotationRepository.deleteRemote(id)
                            else -> Unit
                        }
                    }
                }
                if (!applied) {
                    DiagnosticLog.record(
                        Category.SYNC,
                        "tombstone_skipped_local_pending",
                        outcome = "local_wins",
                        details = mapOf("entity" to type.name.lowercase()),
                    )
                }
            } finally {
                temp.delete()
            }
        }
    }

    suspend fun applyRemoteChanges(
        token: String,
        changedRemote: Map<String, DriveObject>,
        knownRemote: Map<String, DriveObject>,
        initialMergeComplete: Boolean,
        preferredBookUuid: String?,
        onProgress: suspend (CloudSyncProgress) -> Unit,
    ) {
        // Read the whole outbox, not a 256-row page: a truncated set would let a remote object
        // overwrite a local change that was not visible in the snapshot.
        val dirty = syncDao.allPending().flatMap(::keysForMutationOrEmpty).toSet()
        val localStates = syncDao.allObjectStates().associateBy { it.objectKey }
        val handledKeys = mutableSetOf<String>()
        val candidates = changedRemote.filter { (key, value) ->
            if (key.startsWith("tombstones/") || key in dirty) return@filter false
            if (!initialMergeComplete) return@filter true
            val state = localStates[key] ?: return@filter true
            isRemoteNewer(value, state.remoteModifiedAt, state.remoteVersion)
        }
        // Re-read the outbox right before applying. A local edit made while this pull is running
        // must win until it is pushed; the skipped remote snapshot is reconciled on the next run.
        suspend fun stillDirty(key: String): Boolean =
            hasPendingLocalChange(key, syncDao.allPending())

        // Configuration changes affect the presentation and behavior of everything restored
        // afterwards. Apply them before progress and book data during both initial and incremental
        // synchronization, not only during the first shelf rebuild.
        candidates["settings/global"]?.let { info ->
            if (!stillDirty("settings/global")) {
                remoteState.applySettings(token, info)
                rememberRemote("settings/global", info)
                handledKeys += "settings/global"
            }
        }

        // Existing-book progress is the latency-sensitive path. Apply it before metadata/source
        // restoration so entering a book never waits behind unrelated EPUB downloads.
        if (initialMergeComplete) {
            candidates.filterKeys { key ->
                key.startsWith("progress/") &&
                    key.substringAfter("progress/") == preferredBookUuid
            }.forEach { (key, info) ->
                if (stillDirty(key)) return@forEach
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
                canonicalSyncUuidOrNull(uuid) != null &&
                    "books/$uuid/metadata" !in dirty && "books/$uuid/source" !in dirty
            }
            .toList()

        var restoredBooks = 0
        if (changedBookUuids.isNotEmpty()) {
            onProgress(
                CloudSyncProgress(
                    title = if (initialMergeComplete) context.getString(R.string.sync_downloading_books) else context.getString(R.string.sync_restoring_library),
                    text = context.getString(R.string.sync_restored_count, 0, changedBookUuids.size),
                    completed = 0,
                    total = changedBookUuids.size,
                ),
            )
        }
        suspend fun applyKnownChildObjects(
            uuid: String,
            knownRemote: Map<String, DriveObject>,
            handledKeys: MutableSet<String>,
        ) {
            val progressKey = "progress/$uuid"
            val bookmarksKey = "bookmarks/$uuid"
            if (progressKey !in handledKeys) {
                knownRemote[progressKey]?.let { info ->
                    if (!stillDirty(progressKey) && remoteState.applyProgress(token, info)) {
                        rememberRemote(progressKey, info)
                        handledKeys += progressKey
                    }
                }
            }
            if (bookmarksKey !in handledKeys) {
                knownRemote[bookmarksKey]?.let { info ->
                    if (!stillDirty(bookmarksKey) && remoteState.applyBookmarks(token, info)) {
                        rememberRemote(bookmarksKey, info)
                        handledKeys += bookmarksKey
                    }
                }
            }
        }

        suspend fun restoreBook(uuid: String): Boolean {
            val restored = remoteState.restoreBook(token, uuid, knownRemote)
            if (!restored) {
                // A metadata-only book (source not uploaded yet) must not consume its keys, and its
                // progress/bookmarks stay unconsumed so a later run can still apply them.
                DiagnosticLog.record(
                    Category.SYNC,
                    "book_restore_incomplete",
                    outcome = "retry",
                    details = mapOf("book" to uuid.take(8)),
                )
                return false
            }
            handledKeys += "books/$uuid/metadata"
            handledKeys += "books/$uuid/source"
            restoredBooks++
            onProgress(
                CloudSyncProgress(
                    title = if (initialMergeComplete) context.getString(R.string.sync_downloading_books) else context.getString(R.string.sync_restoring_library),
                    text = context.getString(R.string.sync_restored_count, restoredBooks, changedBookUuids.size),
                    completed = restoredBooks,
                    total = changedBookUuids.size,
                ),
            )
            // The book may land in a run where its child objects are no longer part of the change
            // stream; apply them now from the known remote snapshot.
            applyKnownChildObjects(uuid, knownRemote, handledKeys)
            return true
        }

        suspend fun bookIsDirty(uuid: String): Boolean =
            stillDirty("books/$uuid/metadata") || stillDirty("books/$uuid/source")

        if (initialMergeComplete) {
            changedBookUuids.forEach { if (!bookIsDirty(it)) restoreBook(it) }
            // A book restored without its child objects in this page still needs them; the helper
            // already ran per restore, so nothing else is required here.
        } else {
            val restorePlan = planInitialRestore(changedBookUuids, knownRemote)
            restorePlan.priorityBookUuids.forEach { uuid ->
                if (!bookIsDirty(uuid)) restoreBook(uuid)
                candidates["progress/$uuid"]?.let { info ->
                    if (stillDirty("progress/$uuid")) return@let
                    if (remoteState.applyProgress(token, info)) {
                        rememberRemote("progress/$uuid", info)
                        handledKeys += "progress/$uuid"
                    }
                }
                candidates["bookmarks/$uuid"]?.let { info ->
                    if (stillDirty("bookmarks/$uuid")) return@let
                    if (remoteState.applyBookmarks(token, info)) {
                        rememberRemote("bookmarks/$uuid", info)
                        handledKeys += "bookmarks/$uuid"
                    }
                }
            }

            // Put every other source-backed book on the shelf before restoring its secondary data.
            restorePlan.remainingBookUuids.forEach { if (!bookIsDirty(it)) restoreBook(it) }
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
                when (remoteState.applyFont(token, metadata, source)) {
                    CloudFontApplyResult.IMPORTED,
                    CloudFontApplyResult.ALREADY_PRESENT,
                    -> {
                        rememberRemote(metadataKey, metadata)
                        rememberRemote(sourceKey, source)
                        handledKeys += metadataKey
                        handledKeys += sourceKey
                    }
                    // Skipped or invalid objects keep their old baseline so a later run (or the
                    // font-sync enable reconciliation) can still apply them.
                    else -> Unit
                }
            }

        candidates.forEach { (key, info) ->
            if (key in handledKeys || stillDirty(key)) return@forEach
            val applied = when {
                key.startsWith("progress/") -> remoteState.applyProgress(token, info)
                key.startsWith("bookmarks/") -> remoteState.applyBookmarks(token, info)
                key == "settings/global" -> {
                    remoteState.applySettings(token, info)
                    true
                }
                key.startsWith("sessions/") -> remoteState.applySession(token, info)
                key.startsWith("corrections/") -> remoteState.applyCorrection(token, info)
                key.startsWith("annotations/") -> remoteState.applyAnnotation(token, info)
                else -> true
            }
            // A failed apply (for example a book that is not restored yet) must not advance the
            // baseline, otherwise the change stream never offers the object again.
            if (applied) rememberRemote(key, info)
        }
    }

    /**
     * Local wins: the cloud copy was deleted while this device still had unsynced changes, so the
     * whole logical book is queued again and the next push re-creates every remote object. Without
     * this the pending rows would eventually be uploaded, cleared and then deleted by a re-read
     * tombstone, losing the local work silently.
     */
    private suspend fun reenqueueBookObjectsForSync(bookUuid: String) {
        suspend fun recordIfIdle(type: SyncEntityType, id: String) {
            if (syncDao.pendingCount(type.name, id) == 0) mutations.record(type, id)
        }
        recordIfIdle(SyncEntityType.BOOK, bookUuid)
        recordIfIdle(SyncEntityType.BOOKMARKS, bookUuid)
        recordIfIdle(SyncEntityType.PROGRESS, bookUuid)
        readerAnnotationRepository.getBookAnnotations(bookUuid).forEach { recordIfIdle(SyncEntityType.ANNOTATION, it.uuid) }
        textCorrectionRepository.getBookCorrections(bookUuid).forEach { recordIfIdle(SyncEntityType.CORRECTION, it.uuid) }
    }

    /** Imports every known remote font pair; used when font sync is enabled after being off. */
    suspend fun applyRemoteFonts(token: String, knownRemote: Map<String, DriveObject>) {
        knownRemote.keys.asSequence()
            .filter { it.startsWith("fonts/") }
            .mapNotNull { it.split('/').getOrNull(1) }
            .distinct()
            .forEach { uuid ->
                val metadataKey = "fonts/$uuid/metadata"
                val sourceKey = "fonts/$uuid/source"
                val metadata = knownRemote[metadataKey] ?: return@forEach
                val source = knownRemote[sourceKey] ?: return@forEach
                when (remoteState.applyFont(token, metadata, source)) {
                    CloudFontApplyResult.IMPORTED,
                    CloudFontApplyResult.ALREADY_PRESENT,
                    -> {
                        rememberRemote(metadataKey, metadata)
                        rememberRemote(sourceKey, source)
                    }
                    else -> Unit
                }
            }
    }

    private suspend fun rememberRemote(key: String, value: DriveObject) {
        val previous = syncDao.objectState(key)
        syncDao.upsertObjectState(
            SyncObjectStateEntity(
                key,
                value.id,
                previous?.localHash,
                previous?.localChangedAt ?: 0,
                value.modifiedAt,
                value.version,
            ),
        )
    }

    private fun tempFile(prefix: String) = File(
        context.cacheDir,
        "cloud-sync/$prefix-${UUID.randomUUID()}",
    ).also { it.parentFile?.mkdirs() }

    private companion object {
        const val PERMANENT_TOMBSTONE_EXPIRY = Long.MAX_VALUE
    }
}

/**
 * The pending check, the local deletion and the outbox cleanup share one transaction, so a local
 * edit racing the tombstone is serialized: it either arrives before the check (and the tombstone
 * is skipped) or after the commit (and its outbox row survives to be pushed again). File cleanups
 * the repositories defer only run once the transaction has committed.
 */
internal suspend fun applyRemoteTombstoneAtomically(
    database: KixyuDatabase,
    syncDao: SyncDao,
    type: SyncEntityType,
    id: String,
    tombstone: SyncTombstoneEntity,
    hasLocalConflict: suspend () -> Boolean = {
        !shouldApplyRemoteTombstone(syncDao.pendingCount(type.name, id))
    },
    onConflict: suspend () -> Unit = {},
    deleteLocal: suspend () -> Unit,
): Boolean = runWithPostCommitFileCleanup {
    database.withTransaction {
        val shouldDelete = !hasLocalConflict()
        if (shouldDelete) {
            deleteLocal()
            syncDao.removeOutbox(type.name, id)
        } else {
            // The conflict resolution must run in the same transaction as the skip, otherwise a
            // re-read tombstone could still delete the data after the edit was pushed and cleared.
            onConflict()
        }
        syncDao.upsertTombstone(tombstone)
        shouldDelete
    }
}

/** Drive object key that carries the entity a tombstone refers to. */
internal fun mutableTombstoneObjectKey(type: SyncEntityType, id: String): String? = when (type) {
    SyncEntityType.BOOK -> "books/$id/metadata"
    SyncEntityType.FONT -> "fonts/$id/metadata"
    SyncEntityType.BOOKMARKS -> "bookmarks/$id"
    SyncEntityType.PROGRESS -> "progress/$id"
    SyncEntityType.SETTINGS -> "settings/global"
    SyncEntityType.SESSION -> "sessions/$id"
    SyncEntityType.CORRECTION -> "corrections/$id"
    SyncEntityType.ANNOTATION -> "annotations/$id"
}

/** True when the local device re-uploaded the object after the remote deletion, making it stale. */
internal suspend fun tombstoneSupersededByLocalUpload(
    syncDao: SyncDao,
    type: SyncEntityType,
    id: String,
    deletedAt: Long,
): Boolean {
    if (deletedAt <= 0) return false
    val key = mutableTombstoneObjectKey(type, id) ?: return false
    val state = syncDao.objectState(key) ?: return false
    return state.remoteModifiedAt > deletedAt
}

/**
 * A book tombstone cascades into its bookmarks, progress, notes and corrections, so any pending
 * local change on those children must block the deletion exactly like a pending change on the book
 * itself: the child would otherwise be deleted and its own outbox row cleaned up with it.
 */
internal suspend fun hasRemoteTombstoneConflict(
    type: SyncEntityType,
    id: String,
    syncDao: SyncDao,
    annotationUuidsForBook: suspend (String) -> List<String>,
    correctionUuidsForBook: suspend (String) -> List<String>,
): Boolean {
    if (!shouldApplyRemoteTombstone(syncDao.pendingCount(type.name, id))) return true
    if (type != SyncEntityType.BOOK) return false
    if (!shouldApplyRemoteTombstone(syncDao.pendingCount(SyncEntityType.BOOKMARKS.name, id))) return true
    if (!shouldApplyRemoteTombstone(syncDao.pendingCount(SyncEntityType.PROGRESS.name, id))) return true
    if (annotationUuidsForBook(id).any { syncDao.pendingCount(SyncEntityType.ANNOTATION.name, it) > 0 }) {
        return true
    }
    if (correctionUuidsForBook(id).any { syncDao.pendingCount(SyncEntityType.CORRECTION.name, it) > 0 }) {
        return true
    }
    return false
}
