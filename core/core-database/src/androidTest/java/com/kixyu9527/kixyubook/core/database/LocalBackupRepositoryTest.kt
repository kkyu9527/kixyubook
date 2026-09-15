package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.ReadingReminderRepository
import com.kixyu9527.kixyubook.core.common.model.LibraryPreferences
import com.kixyu9527.kixyubook.core.common.model.LibrarySortMode
import com.kixyu9527.kixyubook.core.common.model.LibraryLayoutMode
import com.kixyu9527.kixyubook.core.common.model.ReadingReminderSettings
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
        val library = FakeLibraryPreferences()
        val reminders = FakeReminders()
        val repository = LocalBackupRepository(context, database, FakeReaderSettingsRepository(), library, reminders)

        val exported = repository.exportTo(backupFile.toUri().toString()).getOrThrow()
        assertEquals(1, exported.bookCount)
        assertTrue(backupFile.length() > 0L)
        library.replace(LibraryPreferences())
        reminders.replace(ReadingReminderSettings())

        database.openHelper.writableDatabase.execSQL(
            "UPDATE books SET title = '被修改的书名' WHERE uuid = 'book-1'",
        )
        val restored = repository.restoreFrom(backupFile.toUri().toString()).getOrThrow()

        assertEquals(1, restored.bookCount)
        assertTrue(restored.requiresRestart)
        assertEquals(listOf("book-1"), library.preferences.value.customOrder)
        assertEquals(ReadingReminderSettings(true, 21, 42), reminders.readingReminder.value)
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
        override val searchHistory: Flow<List<String>> = MutableStateFlow(emptyList())

        override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
            currentSettings.value = transform(currentSettings.value)
        }

        override suspend fun setReadingGoalMinutes(minutes: Int) {
            currentGoal.value = minutes
        }

        override suspend fun addSearchHistory(query: String) = Unit

        override suspend fun clearSearchHistory() = Unit
    }

    private class FakeReminders : ReadingReminderRepository {
        override val readingReminder = MutableStateFlow(ReadingReminderSettings(true, 21, 42))
        override suspend fun replace(settings: ReadingReminderSettings) { readingReminder.value = settings }
    }

    private class FakeLibraryPreferences : LibraryPreferencesRepository {
        override val preferences = MutableStateFlow(LibraryPreferences(customOrder = listOf("book-1")))
        override suspend fun replace(preferences: LibraryPreferences) { this.preferences.value = preferences }
        override suspend fun setSortMode(mode: LibrarySortMode) { preferences.value = preferences.value.copy(sortMode = mode) }
        override suspend fun setLayoutMode(mode: LibraryLayoutMode) { preferences.value = preferences.value.copy(layoutMode = mode) }
        override suspend fun setCustomOrder(bookUuids: List<String>) { preferences.value = preferences.value.copy(customOrder = bookUuids) }
        override suspend fun setCategoryHidden(category: String, hidden: Boolean) = Unit
    }

    private companion object {
        const val DATABASE_NAME = "kixyu-books.db"
    }
}
