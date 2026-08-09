package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalBackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: KixyuDatabase
    private lateinit var backupFile: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        listOf("books", "covers", "fonts").forEach { File(context.filesDir, it).deleteRecursively() }
        database = Room.databaseBuilder(context, KixyuDatabase::class.java, DATABASE_NAME)
            .addMigrations(*DatabaseMigrations.supported)
            .build()
        backupFile = File(context.cacheDir, "round-trip.kixyubackup").apply { delete() }
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        context.deleteDatabase(DATABASE_NAME)
        listOf("books", "covers", "fonts").forEach { File(context.filesDir, it).deleteRecursively() }
        backupFile.delete()
    }

    @Test
    fun exportThenRestore_preservesCurrentDatabaseAndOriginalBook() = runBlocking {
        val bookFile = File(context.filesDir, "books/book-1.txt").apply {
            parentFile?.mkdirs()
            writeText("第一章\n测试正文")
        }
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO books (
                uuid, title, author, description, coverPath, format, originalPath,
                storagePath, createdTime, contentHash, category
            ) VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                "book-1",
                "备份前书名",
                "作者",
                "简介",
                "TXT",
                "source.txt",
                bookFile.absolutePath,
                1L,
                "hash-1",
                "测试",
            ),
        )
        val repository = LocalBackupRepository(context, database, FakeReaderSettingsRepository())

        val exported = repository.exportTo(backupFile.toUri().toString()).getOrThrow()
        assertEquals(1, exported.bookCount)
        assertTrue(backupFile.length() > 0L)

        database.openHelper.writableDatabase.execSQL(
            "UPDATE books SET title = '被修改的书名' WHERE uuid = 'book-1'",
        )
        val restored = repository.restoreFrom(backupFile.toUri().toString()).getOrThrow()

        assertEquals(1, restored.bookCount)
        assertTrue(restored.requiresRestart)
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { restoredDatabase ->
            restoredDatabase.rawQuery(
                "SELECT title, storagePath FROM books WHERE uuid = 'book-1'",
                null,
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("备份前书名", cursor.getString(0))
                assertEquals(bookFile.absolutePath, cursor.getString(1))
            }
            restoredDatabase.rawQuery("PRAGMA user_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(KIXYU_DATABASE_VERSION, cursor.getInt(0))
            }
        }
        assertEquals("第一章\n测试正文", bookFile.readText())
    }

    private class FakeReaderSettingsRepository : ReaderSettingsRepository {
        private val currentSettings = MutableStateFlow(ReaderSettings())
        private val currentGoal = MutableStateFlow(30)

        override val settings: Flow<ReaderSettings> = currentSettings
        override val readingGoalMinutes: Flow<Int> = currentGoal

        override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
            currentSettings.value = transform(currentSettings.value)
        }

        override suspend fun setReadingGoalMinutes(minutes: Int) {
            currentGoal.value = minutes
        }
    }

    private companion object {
        const val DATABASE_NAME = "kixyu-books.db"
    }
}
