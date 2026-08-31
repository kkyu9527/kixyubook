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
    fun migrate8To12_keepsBooksAndCreatesCorrections() {
        helper.createDatabase(TEST_DATABASE, 8).use { database ->
            database.insertBook()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            KIXYU_DATABASE_VERSION,
            true,
            MIGRATION_8_9,
            MIGRATION_9_12,
        ).use { database ->
            assertEquals("迁移测试", database.bookTitle())
            database.insertCorrection()
            assertEquals(1, database.correctionCount())
        }
    }

    @Test
    fun migrate9To12_keepsBooksAndCorrections() {
        helper.createDatabase(TEST_DATABASE, 9).use { database ->
            database.insertBook()
            database.insertCorrection()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            KIXYU_DATABASE_VERSION,
            true,
            MIGRATION_9_12,
        ).use { database ->
            assertEquals("迁移测试", database.bookTitle())
            assertEquals(1, database.correctionCount())
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

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
