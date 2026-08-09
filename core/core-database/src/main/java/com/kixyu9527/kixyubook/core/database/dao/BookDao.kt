package com.kixyu9527.kixyubook.core.database.dao

import androidx.room.*
import com.kixyu9527.kixyubook.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY createdTime DESC") fun observeBooks(): Flow<List<BookEntity>>
    @Query("SELECT * FROM reading_progress") fun observeAllProgress(): Flow<List<ReadingProgressEntity>>
    @Query("SELECT * FROM reading_progress") suspend fun getAllProgress(): List<ReadingProgressEntity>
    @Query("SELECT * FROM reading_progress WHERE bookUuid = :uuid") fun observeProgress(uuid: String): Flow<ReadingProgressEntity?>
    @Query("SELECT * FROM reading_progress WHERE bookUuid = :uuid") suspend fun getProgress(uuid: String): ReadingProgressEntity?
    @Query("SELECT * FROM books WHERE uuid = :uuid") suspend fun getBook(uuid: String): BookEntity?
    @Query("SELECT * FROM books") suspend fun getAllBooks(): List<BookEntity>
    @Query("SELECT * FROM books WHERE uuid IN (:uuids)") suspend fun getBooks(uuids: Set<String>): List<BookEntity>
    @Query("SELECT uuid FROM books WHERE contentHash = :hash LIMIT 1") suspend fun findUuidByHash(hash: String): String?
    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE uuid = :uuid)") suspend fun bookExists(uuid: String): Boolean
    @Query("SELECT * FROM chapters WHERE bookUuid = :uuid ORDER BY chapterIndex") suspend fun getChapters(uuid: String): List<ChapterEntity>
    @Query("SELECT * FROM chapters WHERE bookUuid = :uuid ORDER BY chapterIndex") fun observeChapters(uuid: String): Flow<List<ChapterEntity>>
    @Query("SELECT * FROM chapters WHERE bookUuid = :uuid AND chapterIndex = :index LIMIT 1") suspend fun getChapter(uuid: String, index: Int): ChapterEntity?
    @Query("SELECT * FROM chapters WHERE bookUuid = :uuid AND chapterKey = :chapterKey LIMIT 1") suspend fun getChapterByKey(uuid: String, chapterKey: String): ChapterEntity?
    @Query("SELECT * FROM chapters WHERE bookUuid = :uuid AND indexed = 0 ORDER BY chapterIndex") suspend fun getUnindexedChapters(uuid: String): List<ChapterEntity>
    @Query("SELECT DISTINCT b.uuid FROM books b JOIN chapters c ON c.bookUuid = b.uuid WHERE b.format = 'EPUB' AND c.indexed = 0") suspend fun getBooksPendingEpubIndex(): List<String>
    @Query("SELECT * FROM paragraphs WHERE chapterId = :chapterId ORDER BY paragraphIndex") suspend fun getParagraphs(chapterId: Long): List<ParagraphEntity>
    @Query("SELECT * FROM paragraphs WHERE chapterId = :chapterId AND paragraphIndex = :index") suspend fun getParagraph(chapterId: Long, index: Int): ParagraphEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM metadata_edits WHERE bookUuid = :uuid)") suspend fun hasMetadataEdits(uuid: String): Boolean
    @Query("""SELECT b.uuid, b.bookUuid, b.chapterId, c.title AS chapterTitle, c.chapterIndex, b.position, b.preview, b.createdTime
        FROM bookmarks b JOIN chapters c ON c.id = b.chapterId
        WHERE b.bookUuid = :uuid ORDER BY c.chapterIndex, b.position""")
    fun observeBookmarks(uuid: String): Flow<List<BookmarkRow>>
    @Query("""SELECT b.uuid, b.bookUuid, b.chapterId, c.title AS chapterTitle, c.chapterIndex, b.position, b.preview, b.createdTime
        FROM bookmarks b JOIN chapters c ON c.id = b.chapterId
        WHERE b.bookUuid = :uuid ORDER BY c.chapterIndex, b.position""")
    suspend fun getBookmarks(uuid: String): List<BookmarkRow>
    @Query("SELECT * FROM bookmarks") suspend fun getAllBookmarkEntities(): List<BookmarkEntity>
    @Query("""SELECT c.id AS chapterId, c.title AS chapterTitle, c.chapterIndex,
        p.paragraphIndex, p.text FROM paragraphs p JOIN chapters c ON c.id = p.chapterId
        WHERE c.bookUuid = :uuid AND p.text LIKE '%' || :query || '%' ESCAPE '~'
        ORDER BY c.chapterIndex, p.paragraphIndex LIMIT 1000""")
    suspend fun searchBook(uuid: String, query: String): List<BookSearchResultRow>

    @Insert suspend fun insertBook(book: BookEntity)
    @Insert suspend fun insertChapter(chapter: ChapterEntity): Long
    @Insert suspend fun insertChapters(chapters: List<ChapterEntity>): List<Long>
    @Insert suspend fun insertParagraphs(paragraphs: List<ParagraphEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProgress(progress: ReadingProgressEntity)
    @Query("SELECT updatedTime FROM reading_progress WHERE bookUuid = :bookUuid")
    suspend fun getProgressUpdatedTime(bookUuid: String): Long?

    /** Serializes progress writes and prevents a delayed older coroutine from replacing the latest page. */
    @Transaction
    suspend fun saveProgressIfNewer(progress: ReadingProgressEntity): Boolean {
        val storedUpdatedTime = getProgressUpdatedTime(progress.bookUuid)
        if (storedUpdatedTime != null && storedUpdatedTime >= progress.updatedTime) return false
        saveProgress(progress)
        return true
    }

    @Insert suspend fun insertMetadataEdit(edit: MetadataEditEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSession(session: ReadingSessionEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("UPDATE books SET title = :title, author = :author, description = :description WHERE uuid = :uuid") suspend fun updateBookMetadata(uuid: String, title: String, author: String, description: String): Int
    @Query("UPDATE books SET category = :category WHERE uuid = :uuid") suspend fun setCategory(uuid: String, category: String)
    @Query("UPDATE books SET lastOpenedTime = MAX(lastOpenedTime, :openedAt) WHERE uuid = :uuid")
    suspend fun markBookOpened(uuid: String, openedAt: Long): Int
    @Query("UPDATE chapters SET title = :title WHERE id = :chapterId") suspend fun updateChapterTitle(chapterId: Long, title: String)
    @Query("UPDATE chapters SET title = :title, volumeTitle = :volumeTitle, volumeIndex = :volumeIndex WHERE id = :chapterId")
    suspend fun updateChapterOutline(chapterId: Long, title: String, volumeTitle: String?, volumeIndex: Int?)
    @Query("UPDATE chapters SET title = :title, indexed = 1 WHERE id = :chapterId") suspend fun markChapterIndexed(chapterId: Long, title: String)
    @Query("DELETE FROM paragraphs WHERE chapterId = :chapterId") suspend fun deleteParagraphs(chapterId: Long)
    @Query("DELETE FROM books WHERE uuid = :uuid") suspend fun deleteBook(uuid: String)
    @Query("DELETE FROM books WHERE uuid IN (:uuids)") suspend fun deleteBooks(uuids: Set<String>)
    @Query("DELETE FROM metadata_edits WHERE bookUuid IN (:uuids)") suspend fun deleteMetadataEdits(uuids: Set<String>)
    @Query("DELETE FROM reading_progress WHERE bookUuid = :uuid") suspend fun deleteProgress(uuid: String)
    @Query("DELETE FROM chapters WHERE bookUuid = :uuid") suspend fun deleteChapters(uuid: String)
    @Query("SELECT * FROM reading_sessions ORDER BY startedTime") fun observeSessions(): Flow<List<ReadingSessionEntity>>
    @Query("SELECT * FROM reading_sessions ORDER BY startedTime") suspend fun getAllSessions(): List<ReadingSessionEntity>
    @Query("SELECT * FROM reading_sessions WHERE syncUuid = :uuid LIMIT 1") suspend fun getSessionBySyncUuid(uuid: String): ReadingSessionEntity?
    @Query("SELECT MAX(createdTime) FROM metadata_edits WHERE bookUuid = :uuid") suspend fun lastMetadataEditTime(uuid: String): Long?
    @Query("DELETE FROM bookmarks WHERE uuid = :uuid") suspend fun deleteBookmark(uuid: String)
    @Query("DELETE FROM bookmarks WHERE bookUuid = :bookUuid") suspend fun deleteBookmarksForBook(bookUuid: String)

    @Transaction
    suspend fun insertParagraphsChunked(chapterId: Long, values: List<String>) {
        values.chunked(250).forEachIndexed { chunkIndex, chunk ->
            insertParagraphs(chunk.mapIndexed { index, text -> ParagraphEntity(chapterId = chapterId, paragraphIndex = chunkIndex * 250 + index, text = text) })
        }
    }

    @Transaction
    suspend fun replaceChapterIndex(chapterId: Long, title: String, values: List<String>) {
        deleteParagraphs(chapterId)
        insertParagraphsChunked(chapterId, values)
        markChapterIndexed(chapterId, title)
    }
}

@Dao
interface FontDao {
    @Query("SELECT * FROM user_fonts ORDER BY name") fun observeFonts(): Flow<List<UserFontEntity>>
    @Query("SELECT * FROM user_fonts") suspend fun getAllFonts(): List<UserFontEntity>
    @Query("SELECT * FROM user_fonts WHERE uuid = :uuid") suspend fun getFont(uuid: String): UserFontEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(font: UserFontEntity): Long
    @Query("DELETE FROM user_fonts WHERE uuid = :uuid") suspend fun delete(uuid: String)
}
