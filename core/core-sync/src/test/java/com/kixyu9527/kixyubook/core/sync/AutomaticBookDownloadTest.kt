package com.kixyu9527.kixyubook.core.sync

import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.entity.ChapterEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncObjectStateEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomaticBookDownloadTest {
    @Test fun allShelfEntriesAppearThenBodiesDownloadWithoutAnyReaderOpenAndChildrenAreRestored() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val ids = List(6) { UUID.randomUUID().toString() }
            val keys = ids.flatMap { id ->
                f.payloads["books/$id/metadata"] = JSONObject().put("uuid", id).put("contentHash", "hash-$id")
                f.payloads["progress/$id"] = JSONObject().put("bookUuid", id).put("chapterKey", "first")
                    .put("paragraphIndex", 3).put("charOffset", 7).put("progression", .5).put("updatedTime", 2)
                f.payloads["bookmarks/$id"] = JSONObject().put("bookUuid", id).put("items", JSONArray().put(
                    JSONObject().put("uuid", UUID.randomUUID()).put("chapterKey", "first").put("paragraphIndex", 3)))
                listOf("books/$id/metadata", "books/$id/source", "progress/$id", "bookmarks/$id")
            }
            val remote = keys.associateWith(::remoteTestObject)
            var bodies = 0
            f.pipeline().applyRemoteChanges("test", remote, remote, false, ids.first(), restoreSources = { available ->
                assertEquals(ids.size, f.books.getAllBooks().size)
                assertTrue(f.books.getAllBooks().all { it.storagePath.isBlank() })
                val queue = CloudBookSourceQueue(f.books, f.sync, {}, { id ->
                    install(f, id, available)
                })
                assertNull(queue.download(available, ids.first(), true, true) { completed, _ -> bodies = completed })
            }) {}
            assertEquals(6, bodies)
            assertEquals(0, f.sync.pendingBookDownloadCount())
            ids.forEach { id ->
                assertTrue(File(f.books.getBook(id)!!.storagePath).isFile)
                assertEquals(7, f.books.getProgress(id)!!.charOffset)
                assertEquals(1, f.books.getBookmarks(id).size)
            }
            assertTrue(f.sync.remoteInbox().isEmpty())
        }
    }

    @Test fun aSourceArrivingAfterTheShelfAndAnEmptyChangePageBothResumeAutomatically() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val id = UUID.randomUUID().toString()
            val metadata = "books/$id/metadata"
            val source = "books/$id/source"
            f.payloads[metadata] = JSONObject().put("uuid", id).put("contentHash", "hash")
            val installed = mutableListOf<String>()
            var fail = true
            suspend fun sync(changes: Map<String, DriveObject>) {
                f.pipeline().applyRemoteChanges("test", changes, changes, true, null, restoreSources = { available ->
                    CloudBookSourceQueue(f.books, f.sync, {}, { uuid ->
                        if (fail) throw IOException("offline")
                        install(f, uuid, available); installed += uuid
                    }).download(available, null, true, true) { _, _ -> }
                }) {}
            }
            sync(mapOf(metadata to remoteTestObject(metadata)))
            assertTrue(installed.isEmpty())
            sync(mapOf(source to remoteTestObject(source)))
            assertEquals(1, f.sync.pendingBookDownloadCount())
            fail = false
            // Simulate a new worker with no new Drive changes: the Room receipt owns the retry.
            sync(emptyMap())
            assertEquals(listOf(id), installed)
            assertEquals(0, f.sync.pendingBookDownloadCount())
        }
    }

    @Test fun oneBrokenBookDoesNotBlockOtherBodiesAndOnlyItsReceiptRemainsForRetry() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val ids = cloudEntries(f, 5)
            val remote = ids.associate { "books/$it/source" to remoteTestObject("books/$it/source") }
            CloudRemoteInbox(f.database, f.sync).receive(remote)
            val done = ConcurrentHashMap.newKeySet<String>()
            val queue = CloudBookSourceQueue(f.books, f.sync, {}, { id ->
                if (id == ids.first()) throw IOException("one corrupt file")
                install(f, id, remote); done += id
            })
            val failure = queue.download(remote, ids.first(), true, true) { _, _ -> }
            assertTrue(failure is IOException)
            assertEquals(4, done.size)
            assertEquals("books/${ids.first()}/source", f.sync.remoteInbox().single().objectKey)
            assertEquals(1, f.sync.pendingBookDownloadCount())
        }
    }

    @Test fun preferredAndRecentBooksLeadAndDownloadsRunInParallel() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val ids = cloudEntries(f, 5)
            val remote = ids.flatMap { listOf("books/$it/source", "progress/$it") }.associateWith(::remoteTestObject).toMutableMap()
            remote["progress/${ids[1]}"] = remoteTestObject("progress/${ids[1]}", 100)
            val entered = ConcurrentHashMap.newKeySet<String>()
            val enoughStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val active = AtomicInteger()
            val maximum = AtomicInteger()
            val queue = CloudBookSourceQueue(f.books, f.sync, {}, { id ->
                maximum.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                try {
                    entered += id
                    if (entered.size == minOf(MAX_PARALLEL_BOOK_DOWNLOADS, 5)) enoughStarted.complete(Unit)
                    release.await()
                    install(f, id, remote)
                } finally { active.decrementAndGet() }
            })
            val job = async { queue.download(remote, ids.last(), true, true) { _, _ -> } }
            try {
                withTimeout(5_000) { enoughStarted.await() }
                assertTrue(entered.containsAll(setOf(ids.last(), ids[1])))
                assertEquals(minOf(MAX_PARALLEL_BOOK_DOWNLOADS, 5), maximum.get())
            } finally { release.complete(Unit); job.await() }
            assertEquals(5, entered.size)
            assertEquals(minOf(MAX_PARALLEL_BOOK_DOWNLOADS, 5), maximum.get())
        }
    }

    @Test fun wifiAndOriginalFilePolicyRetainWorkAndNeverStartATransfer() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val id = cloudEntries(f, 1).single()
            val key = "books/$id/source"
            val remote = mapOf(key to remoteTestObject(key))
            val queue = CloudBookSourceQueue(f.books, f.sync, {}, { error("must not download") })
            var resumptions = 0
            assertNull(queue.download(remote, null, true, false, onNetworkDeferred = { resumptions++ }) { _, _ -> })
            assertEquals(1, f.sync.pendingBookDownloadCount())
            assertEquals(1, resumptions)
            assertNull(queue.download(remote, null, false, true, onNetworkDeferred = { resumptions++ }) { _, _ -> })
            assertEquals(1, resumptions)
            assertNull(f.sync.objectState(key))
        }
    }

    @Test fun oneSlowLargeBookDoesNotLeaveTheOtherTransferLaneIdle() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val ids = cloudEntries(f, 5)
            val remote = ids.associate { "books/$it/source" to remoteTestObject("books/$it/source") }
            val releaseLargeBook = CompletableDeferred<Unit>()
            val smallBooksFinished = CompletableDeferred<Unit>()
            val finished = AtomicInteger()
            val queue = CloudBookSourceQueue(f.books, f.sync, {}, { id ->
                if (id == ids.first()) releaseLargeBook.await()
                install(f, id, remote)
                if (id != ids.first() && finished.incrementAndGet() == 4) smallBooksFinished.complete(Unit)
            })
            val job = async { queue.download(remote, ids.first(), true, true) { _, _ -> } }
            try {
                withTimeout(5_000) { smallBooksFinished.await() }
                assertEquals(4, finished.get())
                assertFalse(job.isCompleted)
            } finally { releaseLargeBook.complete(Unit); job.await() }
            assertEquals(0, f.sync.pendingBookDownloadCount())
        }
    }

    @Test fun localEditsAndDeletesCannotBeHydratedByTheAutomaticQueue() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val ids = cloudEntries(f, 2)
            ids.forEachIndexed { index, id ->
                f.sync.upsertOutbox(SyncOutboxEntity(UUID.randomUUID().toString(), SyncEntityType.BOOK.name,
                    id, if (index == 0) "DELETE" else "UPSERT", 1, 1, "device"))
            }
            val remote = ids.associate { "books/$it/source" to remoteTestObject("books/$it/source") }
            val queue = CloudBookSourceQueue(f.books, f.sync, {}, { error("must preserve local changes") })
            assertNull(queue.download(remote, null, true, true) { _, _ -> })
            assertEquals(2, f.sync.allPending().size)
        }
    }

    @Test fun foregroundHandoffsAndCancellationPropagateWithoutAcknowledgingIncompleteBooks() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val id = cloudEntries(f, 1).single()
            val key = "books/$id/source"
            val remote = mapOf(key to remoteTestObject(key))
            listOf(CloudSyncYieldedException(), CancellationException("stopped")).forEach { interruption ->
                val queue = CloudBookSourceQueue(f.books, f.sync, {}, { throw interruption })
                try {
                    queue.download(remote, null, true, true) { _, _ -> }?.let { throw it }
                    fail("the worker must yield, not report success")
                } catch (actual: Exception) {
                    // Coroutine stack-trace recovery may copy the throwable across async scopes.
                    assertEquals(interruption.javaClass, actual.javaClass)
                    assertEquals(interruption.message, actual.message)
                }
                assertEquals(key, f.sync.remoteInbox().single().objectKey)
                assertNull(f.sync.objectState(key))
            }
        }
    }

    private suspend fun cloudEntries(f: RemoteApplyFixture, count: Int): List<String> = List(count) {
        UUID.randomUUID().toString().also { id ->
            f.books.insertBook(com.kixyu9527.kixyubook.core.database.entity.BookEntity(
                id, "Cloud", "", "", null, "TXT", "google-drive://$id", "", 1, "hash-$id", ""))
        }
    }

    /** A fake transport/installer: real Room queue, shelf, chapters and child restoration. */
    private suspend fun install(f: RemoteApplyFixture, id: String, remote: Map<String, DriveObject>) {
        val source = File(f.context.cacheDir, "auto-test-$id.txt").apply { writeText("正文") }
        f.books.insertChapter(ChapterEntity(bookUuid = id, title = "正文", chapterIndex = 0, chapterKey = "first"))
        val key = "books/$id/source"
        val info = remote.getValue(key)
        f.database.withTransaction {
            f.books.installBookSource(id, source.path, null)
            f.sync.upsertObjectState(SyncObjectStateEntity(key, info.id, "hash", 0, info.modifiedAt, info.version))
            f.sync.consumeRemoteChange(key)
        }
    }
}
