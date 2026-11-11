package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
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
    @Test fun theWholeShelfAppearsBeforeSecondaryDataWithoutDownloadingAnyBookFiles() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val entries = (1..40).flatMap {
                val id = UUID.randomUUID().toString()
                val key = "books/$id/metadata"
                f.payloads[key] = JSONObject().put("uuid", id).put("contentHash", "hash-$id").put("title", "书籍 $it")
                listOf(key, "books/$id/source")
            }
            val secondary = "sessions/${UUID.randomUUID()}"
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val remote = object : CloudRemoteObjectApplier by f.applier {
                override suspend fun applySession(token: String, info: DriveObject): Boolean {
                    entered.complete(Unit); release.await(); return false
                }
            }
            val changes = (entries + secondary).associateWith(::remoteTestObject)
            val syncing = async { f.pipeline(remote).applyRemoteChanges("test", changes, changes, true, null) {} }
            try {
                withTimeout(10_000) { entered.await() }
                val books = f.books.getAllBooks()
                assertEquals(40, books.size)
                assertTrue(books.all { it.storagePath.isEmpty() })
                assertTrue(f.sync.allObjectStates().none { it.objectKey.endsWith("/source") })
                assertFalse("the reading-data stage is still waiting", syncing.isCompleted)
            } finally { release.complete(Unit); syncing.await() }
        }
    }

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

    @Test fun newBooksAppearOnTheShelfWithoutDownloadingTheirSource() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val book = UUID.randomUUID().toString()
            val key = "books/$book/metadata"
            f.payloads[key] = JSONObject().put("uuid", book).put("contentHash", "hash").put("title", "云端书籍")
            f.pipeline().applyRemoteChanges("test", mapOf(key to remoteTestObject(key)), emptyMap(), true, null) {}
            val entry = f.books.getBook(book)!!
            assertEquals("云端书籍", entry.title)
            assertEquals("", entry.storagePath)
            assertEquals("google-drive://$book", entry.originalPath)
            assertTrue(f.books.getChapters(book).isEmpty())
            assertNotNull(f.sync.objectState(key))
            assertTrue(f.sync.remoteInbox().isEmpty())
        }
    }

    @Test fun receivingASourceDoesNotFetchTheMetadataOrDownloadTheBookAgain() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val book = UUID.randomUUID().toString()
            val key = "books/$book/metadata"
            val source = "books/$book/source"
            f.payloads[key] = JSONObject().put("uuid", book).put("contentHash", "hash")
            f.pipeline().applyRemoteChanges("test", mapOf(key to remoteTestObject(key)), emptyMap(), false, null) {}
            f.payloads.clear() // A repeated metadata download would now fail.
            f.pipeline().applyRemoteChanges("test", mapOf(source to remoteTestObject(source)), emptyMap(), true, null) {}
            assertEquals("", f.books.getBook(book)!!.storagePath)
            assertEquals(source, f.sync.remoteInbox().single().objectKey)
            assertNull(f.sync.objectState(source))
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
