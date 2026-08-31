package com.kixyu9527.kixyubook.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kixyu9527.kixyubook.core.database.entity.ReaderAnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderAnnotationDao {
    @Query("SELECT * FROM reader_annotations WHERE bookUuid = :bookUuid ORDER BY chapterIndex, paragraphIndex, startOffset")
    fun observeForBook(bookUuid: String): Flow<List<ReaderAnnotationEntity>>

    @Query("SELECT * FROM reader_annotations WHERE bookUuid = :bookUuid ORDER BY chapterIndex, paragraphIndex, startOffset")
    suspend fun getForBook(bookUuid: String): List<ReaderAnnotationEntity>

    @Query("SELECT * FROM reader_annotations WHERE uuid = :uuid LIMIT 1")
    suspend fun get(uuid: String): ReaderAnnotationEntity?

    @Query("SELECT * FROM reader_annotations ORDER BY updatedTime")
    suspend fun getAll(): List<ReaderAnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: ReaderAnnotationEntity)

    @Query("DELETE FROM reader_annotations WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}
