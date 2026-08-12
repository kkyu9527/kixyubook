package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.dao.FontDao
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.SyncObjectStateEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncTombstoneEntity
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.File

internal class CloudSyncPushPipeline(
    private val context: Context,
    private val books: BookDao,
    private val fonts: FontDao,
    private val syncDao: SyncDao,
    private val preferences: SyncPreferencesStore,
    private val drive: DriveAppDataClient,
    private val payloads: CloudSyncPayloadFactory,
) {
    suspend fun pushMutation(
        token: String,
        mutation: SyncOutboxEntity,
        knownRemote: Map<String, DriveObject>,
    ): List<Pair<String, DriveObject>> = try {
        val deferredLargePayload = shouldDeferLargePayload(mutation)
        val uploadedObjects = mutableListOf<Pair<String, DriveObject>>()
        payloads.materialize(mutation, includeLargePayload = !deferredLargePayload).forEach { local ->
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

    suspend fun pushDeletion(
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
            SyncEntityType.CORRECTION -> "corrections/${mutation.entityId}"
        }
        currentRemote.filterKeys { it == prefix || it.startsWith(prefix) }.forEach { (key, file) ->
            drive.delete(token, file.id)
            currentRemote.remove(key)
            syncDao.removeObjectState(key)
        }
        val now = System.currentTimeMillis()
        val expiresAt = PERMANENT_TOMBSTONE_EXPIRY
        val tombstoneKey = "tombstones/${mutation.entityType.lowercase()}/${mutation.entityId}"
        val tombstone = payloads.jsonObject(
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

    private companion object {
        const val PERMANENT_TOMBSTONE_EXPIRY = Long.MAX_VALUE
    }
}
