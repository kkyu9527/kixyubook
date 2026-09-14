package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.database.entity.ChapterEntity
import com.kixyu9527.kixyubook.core.database.entity.PendingBookmarkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Real Room coverage for the pending-bookmark lifecycle: the unique
 * (book, chapter, position) bookmark index must never let a conflict silently drop a uuid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PendingBookmarkReconciliationTest {
    private fun database(): KixyuDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication() as Context,
        KixyuDatabase::class.java,
    ).build()

    private suspend fun prepare(database: KixyuDatabase, vararg anchors: String) {
        val dao = database.bookDao()
        dao.insertBook(BookEntity("book", "书", "", "", null, "TXT", "", "", 0, "hash", ""))
        dao.insertChapter(ChapterEntity(10, "book", "第一章", 0, chapterKey = "k"))
        dao.insertParagraphsChunked(10, listOf("目标正文", "其它正文"))
        anchors.forEachIndexed { index, anchor ->
            dao.insertPendingBookmark(
                PendingBookmarkEntity("pb${index + 1}", "book", anchor, "预览${index + 1}", index.toLong()),
            )
        }
    }

    @Test
    fun twoPendingBookmarksSharingAnAnchorBothSurvive() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            prepare(database, "目标正文", "目标正文")
            val dao = database.bookDao()
            val occupied = mutableSetOf<Pair<Long, Int>>()

            reconcilePendingBookmarks(
                dao = dao,
                bookUuid = "book",
                chapterIds = listOf(10L),
                chapterKeys = listOf("k"),
                paragraphsByChapter = mapOf(10L to dao.getParagraphs(10L)),
                occupied = occupied,
            )

            val stored = dao.getAllBookmarkEntities().map { it.uuid }
            val stillPending = dao.getPendingBookmarks("book").map { it.uuid }
            assertEquals("only one can own the single free position", 1, stored.size)
            assertEquals("the other must stay recoverable", 1, stillPending.size)
            assertEquals(setOf("pb1", "pb2"), (stored + stillPending).toSet())
        } finally {
            database.close()
        }
    }

    @Test
    fun anAmbiguousAnchorKeepsThePendingRecord() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val dao = database.bookDao()
            dao.insertBook(BookEntity("book", "书", "", "", null, "TXT", "", "", 0, "hash", ""))
            dao.insertChapter(ChapterEntity(10, "book", "第一章", 0, chapterKey = "k"))
            dao.insertParagraphsChunked(10, listOf("重复正文", "重复正文"))
            dao.insertPendingBookmark(PendingBookmarkEntity("pb1", "book", "重复正文", "预览", 1))

            reconcilePendingBookmarks(
                dao = dao,
                bookUuid = "book",
                chapterIds = listOf(10L),
                chapterKeys = listOf("k"),
                paragraphsByChapter = mapOf(10L to dao.getParagraphs(10L)),
                occupied = mutableSetOf(),
            )

            assertTrue(dao.getAllBookmarkEntities().isEmpty())
            assertEquals(listOf("pb1"), dao.getPendingBookmarks("book").map { it.uuid })
        } finally {
            database.close()
        }
    }

    @Test
    fun aPendingRecordIsCommittedOnlyOnceItsAnchorIsFree() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            prepare(database, "目标正文")
            val dao = database.bookDao()
            val paragraphs = mapOf(10L to dao.getParagraphs(10L))
            val occupiedByExisting = mutableSetOf(10L to 0)

            reconcilePendingBookmarks(
                dao, "book", listOf(10L), listOf("k"), paragraphs, occupiedByExisting,
            )
            assertEquals(listOf("pb1"), dao.getPendingBookmarks("book").map { it.uuid })

            dao.deleteBookmarksForBook("book")
            val occupied = mutableSetOf<Pair<Long, Int>>()
            reconcilePendingBookmarks(
                dao, "book", listOf(10L), listOf("k"), paragraphs, occupied,
            )

            assertTrue(dao.getPendingBookmarks("book").isEmpty())
            assertEquals(listOf("pb1"), dao.getAllBookmarkEntities().map { it.uuid })
        } finally {
            database.close()
        }
    }

    @Test
    fun deletingABookCascadesItsPendingBookmarks() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            prepare(database, "目标正文")
            val dao = database.bookDao()

            dao.deleteBook("book")

            assertTrue(dao.getPendingBookmarks("book").isEmpty())
        } finally {
            database.close()
        }
    }
}
