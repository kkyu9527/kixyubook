package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.database.entity.UserFontEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FontDeletionReferenceTest {
    private class FakeSettings : ReaderSettingsRepository {
        val state = MutableStateFlow(ReaderSettings())
        override val settings: Flow<ReaderSettings> = state
        override val readingGoalMinutes: Flow<Int> = MutableStateFlow(30)
        override val searchHistory: Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
            state.value = transform(state.value)
        }
        override suspend fun setReadingGoalMinutes(minutes: Int) = Unit
        override suspend fun addSearchHistory(query: String) = Unit
        override suspend fun clearSearchHistory() = Unit
    }

    /** Simulates another writer selecting a new font inside the update call. */
    private class RacingSettings(private val newerFontUuid: String) : ReaderSettingsRepository {
        val state = MutableStateFlow(ReaderSettings(fontUuid = "font-1"))
        private var racePending = true
        override val settings: Flow<ReaderSettings> = state
        override val readingGoalMinutes: Flow<Int> = MutableStateFlow(30)
        override val searchHistory: Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
            if (racePending) {
                racePending = false
                state.value = state.value.copy(fontUuid = newerFontUuid)
            }
            state.value = transform(state.value)
        }
        override suspend fun setReadingGoalMinutes(minutes: Int) = Unit
        override suspend fun addSearchHistory(query: String) = Unit
        override suspend fun clearSearchHistory() = Unit
    }

    private class FailingSettings : ReaderSettingsRepository {
        var failing = true
        val state = MutableStateFlow(ReaderSettings(fontUuid = "font-1"))
        override val settings: Flow<ReaderSettings> = state
        override val readingGoalMinutes: Flow<Int> = MutableStateFlow(30)
        override val searchHistory: Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
            if (failing) error("settings write failed")
            state.value = transform(state.value)
        }
        override suspend fun setReadingGoalMinutes(minutes: Int) = Unit
        override suspend fun addSearchHistory(query: String) = Unit
        override suspend fun clearSearchHistory() = Unit
    }

    private class RecordingRecorder : SyncMutationRecorder {
        val recorded = mutableListOf<Triple<SyncEntityType, String, SyncMutationOperation>>()
        override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) {
            recorded += Triple(type, entityId, operation)
        }
    }

    private fun database(): KixyuDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication() as Context,
        KixyuDatabase::class.java,
    ).build()

    private fun repository(
        context: Context,
        database: KixyuDatabase,
        settings: ReaderSettingsRepository,
    ) = LocalFontRepository(
        context,
        database.fontDao(),
        RecordingRecorder(),
        database,
        database.repairDao(),
        settings,
    )

    private fun fontFile(context: Context, uuid: String): File =
        File(context.filesDir, "fonts/$uuid.ttf").apply {
            parentFile?.mkdirs()
            writeText("font")
        }

    @Test
    fun deletingTheSelectedFontClearsTheReferenceAfterTheCommit() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = database()
        try {
            val settings = FakeSettings().apply { state.value = ReaderSettings(fontUuid = "font-1") }
            val file = fontFile(context, "font-1")
            database.fontDao().insert(UserFontEntity("font-1", "自定义", file.absolutePath, 0))
            val repository = repository(context, database, settings)

            repository.deleteFont("font-1")

            assertNull("a config still referencing a deleted font breaks backup export", settings.state.value.fontUuid)
            assertNull(database.fontDao().getFont("font-1"))
            assertTrue(!file.exists())
            assertEquals(0, database.repairDao().count())
        } finally {
            database.close()
        }
    }

    @Test
    fun aRemoteFontDeletionDefersTheReferenceRepairUntilTheTransactionCommits() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = database()
        try {
            val settings = FakeSettings().apply { state.value = ReaderSettings(fontUuid = "font-1") }
            val file = fontFile(context, "font-1")
            database.fontDao().insert(UserFontEntity("font-1", "自定义", file.absolutePath, 0))
            val repository = repository(context, database, settings)

            runWithPostCommitFileCleanup {
                database.withTransaction {
                    repository.deleteFontRemote("font-1")
                    assertTrue("file cleanup must not run inside the transaction", file.exists())
                    assertEquals(
                        "the DataStore repair must not run before the Room transaction commits",
                        "font-1",
                        settings.state.value.fontUuid,
                    )
                    assertEquals(1, database.repairDao().count())
                }
            }

            assertNull("the repair runs after the commit", settings.state.value.fontUuid)
            assertNull(database.fontDao().getFont("font-1"))
            assertTrue("cleanup runs after the transaction commits", !file.exists())
            assertEquals(0, database.repairDao().count())
        } finally {
            database.close()
        }
    }

    @Test
    fun aRolledBackDeletionLeavesTheReferenceAndTheRepairIntentUntouched() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = database()
        try {
            val settings = FakeSettings().apply { state.value = ReaderSettings(fontUuid = "font-1") }
            val file = fontFile(context, "font-1")
            database.fontDao().insert(UserFontEntity("font-1", "自定义", file.absolutePath, 0))
            val repository = repository(context, database, settings)

            val failure = runCatching {
                runWithPostCommitFileCleanup {
                    database.withTransaction {
                        repository.deleteFontRemote("font-1")
                        error("rollback")
                    }
                }
            }

            assertTrue(failure.isFailure)
            assertNotNull("the font row must survive the rollback", database.fontDao().getFont("font-1"))
            assertEquals("the reference must stay untouched", "font-1", settings.state.value.fontUuid)
            assertEquals("the repair intent rolls back with the delete", 0, database.repairDao().count())
            assertTrue(file.exists())
        } finally {
            database.close()
        }
    }

    @Test
    fun aFailedReferenceRepairIsRetriedUntilItSucceeds() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = database()
        try {
            val settings = FailingSettings()
            val file = fontFile(context, "font-1")
            database.fontDao().insert(UserFontEntity("font-1", "自定义", file.absolutePath, 0))
            val repository = repository(context, database, settings)

            val failure = runCatching {
                runWithPostCommitFileCleanup {
                    database.withTransaction { repository.deleteFontRemote("font-1") }
                }
            }

            assertTrue("a failed repair must surface to the caller", failure.isFailure)
            assertNull("the delete itself committed", database.fontDao().getFont("font-1"))
            assertEquals("the reference is still dangling until the retry", "font-1", settings.state.value.fontUuid)
            assertEquals("the repair intent must be queued for retry", 1, database.repairDao().count())

            settings.failing = false
            repository.observeFonts().first()

            assertNull("observing fonts retries the queued repair", settings.state.value.fontUuid)
            assertEquals(0, database.repairDao().count())
        } finally {
            database.close()
        }
    }

    @Test
    fun aFontSelectedWhileTheOldReferenceIsClearedSurvives() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = database()
        try {
            val settings = RacingSettings(newerFontUuid = "font-2")
            val file = fontFile(context, "font-1")
            database.fontDao().insert(UserFontEntity("font-1", "旧字体", file.absolutePath, 0))
            val repository = repository(context, database, settings)

            repository.deleteFont("font-1")

            assertEquals(
                "a font selected between the read and the write must not be cleared",
                "font-2",
                settings.state.value.fontUuid,
            )
            assertNull(database.fontDao().getFont("font-1"))
        } finally {
            database.close()
        }
    }

    @Test
    fun aSyncedFontIsStoredUnderTheSharedStorageLock() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = database()
        try {
            val settings = FakeSettings()
            val source = File(context.cacheDir, "cloud-font.ttf").apply { writeText("font") }
            val repository = repository(context, database, settings)

            val stored: UserFont = repository.storeSyncedFont("font-2", "云端字体", 7, source)

            assertEquals("font-2", stored.uuid)
            assertEquals("云端字体", stored.name)
            val row = database.fontDao().getFont("font-2")!!
            assertTrue(File(row.filePath).exists())
        } finally {
            database.close()
        }
    }
}
