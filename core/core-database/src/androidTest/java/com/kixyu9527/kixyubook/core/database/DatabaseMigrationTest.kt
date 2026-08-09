package com.kixyu9527.kixyubook.core.database

import androidx.room.testing.MigrationTestHelper
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
    fun migrate6To7_preservesReadingDataAndCreatesSyncSchema() {
        helper.createDatabase(TEST_DATABASE, 6).apply {
            execSQL(
                """
                INSERT INTO books (
                    uuid, title, author, description, coverPath, format, originalPath,
                    storagePath, createdTime, contentHash, category
                ) VALUES ('book-1', '测试书籍', '作者', '', NULL, 'TXT', '/source', '/stored', 1, 'hash-1', '未分类')
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO chapters (id, bookUuid, title, chapterIndex, volumeTitle, volumeIndex, indexed) " +
                    "VALUES (1, 'book-1', '第一章', 0, NULL, NULL, 1)",
            )
            execSQL(
                "INSERT INTO reading_progress " +
                    "(bookUuid, chapterId, position, offset, updatedTime, fraction) " +
                    "VALUES ('book-1', 1, 12, 34, 100, 0.5)",
            )
            execSQL(
                "INSERT INTO reading_sessions " +
                    "(id, bookUuid, startedTime, durationMillis, epochDay) " +
                    "VALUES (7, 'book-1', 10, 20, 30)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            7,
            true,
            DatabaseMigrations.MIGRATION_6_7,
        ).use { database ->
            database.query(
                "SELECT position, offset, paragraphIndex, charOffset FROM reading_progress " +
                    "WHERE bookUuid = 'book-1'",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(12, cursor.getInt(0))
                assertEquals(34, cursor.getInt(1))
                assertEquals(12, cursor.getInt(2))
                assertEquals(34, cursor.getInt(3))
            }
            database.query("SELECT syncUuid FROM reading_sessions WHERE id = 7").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("legacy-7", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrate7To8_addsLocalBookOpenActivity() {
        helper.createDatabase(TEST_DATABASE, 7).apply {
            execSQL(
                """
                INSERT INTO books (
                    uuid, title, author, description, coverPath, format, originalPath,
                    storagePath, createdTime, contentHash, category
                ) VALUES ('book-1', '测试书籍', '作者', '', NULL, 'TXT', '/source', '/stored', 1, 'hash-1', '未分类')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            8,
            true,
            DatabaseMigrations.MIGRATION_7_8,
        ).use { database ->
            database.query("SELECT lastOpenedTime FROM books WHERE uuid = 'book-1'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
    }
}
