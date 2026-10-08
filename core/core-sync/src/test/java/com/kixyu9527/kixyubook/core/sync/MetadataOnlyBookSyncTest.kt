package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MetadataOnlyBookSyncTest {
    @Test fun existingBooksReceiveMetadataWithoutACloudSource() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val book = UUID.randomUUID().toString()
            f.addBook(book)
            val key = "books/$book/metadata"
            f.payloads[key] = JSONObject().put("uuid", book).put("contentHash", "hash")
                .put("title", "云端书名").put("author", "作者").put("description", "简介").put("category", "分类")
            val remote = mapOf(key to remoteTestObject(key))
            f.pipeline().applyRemoteChanges("test", remote, remote, true, null) {}
            assertEquals("云端书名", f.books.getBook(book)!!.title)
            assertEquals("分类", f.books.getBook(book)!!.category)
            assertNotNull(f.sync.objectState(key))
            assertTrue(f.sync.remoteInbox().isEmpty())
        }
    }

    @Test fun newBooksStillWaitForTheirSourceAndRetainTheirMetadata() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val book = UUID.randomUUID().toString()
            val key = "books/$book/metadata"
            f.pipeline().applyRemoteChanges("test", mapOf(key to remoteTestObject(key)), emptyMap(), true, null) {}
            assertFalse(f.books.bookExists(book))
            assertNull(f.sync.objectState(key))
            assertEquals(key, f.sync.remoteInbox().single().objectKey)
        }
    }

    @Test fun anEditArrivingDuringMetadataDownloadIsNotConsumedAsApplied() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val book = UUID.randomUUID().toString()
            f.addBook(book)
            f.sync.upsertOutbox(SyncOutboxEntity("local", SyncEntityType.BOOK.name, book, "UPSERT", 2, 2, "device"))
            val key = "books/$book/metadata"
            f.payloads[key] = JSONObject().put("uuid", book).put("contentHash", "hash").put("title", "云端书名")
            assertFalse(f.applier.restoreBook("test", book, mapOf(key to remoteTestObject(key))))
            assertEquals("本地书名", f.books.getBook(book)!!.title)
            assertNull(f.sync.objectState(key))
        }
    }
}
