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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    @Test
    fun deletingTheSelectedFontClearsTheGlobalReferenceBeforeTheRowGoesAway() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = database()
        try {
            val settings = FakeSettings().apply { state.value = ReaderSettings(fontUuid = "font-1") }
            val file = File(context.filesDir, "fonts/font-1.ttf").apply {
                parentFile?.mkdirs()
                writeText("font")
            }
            database.fontDao().insert(UserFontEntity("font-1", "自定义", file.absolutePath, 0))
            val repository = LocalFontRepository(context, database.fontDao(), RecordingRecorder(), database, settings)

            repository.deleteFont("font-1")

            assertNull("a config still referencing a deleted font breaks backup export", settings.state.value.fontUuid)
            assertNull(database.fontDao().getFont("font-1"))
            assertTrue(!file.exists())
        } finally {
            database.close()
        }
    }

    @Test
    fun aRemoteFontDeletionClearsTheReferenceAndDefersTheFileCleanup() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = database()
        try {
            val settings = FakeSettings().apply { state.value = ReaderSettings(fontUuid = "font-1") }
            val file = File(context.filesDir, "fonts/font-1.ttf").apply {
                parentFile?.mkdirs()
                writeText("font")
            }
            database.fontDao().insert(UserFontEntity("font-1", "自定义", file.absolutePath, 0))
            val repository = LocalFontRepository(context, database.fontDao(), RecordingRecorder(), database, settings)

            // Inside the caller's tombstone transaction neither the file nor the global reference
            // may change; both follow the commit.
            runWithPostCommitFileCleanup {
                database.withTransaction {
                    repository.deleteFontRemote("font-1")
                    assertTrue("cleanup must not run inside the transaction", file.exists())
                    assertEquals("font-1", settings.state.value.fontUuid)
                }
            }

            assertNull(settings.state.value.fontUuid)
            assertNull(database.fontDao().getFont("font-1"))
            assertTrue("cleanup runs after the transaction commits", !file.exists())
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
            val repository = LocalFontRepository(context, database.fontDao(), RecordingRecorder(), database, settings)

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
