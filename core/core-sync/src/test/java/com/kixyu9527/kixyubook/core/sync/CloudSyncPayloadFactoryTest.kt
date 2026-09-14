package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.model.LibraryLayoutMode
import com.kixyu9527.kixyubook.core.common.model.LibraryPreferences
import com.kixyu9527.kixyubook.core.common.model.LibrarySortMode
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.database.entity.BookmarkEntity
import com.kixyu9527.kixyubook.core.database.entity.ChapterEntity
import com.kixyu9527.kixyubook.core.database.entity.PendingBookmarkEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudSyncPayloadFactoryTest {
    private val recorder = object : SyncMutationRecorder {
        override suspend fun record(
            type: SyncEntityType,
            entityId: String,
            operation: SyncMutationOperation,
        ) = Unit
    }

    private val settingsRepository = object : ReaderSettingsRepository {
        override val settings: Flow<ReaderSettings> = flowOf(ReaderSettings())
        override val readingGoalMinutes: Flow<Int> = flowOf(30)
        override val searchHistory: Flow<List<String>> = flowOf(emptyList())
        override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) = Unit
        override suspend fun setReadingGoalMinutes(minutes: Int) = Unit
        override suspend fun addSearchHistory(query: String) = Unit
        override suspend fun clearSearchHistory() = Unit
    }

    private val libraryPreferences = object : LibraryPreferencesRepository {
        override val preferences: Flow<LibraryPreferences> = MutableStateFlow(LibraryPreferences())
        override suspend fun setSortMode(mode: LibrarySortMode) = Unit
        override suspend fun setLayoutMode(mode: LibraryLayoutMode) = Unit
        override suspend fun setCustomOrder(bookUuids: List<String>) = Unit
        override suspend fun setCategoryHidden(category: String, hidden: Boolean) = Unit
        override suspend fun replace(preferences: LibraryPreferences) = Unit
    }

    @Test
    fun uploadedBookmarkSnapshotIncludesPendingRecords() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(BookEntity("book", "书", "", "", null, "TXT", "", "", 0, "hash", ""))
            dao.insertChapter(ChapterEntity(1, "book", "第一章", 0, chapterKey = "k"))
            dao.insertBookmark(BookmarkEntity("located", "book", 1, 0, "已定位", 1))
            dao.insertPendingBookmark(PendingBookmarkEntity("pending", "book", "锚点正文", "待恢复", 2))
            val factory = CloudSyncPayloadFactory(
                context = context,
                books = dao,
                fonts = database.fontDao(),
                corrections = database.textCorrectionDao(),
                annotations = database.readerAnnotationDao(),
                settingsRepository = settingsRepository,
                libraryPreferencesRepository = libraryPreferences,
                readingReminders = ReadingReminderScheduler(context, NotificationPreferencesStore(context, recorder)),
                preferences = SyncPreferencesStore(context),
            )
            val mutation = SyncOutboxEntity(
                "m1", SyncEntityType.BOOKMARKS.name, "book", "UPSERT", 1, 1, "device",
            )

            val payload = factory.materialize(mutation, includeLargePayload = false).single()
            val items = JSONObject(payload.file.readText()).getJSONArray("items")
            val byUuid = (0 until items.length()).associate { index ->
                val item = items.getJSONObject(index)
                item.getString("uuid") to item
            }

            assertEquals(setOf("located", "pending"), byUuid.keys)
            assertEquals(BOOKMARK_STATUS_PENDING, byUuid.getValue("pending").getString("status"))
            assertEquals("锚点正文", byUuid.getValue("pending").getString("anchorText"))
            assertEquals(BOOKMARK_STATUS_LOCATED, byUuid.getValue("located").getString("status"))
        } finally {
            database.close()
        }
    }
}
