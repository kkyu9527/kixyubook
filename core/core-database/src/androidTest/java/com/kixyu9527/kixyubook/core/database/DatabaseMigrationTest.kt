package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migration1To3PreservesContentAndRemovesLegacyPatches() {
        createV1Database().use { helper ->
            helper.writableDatabase.apply {
                execSQL("INSERT INTO books VALUES(1, '旧书名', '旧作者', NULL, ?, 'TXT', 1234)", arrayOf(context.filesDir.resolve("legacy.txt").absolutePath))
                execSQL("INSERT INTO chapters VALUES(10, 1, '第一卷 · 第一章', 0)")
                execSQL("INSERT INTO paragraphs VALUES(20, 10, 0, '旧版正文')")
                execSQL("INSERT INTO reading_progress VALUES(1, 10, 0, 5678, 0.5)")
            }
        }

        val database = Room.databaseBuilder(context, KixyuDatabase::class.java, TEST_DATABASE)
            .addMigrations(migration1To2(context), migration2To3)
            .allowMainThreadQueries()
            .build()
        val sqlite = database.openHelper.writableDatabase

        sqlite.query("SELECT title, author, format FROM books").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("旧书名", cursor.getString(0))
            assertEquals("旧作者", cursor.getString(1))
            assertEquals("TXT", cursor.getString(2))
        }
        sqlite.query("SELECT title FROM chapters WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("第一章", cursor.getString(0))
        }
        sqlite.query("SELECT text FROM paragraphs WHERE id = 20").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("旧版正文", cursor.getString(0))
        }
        sqlite.query("PRAGMA foreign_key_list(`chapters`)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("books", cursor.getString(cursor.getColumnIndexOrThrow("table")))
        }
        sqlite.query("PRAGMA foreign_key_list(`paragraphs`)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("chapters", cursor.getString(cursor.getColumnIndexOrThrow("table")))
        }
        sqlite.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'text_edit_patches'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    private fun createV1Database(): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `coverPath` TEXT, `filePath` TEXT NOT NULL, `format` TEXT NOT NULL, `addedTime` INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_filePath` ON `books` (`filePath`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `title` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_bookId` ON `chapters` (`bookId`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapters_bookId_chapterIndex` ON `chapters` (`bookId`, `chapterIndex`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `paragraphs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chapterId` INTEGER NOT NULL, `paragraphIndex` INTEGER NOT NULL, `text` TEXT NOT NULL, FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_paragraphs_chapterId` ON `paragraphs` (`chapterId`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_paragraphs_chapterId_paragraphIndex` ON `paragraphs` (`chapterId`, `paragraphIndex`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `reading_progress` (`bookId` INTEGER NOT NULL, `chapterId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `updatedTime` INTEGER NOT NULL, `fraction` REAL NOT NULL, PRIMARY KEY(`bookId`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_progress_chapterId` ON `reading_progress` (`chapterId`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"
    }
}
