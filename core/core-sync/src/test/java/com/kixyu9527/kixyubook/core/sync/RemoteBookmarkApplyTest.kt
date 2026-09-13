package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.database.entity.BookmarkEntity
import com.kixyu9527.kixyubook.core.database.entity.ChapterEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteBookmarkApplyTest {
    private fun remoteJson(vararg uuids: String): JSONObject = JSONObject()
        .put("bookUuid", "book")
        .put(
            "items",
            JSONArray().apply {
                uuids.forEachIndexed { index, uuid ->
                    put(
                        JSONObject()
                            .put("uuid", uuid)
                            .put("chapterKey", "k")
                            .put("chapterIndex", 0)
                            .put("paragraphIndex", index)
                            .put("preview", "远端-$uuid")
                            .put("createdTime", index.toLong()),
                    )
                }
            },
        )

    private fun database(): KixyuDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication() as Context,
        KixyuDatabase::class.java,
    ).build()

    @Test fun locallyAddedBookmarkSurvivesAPullThatRunsWhileItIsPending() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val books = database.bookDao()
            val syncDao = database.syncDao()
            books.insertBook(BookEntity("book", "书", "", "", null, "EPUB", "", "", 0, "hash", ""))
            books.insertChapter(ChapterEntity(1, "book", "第一章", 0, chapterKey = "k"))
            // Local bookmark added during the pull; its outbox row commits in the same transaction.
            books.insertBookmark(BookmarkEntity("local", "book", 1, 0, "本地", 1))
            syncDao.upsertOutbox(
                SyncOutboxEntity("m1", SyncEntityType.BOOKMARKS.name, "book", "UPSERT", 1, 1, "device"),
            )

            val applied = replaceBookmarksFromRemote(database, books, syncDao, remoteJson("remote"))

            assertFalse("a skipped apply must tell the caller to keep the queued edit", applied)
            assertEquals(listOf("local"), books.getAllBookmarkEntities().map { it.uuid })
        } finally {
            database.close()
        }
    }

    @Test fun remoteListReplacesLocalBookmarksWhenNothingIsPending() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val books = database.bookDao()
            val syncDao = database.syncDao()
            books.insertBook(BookEntity("book", "书", "", "", null, "EPUB", "", "", 0, "hash", ""))
            books.insertChapter(ChapterEntity(1, "book", "第一章", 0, chapterKey = "k"))
            books.insertBookmark(BookmarkEntity("local", "book", 1, 0, "本地", 1))

            val applied = replaceBookmarksFromRemote(database, books, syncDao, remoteJson("remote"))

            assertTrue(applied)
            assertEquals(listOf("remote"), books.getAllBookmarkEntities().map { it.uuid })
        } finally {
            database.close()
        }
    }
}
