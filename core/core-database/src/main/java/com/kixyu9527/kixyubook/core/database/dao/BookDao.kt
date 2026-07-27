package com.kixyu9527.kixyubook.core.database.dao

import androidx.room.*
import com.kixyu9527.kixyubook.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY createdTime DESC") fun observeBooks(): Flow<List<BookEntity>>
    @Query("SELECT * FROM reading_progress") fun observeAllProgress(): Flow<List<ReadingProgressEntity>>
    @Query("SELECT * FROM reading_progress WHERE bookUuid = :uuid") fun observeProgress(uuid: String): Flow<ReadingProgressEntity?>
    @Query("SELECT * FROM reading_progress WHERE bookUuid = :uuid") suspend fun getProgress(uuid: String): ReadingProgressEntity?
    @Query("SELECT * FROM books WHERE uuid = :uuid") suspend fun getBook(uuid: String): BookEntity?
    @Query("SELECT * FROM books WHERE uuid IN (:uuids)") suspend fun getBooks(uuids: Set<String>): List<BookEntity>
    @Query("SELECT uuid FROM books WHERE contentHash = :hash LIMIT 1") suspend fun findUuidByHash(hash: String): String?
    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE uuid = :uuid)") suspend fun bookExists(uuid: String): Boolean
    @Query("SELECT * FROM chapters WHERE bookUuid = :uuid ORDER BY chapterIndex") suspend fun getChapters(uuid: String): List<ChapterEntity>
    @Query("SELECT * FROM chapters WHERE bookUuid = :uuid AND chapterIndex = :index LIMIT 1") suspend fun getChapter(uuid: String, index: Int): ChapterEntity?
    @Query("SELECT * FROM paragraphs WHERE chapterId = :chapterId ORDER BY paragraphIndex") suspend fun getParagraphs(chapterId: Long): List<ParagraphEntity>
    @Query("SELECT * FROM paragraphs WHERE chapterId = :chapterId AND paragraphIndex = :index") suspend fun getParagraph(chapterId: Long, index: Int): ParagraphEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM metadata_edits WHERE bookUuid = :uuid)") suspend fun hasMetadataEdits(uuid: String): Boolean

    @Insert suspend fun insertBook(book: BookEntity)
    @Insert suspend fun insertChapter(chapter: ChapterEntity): Long
    @Insert suspend fun insertParagraphs(paragraphs: List<ParagraphEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProgress(progress: ReadingProgressEntity)
    @Insert suspend fun insertMetadataEdit(edit: MetadataEditEntity)
    @Insert suspend fun insertSession(session: ReadingSessionEntity)

    @Query("UPDATE books SET title = :title, author = :author, description = :description WHERE uuid = :uuid AND format = 'TXT'") suspend fun updateTxtMetadata(uuid: String, title: String, author: String, description: String): Int
    @Query("UPDATE books SET contentHash = :contentHash WHERE uuid = :uuid") suspend fun updateContentHash(uuid: String, contentHash: String)
    @Query("UPDATE books SET category = :category WHERE uuid = :uuid") suspend fun setCategory(uuid: String, category: String)
    @Query("DELETE FROM books WHERE uuid = :uuid") suspend fun deleteBook(uuid: String)
    @Query("DELETE FROM books WHERE uuid IN (:uuids)") suspend fun deleteBooks(uuids: Set<String>)
    @Query("DELETE FROM reading_progress WHERE bookUuid = :uuid") suspend fun deleteProgress(uuid: String)
    @Query("DELETE FROM chapters WHERE bookUuid = :uuid") suspend fun deleteChapters(uuid: String)
    @Query("SELECT * FROM reading_sessions ORDER BY startedTime") fun observeSessions(): Flow<List<ReadingSessionEntity>>

    @Transaction
    suspend fun insertParagraphsChunked(chapterId: Long, values: List<String>) {
        values.chunked(250).forEachIndexed { chunkIndex, chunk ->
            insertParagraphs(chunk.mapIndexed { index, text -> ParagraphEntity(chapterId = chapterId, paragraphIndex = chunkIndex * 250 + index, text = text) })
        }
    }
}

@Dao
interface FontDao {
    @Query("SELECT * FROM user_fonts ORDER BY name") fun observeFonts(): Flow<List<UserFontEntity>>
    @Query("SELECT * FROM user_fonts WHERE uuid = :uuid") suspend fun getFont(uuid: String): UserFontEntity?
    @Insert suspend fun insert(font: UserFontEntity)
    @Query("DELETE FROM user_fonts WHERE uuid = :uuid") suspend fun delete(uuid: String)
}
