package com.kixyu9527.kixyubook.core.sync

import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.SyncRemoteInboxEntity

/** Cursor advancement acknowledges receipt, not application. This queue bridges the two. */
internal class CloudRemoteInbox(private val database: KixyuDatabase, private val dao: SyncDao) {
    suspend fun receive(changes: Map<String, DriveObject>): Map<String, DriveObject> = database.withTransaction {
        dao.retainRemoteChanges(changes.values.map { info ->
            val previous = dao.receivedObject(info.objectKey)
            if (previous != null && previous.matches(info)) previous else info.toInboxEntity()
        })
        dao.remoteInbox().associate { it.objectKey to it.toDriveObject() }
    }

    suspend fun pending(): Map<String, DriveObject> = dao.remoteInbox().associate { it.objectKey to it.toDriveObject() }
}

internal fun SyncRemoteInboxEntity.matches(info: DriveObject): Boolean =
    driveFileId == info.id && version == info.version && modifiedAt == info.modifiedAt

private fun DriveObject.toInboxEntity() = SyncRemoteInboxEntity(objectKey, id, name, mimeType, modifiedAt, version, size, md5)
private fun SyncRemoteInboxEntity.toDriveObject() = DriveObject(driveFileId, name, objectKey, mimeType, modifiedAt, version, size, md5)

/** Shared orchestration boundary: tests can delay book restoration without mocking the network. */
internal interface CloudRemoteObjectApplier {
    suspend fun restoreBook(token: String, uuid: String, knownRemote: Map<String, DriveObject>): Boolean
    suspend fun applyProgress(token: String, info: DriveObject): Boolean
    suspend fun applyBookmarks(token: String, info: DriveObject): Boolean
    suspend fun applySettings(token: String, info: DriveObject): Boolean
    suspend fun applySession(token: String, info: DriveObject): Boolean
    suspend fun applyCorrection(token: String, info: DriveObject): Boolean
    suspend fun applyAnnotation(token: String, info: DriveObject): Boolean
    suspend fun applyFont(token: String, metadata: DriveObject, source: DriveObject): CloudFontApplyResult
}
