package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
class RemoteBookApplyTest {
    private fun database(): KixyuDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication() as Context,
        KixyuDatabase::class.java,
    ).build()

    @Test fun pendingLocalMetadataEditSurvivesARemoteApply() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val books = database.bookDao()
            val syncDao = database.syncDao()
            books.insertBook(BookEntity("book", "本地书名", "", "", null, "EPUB", "", "", 0, "hash", ""))
            syncDao.upsertOutbox(
                SyncOutboxEntity("m", SyncEntityType.BOOK.name, "book", "UPSERT", 1, 1, "device"),
            )

            val applied = applyBookMetadataFromRemote(database, syncDao, "book") {
                books.updateBookMetadata("book", "远端书名", "作者", "简介")
            }

            assertFalse(applied)
            assertEquals("本地书名", books.getBook("book")!!.title)
        } finally {
            database.close()
        }
    }

    @Test fun remoteMetadataAppliesWhenNoLocalEditIsPending() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val books = database.bookDao()
            val syncDao = database.syncDao()
            books.insertBook(BookEntity("book", "本地书名", "", "", null, "EPUB", "", "", 0, "hash", ""))

            val applied = applyBookMetadataFromRemote(database, syncDao, "book") {
                books.updateBookMetadata("book", "远端书名", "作者", "简介")
            }

            assertTrue(applied)
            assertEquals("远端书名", books.getBook("book")!!.title)
        } finally {
            database.close()
        }
    }
}
