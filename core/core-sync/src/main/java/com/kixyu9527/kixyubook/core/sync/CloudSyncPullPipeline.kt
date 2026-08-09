package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.SyncObjectStateEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncTombstoneEntity
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal class CloudSyncPullPipeline(
    private val context: Context,
    private val books: BookDao,
    private val syncDao: SyncDao,
    private val bookRepository: BookRepository,
    private val fontRepository: FontRepository,
    private val mutations: RoomSyncMutationRecorder,
    private val drive: DriveAppDataClient,
    private val remoteState: CloudRemoteStateApplier,
    private val payloads: CloudSyncPayloadFactory,
) {
    suspend fun applyRemoteTombstones(token: String, remote: MutableMap<String, DriveObject>) {
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
                    val normalized = payloads.jsonObject(key, json)
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

    suspend fun applyRemoteChanges(
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
