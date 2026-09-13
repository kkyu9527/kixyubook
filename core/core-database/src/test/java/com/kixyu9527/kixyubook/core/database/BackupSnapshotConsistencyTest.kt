package com.kixyu9527.kixyubook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.net.toUri
import androidx.room.Room
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.withLock
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.ReadingReminderRepository
import com.kixyu9527.kixyubook.core.common.repository.NoBookSettings
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [BackupSnapshotConsistencyTest.Storage::class])
class BackupSnapshotConsistencyTest {
    @org.robolectric.annotation.Implements(android.os.storage.StorageManager::class)
    class Storage {
        @org.robolectric.annotation.Implementation
        fun getUuidForPath(path: File): java.util.UUID = android.os.storage.StorageManager.UUID_DEFAULT
        @org.robolectric.annotation.Implementation
        fun getAllocatableBytes(uuid: java.util.UUID): Long = Long.MAX_VALUE
    }
    private lateinit var context: Context
    private lateinit var database: KixyuDatabase
    private lateinit var backupFile: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
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
    fun concurrentDeletionAfterSnapshotDoesNotBreakPortableBackup() = runBlocking {
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
        var removed = false
        val settings = FakeReaderSettingsRepository {
            if (!removed) {
                // Compression hasn't begun, but snapshot and all assets must already be pinned.
                assertFalse(LibraryStorageGate.mutex.isLocked)
                LibraryStorageGate.mutex.withLock {
                    database.bookDao().deleteBook("book-1")
                    assertTrue(bookFile.delete())
                    removed = true
                }
            }
        }
        val repository = LocalBackupRepository(context, database, settings, library, reminders, NoBookSettings)

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

    @Test
    fun fontSelectedAfterTheSnapshotIsPinnedIntoTheArchive() = runBlocking {
        val fontUuid = "font-1"
        val fontFile = File(context.filesDir, "fonts/$fontUuid.ttf")
        var added = false
        // Settings are read after the snapshot is pinned, so this hook models a font picked in
        // between: it exists in the live database but is absent from the pinned snapshot.
        val settings = FakeReaderSettingsRepository(ReaderSettings(fontUuid = fontUuid)) {
            if (!added) {
                fontFile.parentFile?.mkdirs()
                fontFile.writeText("font-bytes")
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO user_fonts (uuid, name, filePath, createdTime) VALUES (?, ?, ?, ?)",
                    arrayOf<Any?>(fontUuid, "我的字体", fontFile.absolutePath, 1L),
                )
                added = true
            }
        }
        val repository = LocalBackupRepository(
            context, database, settings, FakeLibraryPreferences(), FakeReminders(), NoBookSettings,
        )

        repository.exportTo(backupFile.toUri().toString()).getOrThrow()

        val entries = java.util.zip.ZipFile(backupFile).use { zip ->
            zip.entries().asSequence().map { it.name }.toList()
        }
        assertTrue("archive=$entries", entries.contains("files/fonts/$fontUuid.ttf"))
    }

    @Test
    fun perBookFontChosenAfterTheSnapshotIsPinnedIntoTheArchive() = runBlocking {
        val fontUuid = "font-book"
        val fontFile = File(context.filesDir, "fonts/$fontUuid.ttf")
        var added = false
        val settings = FakeReaderSettingsRepository {
            if (!added) {
                fontFile.parentFile?.mkdirs()
                fontFile.writeText("font-bytes")
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO user_fonts (uuid, name, filePath, createdTime) VALUES (?, ?, ?, ?)",
                    arrayOf<Any?>(fontUuid, "单书字体", fontFile.absolutePath, 1L),
                )
                added = true
            }
        }
        val bookSettings = FakeBookSettings(
            mapOf("book-1" to org.json.JSONObject().put("fontUuid", fontUuid).toString()),
        )
        val repository = LocalBackupRepository(
            context, database, settings, FakeLibraryPreferences(), FakeReminders(), bookSettings,
        )

        repository.exportTo(backupFile.toUri().toString()).getOrThrow()

        val entries = java.util.zip.ZipFile(backupFile).use { zip ->
            zip.entries().asSequence().map { it.name }.toList()
        }
        assertTrue("archive=$entries", entries.contains("files/fonts/$fontUuid.ttf"))
    }

    @Test
    fun fontDeletedMidExportIsRetriedAgainstAFreshSettingsSnapshot() = runBlocking {
        val fontUuid = "font-race"
        val fontFile = File(context.filesDir, "fonts/$fontUuid.ttf")
        // The font is imported and selected only after the snapshot, then deleted while the settings
        // reference is already being read. The retry must observe the cleared reference.
        val settings = SequencedSettings(
            values = listOf(ReaderSettings(fontUuid = fontUuid), ReaderSettings()),
            onRead = { index ->
                if (index == 0) {
                    fontFile.parentFile?.mkdirs()
                    fontFile.writeText("font-bytes")
                    database.openHelper.writableDatabase.execSQL(
                        "INSERT INTO user_fonts (uuid, name, filePath, createdTime) VALUES (?, ?, ?, ?)",
                        arrayOf<Any?>(fontUuid, "竞态字体", fontFile.absolutePath, 1L),
                    )
                }
            },
        )
        val bookSettings = FakeBookSettings(emptyMap()) {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM user_fonts WHERE uuid = ?",
                arrayOf<Any?>(fontUuid),
            )
            fontFile.delete()
        }
        val repository = LocalBackupRepository(
            context, database, settings, FakeLibraryPreferences(), FakeReminders(), bookSettings,
        )

        repository.exportTo(backupFile.toUri().toString()).getOrThrow()

        val entries = java.util.zip.ZipFile(backupFile).use { zip ->
            zip.entries().asSequence().map { it.name }.toList()
        }
        assertFalse("archive=$entries", entries.contains("files/fonts/$fontUuid.ttf"))
    }

    @Test
    fun fontReferencedBySettingsButMissingFailsInsteadOfSilentlySkipping() = runBlocking {
        val settings = FakeReaderSettingsRepository(ReaderSettings(fontUuid = "font-gone"))
        val repository = LocalBackupRepository(
            context, database, settings, FakeLibraryPreferences(), FakeReminders(), NoBookSettings,
        )

        val result = repository.exportTo(backupFile.toUri().toString())

        assertTrue(result.isFailure)
    }

    private class SequencedSettings(
        private val values: List<ReaderSettings>,
        private val onRead: suspend (Int) -> Unit = {},
    ) : ReaderSettingsRepository {
        private var reads = 0
        override val settings: Flow<ReaderSettings> = flow {
            val index = reads.coerceAtMost(values.lastIndex)
            reads++
            onRead(index)
            emit(values[index])
        }
        override val readingGoalMinutes: Flow<Int> = MutableStateFlow(30)
        override val searchHistory: Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) = Unit
        override suspend fun setReadingGoalMinutes(minutes: Int) = Unit
        override suspend fun addSearchHistory(query: String) = Unit
        override suspend fun clearSearchHistory() = Unit
    }

    private class FakeBookSettings(
        private val values: Map<String, String>,
        private val onRead: suspend () -> Unit = {},
    ) : com.kixyu9527.kixyubook.core.common.repository.BookSettingsRepository {
        override val overrides: Flow<Map<String, String>> = flow { onRead(); emit(values) }
        override suspend fun setEnabled(bookUuid: String, enabled: Boolean) = Unit
        override suspend fun updateField(bookUuid: String, field: String, encodedValue: String) = Unit
        override suspend fun replaceAll(values: Map<String, String>) = Unit
        override suspend fun clearFontReferences(fontUuid: String) = Unit
    }

    private class FakeReaderSettingsRepository(
        initial: ReaderSettings = ReaderSettings(),
        private val beforeRead: suspend () -> Unit = {},
    ) : ReaderSettingsRepository {
        private val currentSettings = MutableStateFlow(initial)
        private val currentGoal = MutableStateFlow(30)

        override val settings: Flow<ReaderSettings> = flow { beforeRead(); emit(currentSettings.value) }
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
