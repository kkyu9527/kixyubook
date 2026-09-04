package com.kixyu9527.kixyubook.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kixyu9527.kixyubook.core.database.entity.ImportItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {
    @Query(
        """
        SELECT * FROM import_items
        WHERE runId = (SELECT runId FROM import_items ORDER BY startedTime DESC, rowid DESC LIMIT 1)
        ORDER BY itemOrder
        """,
    )
    fun observeLatestRun(): Flow<List<ImportItemEntity>>

    @Query("SELECT * FROM import_items ORDER BY startedTime DESC, itemOrder")
    fun observeHistory(): Flow<List<ImportItemEntity>>

    @Query("SELECT * FROM import_items WHERE runId = :runId ORDER BY itemOrder")
    suspend fun getRun(runId: String): List<ImportItemEntity>

    @Query("SELECT * FROM import_items WHERE runId = :runId AND sourceId = :sourceId LIMIT 1")
    suspend fun get(runId: String, sourceId: String): ImportItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<ImportItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ImportItemEntity)

    @Query("DELETE FROM import_items WHERE runId = :runId")
    suspend fun deleteRun(runId: String)

    @Query("DELETE FROM import_items")
    suspend fun deleteAll()

    @Query(
        """
        DELETE FROM import_items
        WHERE runId NOT IN (
            SELECT DISTINCT runId FROM import_items ORDER BY startedTime DESC LIMIT :keepRuns
        )
        """,
    )
    suspend fun pruneHistory(keepRuns: Int = 20)
}
