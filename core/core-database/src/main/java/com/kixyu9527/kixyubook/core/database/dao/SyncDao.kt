package com.kixyu9527.kixyubook.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kixyu9527.kixyubook.core.database.entity.SyncObjectStateEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncTombstoneEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncRemoteInboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_remote_inbox")
    suspend fun remoteInbox(): List<SyncRemoteInboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun retainRemoteChanges(values: List<SyncRemoteInboxEntity>)

    @Query("DELETE FROM sync_remote_inbox WHERE objectKey = :key")
    suspend fun consumeRemoteChange(key: String)

    @Query("DELETE FROM sync_remote_inbox WHERE driveFileId = :fileId")
    suspend fun removeRemoteFile(fileId: String)

    @Query("DELETE FROM sync_remote_inbox")
    suspend fun clearRemoteInbox()

    /** Room emits only committed outbox snapshots, never intermediate transaction writes. */
    @Query("SELECT * FROM sync_outbox ORDER BY changedAt, logicalCounter")
    fun observePendingMutations(): Flow<List<SyncOutboxEntity>>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_outbox ORDER BY changedAt, logicalCounter LIMIT :limit")
    suspend fun pending(limit: Int = 256): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox ORDER BY changedAt, logicalCounter")
    suspend fun allPending(): List<SyncOutboxEntity>

    /** Non-zero while the local device still owns un-pushed changes for one object. */
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE entityType = :type AND entityId = :entityId")
    suspend fun pendingCount(type: String, entityId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(value: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE uuid IN (:uuids)")
    suspend fun removeOutbox(uuids: List<String>)

    @Query("DELETE FROM sync_outbox WHERE entityType = :type AND entityId = :entityId")
    suspend fun removeOutbox(type: String, entityId: String)

    @Query("UPDATE sync_outbox SET attemptCount = attemptCount + 1, lastAttemptAt = :now WHERE uuid IN (:uuids)")
    suspend fun markAttempts(uuids: List<String>, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM sync_object_state")
    suspend fun allObjectStates(): List<SyncObjectStateEntity>

    @Query("SELECT * FROM sync_object_state WHERE objectKey = :key")
    suspend fun objectState(key: String): SyncObjectStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertObjectState(value: SyncObjectStateEntity)

    @Query("DELETE FROM sync_object_state WHERE objectKey = :key")
    suspend fun removeObjectState(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(value: SyncTombstoneEntity)

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()

    @Query("DELETE FROM sync_object_state")
    suspend fun clearObjectStates()

    @Query("SELECT * FROM sync_tombstones WHERE objectKey = :key LIMIT 1")
    suspend fun tombstone(key: String): SyncTombstoneEntity?

    @Query("DELETE FROM sync_tombstones WHERE objectKey = :key")
    suspend fun deleteTombstone(key: String)

    @Query("DELETE FROM sync_tombstones")
    suspend fun clearTombstones()
}
