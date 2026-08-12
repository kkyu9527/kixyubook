package com.kixyu9527.kixyubook.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kixyu9527.kixyubook.core.database.entity.TextCorrectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TextCorrectionDao {
    @Query("SELECT * FROM text_corrections WHERE bookUuid = :bookUuid ORDER BY updatedTime DESC")
    fun observeForBook(bookUuid: String): Flow<List<TextCorrectionEntity>>

    @Query("SELECT * FROM text_corrections WHERE bookUuid = :bookUuid ORDER BY chapterIndex, paragraphIndex, startOffset")
    suspend fun getForBook(bookUuid: String): List<TextCorrectionEntity>

    @Query("SELECT * FROM text_corrections WHERE uuid = :uuid LIMIT 1")
    suspend fun get(uuid: String): TextCorrectionEntity?

    @Query("SELECT * FROM text_corrections ORDER BY updatedTime")
    suspend fun getAll(): List<TextCorrectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: TextCorrectionEntity)

    @Query("DELETE FROM text_corrections WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}
