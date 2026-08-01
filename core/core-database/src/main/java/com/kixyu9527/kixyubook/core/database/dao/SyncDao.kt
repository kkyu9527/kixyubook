package com.kixyu9527.kixyubook.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kixyu9527.kixyubook.core.database.entity.SyncObjectStateEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncTombstoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_outbox ORDER BY changedAt, logicalCounter LIMIT :limit")
    suspend fun pending(limit: Int = 256): List<SyncOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(value: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE uuid IN (:uuids)")
    suspend fun removeOutbox(uuids: List<String>)

    @Query("DELETE FROM sync_outbox WHERE entityType = :type AND entityId = :entityId")
    suspend fun removeOutbox(type: String, entityId: String)

    @Query("UPDATE sync_outbox SET attemptCount = attemptCount + 1 WHERE uuid IN (:uuids)")
    suspend fun markAttempts(uuids: List<String>)

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

    @Query("DELETE FROM sync_tombstones")
    suspend fun clearTombstones()
}
