package com.kixyu9527.kixyubook.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kixyu9527.kixyubook.core.database.entity.PendingRepairEntity

@Dao
interface RepairDao {
    /** Duplicate intents for the same target collapse into the existing row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(value: PendingRepairEntity): Long

    @Query("SELECT * FROM pending_repairs ORDER BY createdTime, id")
    suspend fun getAll(): List<PendingRepairEntity>

    @Query("DELETE FROM pending_repairs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM pending_repairs")
    suspend fun count(): Int
}
