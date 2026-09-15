package com.kixyu9527.kixyubook.core.database.dao

import androidx.room.*
import com.kixyu9527.kixyubook.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    /** Keyset paging bounds decoded text during full-book search, including giant chapters. */
    @Query("SELECT * FROM paragraphs WHERE chapterId = :chapterId AND paragraphIndex > :afterIndex ORDER BY paragraphIndex LIMIT :limit")
    suspend fun getParagraphBatch(chapterId: Long, afterIndex: Int, limit: Int): List<ParagraphEntity>

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
    @Query("SELECT COUNT(*) FROM chapters WHERE bookUuid = :uuid") suspend fun getChapterCount(uuid: String): Int
    @Query("SELECT COUNT(*) FROM chapters WHERE bookUuid = :uuid AND indexed = 0") suspend fun getUnindexedChapterCount(uuid: String): Int
    @Query("SELECT * FROM chapters WHERE bookUuid = :uuid AND indexed = 0 ORDER BY chapterIndex LIMIT 1")
    suspend fun getNextUnindexedChapter(uuid: String): ChapterEntity?
    @Query("SELECT * FROM chapters WHERE bookUuid = :uuid AND indexed = 0 AND chapterIndex > :afterIndex ORDER BY chapterIndex LIMIT 1")
    suspend fun getNextUnindexedChapterAfter(uuid: String, afterIndex: Int): ChapterEntity?
    @Query("SELECT DISTINCT b.uuid FROM books b JOIN chapters c ON c.bookUuid = b.uuid WHERE b.format = 'EPUB' AND c.indexed = 0") suspend fun getBooksPendingEpubIndex(): List<String>
    @Query("SELECT * FROM paragraphs WHERE chapterId = :chapterId ORDER BY paragraphIndex") suspend fun getParagraphs(chapterId: Long): List<ParagraphEntity>
    @Query("SELECT * FROM paragraphs WHERE chapterId = :chapterId AND paragraphIndex = :index") suspend fun getParagraph(chapterId: Long, index: Int): ParagraphEntity?
    @Query("SELECT * FROM metadata_edits WHERE bookUuid = :uuid") suspend fun getMetadataEdits(uuid: String): List<MetadataEditEntity>
    // rowid, not createdTime, decides the survivors: several edits can share one millisecond and
    // createdTime alone would then keep an arbitrary subset.
    @Query("DELETE FROM metadata_edits WHERE bookUuid = :uuid AND uuid NOT IN (SELECT uuid FROM metadata_edits WHERE bookUuid = :uuid ORDER BY rowid DESC LIMIT :keep)")
    suspend fun pruneMetadataEdits(uuid: String, keep: Int)
    @Query("""SELECT b.uuid, b.bookUuid, b.chapterId, c.title AS chapterTitle, c.chapterIndex, b.position, b.preview, b.createdTime, b.chapterKey
        FROM bookmarks b JOIN chapters c ON c.id = b.chapterId
        WHERE b.bookUuid = :uuid ORDER BY c.chapterIndex, b.position""")
    fun observeBookmarks(uuid: String): Flow<List<BookmarkRow>>
    @Query("""SELECT b.uuid, b.bookUuid, b.chapterId, c.title AS chapterTitle, c.chapterIndex, b.position, b.preview, b.createdTime, b.chapterKey
        FROM bookmarks b JOIN chapters c ON c.id = b.chapterId
        WHERE b.bookUuid = :uuid ORDER BY c.chapterIndex, b.position""")
    suspend fun getBookmarks(uuid: String): List<BookmarkRow>
    @Query("SELECT * FROM bookmarks") suspend fun getAllBookmarkEntities(): List<BookmarkEntity>
    @Query("SELECT * FROM bookmarks WHERE uuid = :uuid LIMIT 1")
    suspend fun getBookmarkEntity(uuid: String): BookmarkEntity?
    @Query("SELECT chapterKey FROM chapters WHERE id = :chapterId") suspend fun getChapterKey(chapterId: Long): String?
    @Query("""SELECT c.id AS chapterId, c.title AS chapterTitle, c.chapterIndex,
        p.paragraphIndex, p.text
        FROM paragraphs_fts f
        JOIN paragraphs p ON p.id = f.rowid
        JOIN chapters c ON c.id = p.chapterId
        WHERE c.bookUuid = :uuid AND paragraphs_fts MATCH :matchQuery
        ORDER BY c.chapterIndex, p.paragraphIndex LIMIT 1000""")
    suspend fun searchBook(uuid: String, matchQuery: String): List<BookSearchResultRow>
    @Query("""SELECT c.id AS chapterId, c.title AS chapterTitle, c.chapterIndex,
        p.paragraphIndex, p.text
        FROM paragraphs p
        JOIN chapters c ON c.id = p.chapterId
        WHERE c.bookUuid = :uuid AND c.chapterIndex IN (:chapterIndexes)
            AND instr(lower(p.text), lower(:literalQuery)) > 0
        ORDER BY c.chapterIndex, p.paragraphIndex LIMIT :limit""")
    suspend fun searchBookLiteralChapters(
        uuid: String,
        literalQuery: String,
        chapterIndexes: List<Int>,
        limit: Int,
    ): List<BookSearchResultRow>

    @Insert suspend fun insertBook(book: BookEntity)
    @Insert suspend fun insertChapter(chapter: ChapterEntity): Long
    @Insert suspend fun insertChapters(chapters: List<ChapterEntity>): List<Long>
    @Insert suspend fun insertParagraphs(paragraphs: List<ParagraphEntity>): List<Long>
    @Insert suspend fun insertParagraphFts(paragraphs: List<ParagraphFtsEntity>)
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
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPendingBookmark(bookmark: PendingBookmarkEntity): Long
    @Query("SELECT * FROM pending_bookmarks") suspend fun getAllPendingBookmarks(): List<PendingBookmarkEntity>

    /**
     * One consistent read for sync snapshots. Reading chapters, located and pending bookmarks in
     * separate queries can miss a bookmark that is relocated between the two reads.
     */
    @Transaction
    suspend fun bookmarkSnapshot(uuid: String): BookmarkSnapshot =
        BookmarkSnapshot(getChapters(uuid), getBookmarks(uuid), getPendingBookmarks(uuid))
    @Query("SELECT * FROM pending_bookmarks WHERE bookUuid = :uuid") suspend fun getPendingBookmarks(uuid: String): List<PendingBookmarkEntity>
    @Query("SELECT * FROM pending_bookmarks WHERE uuid = :uuid LIMIT 1") suspend fun getPendingBookmark(uuid: String): PendingBookmarkEntity?
    @Query("DELETE FROM pending_bookmarks WHERE uuid = :uuid") suspend fun deletePendingBookmark(uuid: String)
    @Query("DELETE FROM pending_bookmarks WHERE bookUuid IN (:uuids)") suspend fun deletePendingBookmarks(uuids: Set<String>)

    @Query("UPDATE books SET title = :title, author = :author, description = :description WHERE uuid = :uuid") suspend fun updateBookMetadata(uuid: String, title: String, author: String, description: String): Int
    @Query(
        "UPDATE books SET titleSort = CASE WHEN :titleSort != '' THEN :titleSort ELSE titleSort END, " +
            "seriesName = CASE WHEN :seriesName != '' THEN :seriesName ELSE seriesName END, " +
            "seriesIndex = COALESCE(:seriesIndex, seriesIndex) WHERE uuid = :uuid",
    )
    suspend fun updateBookSortMetadata(uuid: String, titleSort: String, seriesName: String, seriesIndex: Double?)
    @Query("UPDATE books SET coverPath = :coverPath WHERE uuid = :uuid") suspend fun updateBookCover(uuid: String, coverPath: String?)
    @Query(
        "UPDATE books SET userEditedTitle = CASE WHEN :title THEN 1 ELSE userEditedTitle END, " +
            "userEditedAuthor = CASE WHEN :author THEN 1 ELSE userEditedAuthor END, " +
            "userEditedDescription = CASE WHEN :description THEN 1 ELSE userEditedDescription END " +
            "WHERE uuid = :uuid",
    )
    suspend fun markMetadataEdited(uuid: String, title: Boolean, author: Boolean, description: Boolean)
    @Query(
        "UPDATE books SET " +
            "originalDisplayName = CASE WHEN :originalDisplayName != '' THEN :originalDisplayName ELSE originalDisplayName END, " +
            "titleSort = CASE WHEN :titleSort != '' THEN :titleSort ELSE titleSort END, " +
            "seriesName = CASE WHEN :seriesName != '' THEN :seriesName ELSE seriesName END, " +
            "seriesIndex = COALESCE(:seriesIndex, seriesIndex) WHERE uuid = :uuid",
    )
    suspend fun updateBookImportedMetadata(uuid: String, originalDisplayName: String, titleSort: String, seriesName: String, seriesIndex: Double?)
    @Query("UPDATE books SET category = :category WHERE uuid = :uuid") suspend fun setCategory(uuid: String, category: String)
    @Query("UPDATE books SET category = :category WHERE uuid IN (:uuids)")
    suspend fun setCategories(uuids: Set<String>, category: String)
    @Query("UPDATE books SET lastOpenedTime = MAX(lastOpenedTime, :openedAt) WHERE uuid = :uuid")
    suspend fun markBookOpened(uuid: String, openedAt: Long): Int
    @Query("UPDATE chapters SET title = :title WHERE id = :chapterId") suspend fun updateChapterTitle(chapterId: Long, title: String)
    @Query("UPDATE chapters SET title = :title, volumeTitle = :volumeTitle, volumeIndex = :volumeIndex WHERE id = :chapterId")
    suspend fun updateChapterOutline(chapterId: Long, title: String, volumeTitle: String?, volumeIndex: Int?)
    @Query("UPDATE chapters SET title = :title, indexed = 1 WHERE id = :chapterId") suspend fun markChapterIndexed(chapterId: Long, title: String)
    @Query("DELETE FROM paragraphs WHERE chapterId = :chapterId") suspend fun deleteParagraphs(chapterId: Long)
    @Query("DELETE FROM paragraphs_fts WHERE rowid IN (SELECT id FROM paragraphs WHERE chapterId = :chapterId)")
    suspend fun deleteParagraphFts(chapterId: Long)
    @Query(
        """
        DELETE FROM paragraphs_fts WHERE rowid IN (
            SELECT p.id FROM paragraphs p JOIN chapters c ON c.id = p.chapterId
            WHERE c.bookUuid IN (:bookUuids)
        )
        """,
    )
    suspend fun deleteBookParagraphFts(bookUuids: Set<String>)
    @Query("INSERT INTO paragraphs_fts(rowid, text) SELECT p.id, p.text FROM paragraphs p JOIN chapters c ON c.id = p.chapterId WHERE c.bookUuid = :bookUuid")
    suspend fun populateBookParagraphFts(bookUuid: String)
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
            val entities = chunk.mapIndexed { index, text ->
                ParagraphEntity(chapterId = chapterId, paragraphIndex = chunkIndex * 250 + index, text = text)
            }
            val ids = insertParagraphs(entities)
            insertParagraphFts(ids.zip(entities) { id, entity -> ParagraphFtsEntity(id, entity.text) })
        }
    }

    @Transaction
    suspend fun replaceChapterIndex(chapterId: Long, title: String, values: List<String>) {
        deleteParagraphFts(chapterId)
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
