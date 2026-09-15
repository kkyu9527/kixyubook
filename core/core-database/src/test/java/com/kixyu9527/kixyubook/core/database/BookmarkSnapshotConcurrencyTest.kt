package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.database.entity.BookmarkEntity
import com.kixyu9527.kixyubook.core.database.entity.ChapterEntity
import com.kixyu9527.kixyubook.core.database.entity.PendingBookmarkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The sync snapshot must be one transaction. A reader whose located and pending queries straddle
 * the commit of a bookmark migration sees neither table contain the uuid, and the uploaded list
 * would then lose it everywhere.
 *
 * A real file-backed Room database with WAL is required: an in-memory database serializes every
 * call on one connection, so the interleaving could never happen. A large located table makes the
 * snapshot's first query slow enough for the migration to commit in the middle of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookmarkSnapshotConcurrencyTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun aSnapshotNeverMissesABookmarkBeingMigrated() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val database = Room.databaseBuilder(
            context,
            KixyuDatabase::class.java,
            folder.newFile("snapshot.db").absolutePath,
        ).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).build()
        try {
            val dao = database.bookDao()
            dao.insertBook(BookEntity("book", "书", "", "", null, "TXT", "", "", 0, "hash", ""))
            dao.insertChapter(ChapterEntity(10, "book", "第一章", 0, chapterKey = "k"))
            dao.insertChapter(ChapterEntity(99, "book", "填充章节", 1, chapterKey = "fill"))
            dao.insertParagraphsChunked(10, listOf("目标正文", "其它正文"))
            database.withTransaction {
                repeat(SLOW_SNAPSHOT_ROWS) { index ->
                    dao.insertBookmark(
                        BookmarkEntity("located-$index", "book", 99, index, "已定位", 1, "fill"),
                    )
                }
            }
            val paragraphsByChapter = mapOf(10L to dao.getParagraphs(10L))

            repeat(3) { iteration ->
                dao.deleteBookmark("moving")
                dao.deletePendingBookmark("moving")
                dao.insertPendingBookmark(
                    PendingBookmarkEntity("moving", "book", "目标正文", "预览", 1),
                )

                val snapshot = async(Dispatchers.IO) { dao.bookmarkSnapshot("book") }
                // Let the reader start its (slow) located query before the migration commits.
                delay(2)
                val migration = async(Dispatchers.IO) {
                    database.withTransaction {
                        reconcilePendingBookmarks(
                            dao = dao,
                            bookUuid = "book",
                            chapterIds = listOf(10L),
                            chapterKeys = listOf("k"),
                            paragraphsByChapter = paragraphsByChapter,
                            occupied = mutableSetOf(),
                        )
                    }
                }

                val observed = snapshot.await()
                migration.await()
                val uuids = observed.located.map { it.uuid } + observed.pending.map { it.uuid }
                assertEquals(
                    "iteration $iteration must see the migration's pre or post state exactly once",
                    1,
                    uuids.count { it == "moving" },
                )
                assertEquals("the snapshot must stay consistent", observed.located.size + observed.pending.size, uuids.size)
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val SLOW_SNAPSHOT_ROWS = 30_000
    }
}
