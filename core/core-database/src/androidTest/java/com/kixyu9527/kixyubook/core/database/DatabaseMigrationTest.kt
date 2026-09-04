package com.kixyu9527.kixyubook.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KixyuDatabase::class.java,
    )

    @Test
    fun migrate6To14_keepsPublishedLibraryAndReadingState() {
        helper.createDatabase(TEST_DATABASE, 6).use { database ->
            database.insertLegacyBook()
            database.insertLegacyChapterAndReadingState()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            KIXYU_DATABASE_VERSION,
            true,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_12,
            MIGRATION_12_14,
        ).use { database ->
            assertPublishedLibraryStateWasPreserved(database)
            assertEquals(0L, database.bookLastOpenedTime())
            assertEquals("", database.chapterKey())
            assertEquals(4, database.progressParagraphIndex())
            assertEquals(12, database.progressCharOffset())
        }
    }

    @Test
    fun migrate7To14_keepsPublishedLibraryAndReadingState() {
        helper.createDatabase(TEST_DATABASE, 7).use { database ->
            database.insertLegacyBook()
            database.insertVersion7ChapterAndReadingState()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            KIXYU_DATABASE_VERSION,
            true,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_12,
            MIGRATION_12_14,
        ).use { database ->
            assertPublishedLibraryStateWasPreserved(database)
            assertEquals(0L, database.bookLastOpenedTime())
            assertEquals("chapter-0", database.chapterKey())
            assertEquals(4, database.progressParagraphIndex())
            assertEquals(12, database.progressCharOffset())
        }
    }

    @Test
    fun migrate8To14_keepsBooksAndCreatesUserTextTables() {
        helper.createDatabase(TEST_DATABASE, 8).use { database ->
            database.insertBook()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            KIXYU_DATABASE_VERSION,
            true,
            MIGRATION_8_9,
            MIGRATION_9_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
        ).use { database ->
            assertEquals("迁移测试", database.bookTitle())
            database.insertCorrection()
            assertEquals(1, database.correctionCount())
            database.insertAnnotation()
            assertEquals(1, database.annotationCount())
        }
    }

    @Test
    fun migrate9To14_keepsBooksAndCorrections() {
        helper.createDatabase(TEST_DATABASE, 9).use { database ->
            database.insertBook()
            database.insertCorrection()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            KIXYU_DATABASE_VERSION,
            true,
            MIGRATION_9_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
        ).use { database ->
            assertEquals("迁移测试", database.bookTitle())
            assertEquals(1, database.correctionCount())
            assertEquals(0, database.annotationCount())
        }
    }

    @Test
    fun migrate12To14_keepsBooksAndCreatesAnnotations() {
        helper.createDatabase(TEST_DATABASE, 12).use { database ->
            database.insertBook()
            database.insertVersion12ChapterAndReadingState()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            KIXYU_DATABASE_VERSION,
            true,
            MIGRATION_12_14,
        ).use { database ->
            assertEquals("迁移测试", database.bookTitle())
            assertEquals("第一章", database.chapterTitle())
            assertEquals(1, database.bookmarkCount())
            assertEquals(1, database.searchResultCount())
            assertEquals(4, database.progressParagraphIndex())
            assertEquals(12, database.progressCharOffset())
            database.insertAnnotation()
            assertEquals(1, database.annotationCount())
        }
    }

    @Test
    fun migrate13To14_buildsFullTextIndexAndImportTaskTable() {
        helper.createDatabase(TEST_DATABASE, 13).use { database ->
            database.insertBook()
            database.execSQL(
                "INSERT INTO chapters(id, bookUuid, title, chapterIndex, volumeTitle, volumeIndex, indexed, chapterKey) " +
                    "VALUES(7, 'migration-book', '第一章', 0, NULL, NULL, 1, 'chapter-0')",
            )
            database.execSQL(
                "INSERT INTO paragraphs(id, chapterId, paragraphIndex, text) VALUES(9, 7, 0, '可搜索正文')",
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            KIXYU_DATABASE_VERSION,
            true,
            MIGRATION_13_14,
        ).use { database ->
            assertEquals(
                1,
                database.query("SELECT COUNT(*) FROM paragraphs_fts WHERE paragraphs_fts MATCH '可搜索正文'")
                    .use { cursor -> check(cursor.moveToFirst()); cursor.getInt(0) },
            )
            database.execSQL(
                """
                INSERT INTO import_items(
                    runId, sourceId, displayName, itemOrder, stage, progress, status,
                    bookUuid, message, startedTime, updatedTime
                ) VALUES('run', 'source', '测试.txt', 0, 'QUEUED', 0, 'PENDING', NULL, NULL, 1, 1)
                """.trimIndent(),
            )
        }
    }

    private fun SupportSQLiteDatabase.insertBook() {
        execSQL(
            """
            INSERT INTO books(
                uuid, title, author, description, coverPath, format, originalPath,
                storagePath, createdTime, contentHash, category, lastOpenedTime
            ) VALUES(
                'migration-book', '迁移测试', '作者', '', NULL, 'EPUB', '/source.epub',
                '/stored.epub', 1, 'migration-hash', '默认', 2
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertLegacyBook() {
        execSQL(
            """
            INSERT INTO books(
                uuid, title, author, description, coverPath, format, originalPath,
                storagePath, createdTime, contentHash, category
            ) VALUES(
                'migration-book', '旧版书架', '作者', '', NULL, 'EPUB', '/source.epub',
                '/stored.epub', 1, 'migration-hash', '默认'
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertLegacyChapterAndReadingState() {
        execSQL(
            """
            INSERT INTO chapters(id, bookUuid, title, chapterIndex, volumeTitle, volumeIndex, indexed)
            VALUES(7, 'migration-book', '第一章', 0, '第一卷', 0, 1)
            """.trimIndent(),
        )
        insertLegacyParagraph()
        insertLegacyProgressAndBookmark()
    }

    private fun SupportSQLiteDatabase.insertVersion7ChapterAndReadingState() {
        execSQL(
            """
            INSERT INTO chapters(
                id, bookUuid, title, chapterIndex, volumeTitle, volumeIndex, indexed, chapterKey
            ) VALUES(7, 'migration-book', '第一章', 0, '第一卷', 0, 1, 'chapter-0')
            """.trimIndent(),
        )
        insertLegacyParagraph()
        execSQL(
            """
            INSERT INTO reading_progress(
                bookUuid, chapterId, position, offset, updatedTime, fraction,
                chapterKey, paragraphIndex, charOffset, quoteAnchor
            ) VALUES('migration-book', 7, 4, 12, 2, 0.5, 'chapter-0', 4, 12, '锚点')
            """.trimIndent(),
        )
        insertBookmark()
    }

    private fun SupportSQLiteDatabase.insertVersion12ChapterAndReadingState() {
        insertVersion7ChapterAndReadingState()
    }

    private fun SupportSQLiteDatabase.insertLegacyParagraph() {
        execSQL(
            "INSERT INTO paragraphs(id, chapterId, paragraphIndex, text) " +
                "VALUES(9, 7, 0, '旧版正文仍然可以搜索')",
        )
    }

    private fun SupportSQLiteDatabase.insertLegacyProgressAndBookmark() {
        execSQL(
            """
            INSERT INTO reading_progress(bookUuid, chapterId, position, offset, updatedTime, fraction)
            VALUES('migration-book', 7, 4, 12, 2, 0.5)
            """.trimIndent(),
        )
        insertBookmark()
    }

    private fun SupportSQLiteDatabase.insertBookmark() {
        execSQL(
            """
            INSERT INTO bookmarks(uuid, bookUuid, chapterId, position, preview, createdTime)
            VALUES('bookmark', 'migration-book', 7, 4, '旧版书签', 3)
            """.trimIndent(),
        )
    }

    private fun assertPublishedLibraryStateWasPreserved(database: SupportSQLiteDatabase) {
        assertEquals("旧版书架", database.bookTitle())
        assertEquals("第一章", database.chapterTitle())
        assertEquals(1, database.bookmarkCount())
        assertEquals(1, database.searchResultCount())
    }

    private fun SupportSQLiteDatabase.insertCorrection() {
        execSQL(
            """
            INSERT INTO text_corrections(
                uuid, bookUuid, sourceContentHash, chapterKey, chapterIndex,
                paragraphIndex, startOffset, endOffset, exactText, prefixText,
                suffixText, replacementText, status, createdTime, updatedTime, deviceId
            ) VALUES(
                'correction', 'migration-book', 'migration-hash', 'chapter-0', 0,
                0, 0, 1, '原', '', '', '新', 'ACTIVE', 1, 2, 'test-device'
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertAnnotation() {
        execSQL(
            """
            INSERT INTO reader_annotations(
                uuid, bookUuid, sourceContentHash, chapterKey, chapterIndex,
                paragraphIndex, startOffset, endOffset, exactText, style, note,
                createdTime, updatedTime, deviceId
            ) VALUES(
                'annotation', 'migration-book', 'migration-hash', 'chapter-0', 0,
                0, 0, 1, '原', 'HIGHLIGHT', '笔记', 1, 2, 'test-device'
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.bookTitle(): String =
        query("SELECT title FROM books WHERE uuid = 'migration-book'").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun SupportSQLiteDatabase.correctionCount(): Int =
        query("SELECT COUNT(*) FROM text_corrections").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.annotationCount(): Int =
        query("SELECT COUNT(*) FROM reader_annotations").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.bookLastOpenedTime(): Long =
        query("SELECT lastOpenedTime FROM books WHERE uuid = 'migration-book'").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun SupportSQLiteDatabase.chapterTitle(): String =
        query("SELECT title FROM chapters WHERE id = 7").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun SupportSQLiteDatabase.chapterKey(): String =
        query("SELECT chapterKey FROM chapters WHERE id = 7").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun SupportSQLiteDatabase.progressParagraphIndex(): Int =
        query("SELECT paragraphIndex FROM reading_progress WHERE bookUuid = 'migration-book'").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.progressCharOffset(): Int =
        query("SELECT charOffset FROM reading_progress WHERE bookUuid = 'migration-book'").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.bookmarkCount(): Int =
        query("SELECT COUNT(*) FROM bookmarks WHERE bookUuid = 'migration-book'").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.searchResultCount(): Int =
        query(
            """
            SELECT COUNT(*) FROM paragraphs_fts f
            JOIN paragraphs p ON p.id = f.rowid
            JOIN chapters c ON c.id = p.chapterId
            WHERE c.bookUuid = 'migration-book'
            """.trimIndent(),
        ).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
