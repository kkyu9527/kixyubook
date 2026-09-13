package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.database.entity.ChapterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpubIndexQueueIsolationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun corruptBookDoesNotStopTheWholeIndexQueue() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val preferences = context.getSharedPreferences("epub-index-queue-test", Context.MODE_PRIVATE)
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = database.bookDao()
            listOf("bad-1", "bad-2").forEachIndexed { index, uuid ->
                dao.insertBook(
                    BookEntity(
                        uuid, "坏书$index", "", "", null, "EPUB", "",
                        File(context.filesDir, "$uuid.epub").absolutePath, 1, "hash-$uuid", "",
                    ),
                )
                dao.insertChapter(
                    ChapterEntity(
                        bookUuid = uuid,
                        title = "第一章",
                        chapterIndex = 0,
                        indexed = false,
                        chapterKey = "key-$uuid",
                    ),
                )
            }
            val coordinator = EpubIndexCoordinator(
                database, dao, EpubParseCoordinator(), EpubChapterCache(folder.newFolder("cache")),
                Mutex(), Mutex(), preferences, {},
            )

            // Both books fail to parse (their files do not exist). continueAll must skip them and
            // return instead of throwing, so one bad EPUB cannot block the queue forever.
            withTimeout(15_000) { coordinator.continueAll() }

            assertTrue(dao.getBooksPendingEpubIndex().containsAll(listOf("bad-1", "bad-2")))
        } finally {
            database.close()
            preferences.edit().clear().commit()
        }
    }
}
