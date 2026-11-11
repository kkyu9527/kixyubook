package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.database.entity.ChapterEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudBookTransferServiceTest {
    @Test fun aCloudOnlyBookWithoutAuthorizationReportsAnActionableFailureInsteadOfSilentCancellation() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val setup = Setup(f)
            setup.initialize()
            f.preferences.clearAccount()
            val result = setup.service.ensure(setup.uuid, background = false)
            assertTrue(result.exceptionOrNull() is CloudSyncEngine.AuthorizationRequiredException)
            assertEquals(0, setup.binaryDownloads.get())
            assertEquals("", f.books.getBook(setup.uuid)!!.storagePath)
        }
    }

    @Test fun binaryNetworkDoesNotHoldTheMetadataLockAndReaderOpensJoinItWithoutRedownloading() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val setup = Setup(f)
            setup.initialize()
            setup.cacheReadingData()
            f.payloads.clear() // The install must restore the durable snapshots, not fetch again.
            val background = async { setup.service.ensure(setup.uuid, background = true) }
            try {
                withTimeout(5_000) { setup.entered.await() }
                // A metadata sync or account operation can still enter while bytes are stalled.
                withTimeout(2_000) { setup.gate.mutex.withLock { assertTrue(true) } }
                val foreground = async {
                    setup.foreground.prioritize(setup.uuid) { setup.service.ensure(setup.uuid, background = false) }
                }
                yield()
                setup.release.complete(Unit)
                assertTrue(background.await().isSuccess)
                assertTrue(foreground.await().isSuccess)
                assertEquals(1, setup.binaryDownloads.get())
                assertEquals(0, setup.anchorDownloads.get())
                assertEquals(7, f.books.getProgress(setup.uuid)!!.charOffset)
                assertEquals(1, f.books.getBookmarks(setup.uuid).size)
                assertTrue(File(f.books.getBook(setup.uuid)!!.storagePath).isFile)
                assertTrue(f.sync.remoteInbox().isEmpty())
            } finally { setup.release.complete(Unit); background.cancelAndJoin(); f.preferences.clearAccount() }
        }
    }

    @Test fun changingAccountWhileBytesAreDownloadingRejectsTheOldCommitWithoutWaitingForTheFile() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val setup = Setup(f)
            setup.initialize()
            setup.cacheReadingData()
            val download = async { runCatching { setup.service.ensure(setup.uuid, background = true) } }
            try {
                withTimeout(5_000) { setup.entered.await() }
                withTimeout(2_000) { setup.gate.commitAuthorization(SyncAccount("another-account", "b@example.com", "B", null)) {} }
                setup.release.complete(Unit)
                val result = download.await()
                assertTrue(result.exceptionOrNull() is CloudAccountChangedException)
                assertEquals("", f.books.getBook(setup.uuid)!!.storagePath)
                assertEquals(0, f.books.getChapterCount(setup.uuid))
                assertTrue(f.sync.remoteInbox().isEmpty())
                assertTrue(f.sync.allObjectStates().isEmpty())
            } finally { setup.release.complete(Unit); download.cancelAndJoin(); f.preferences.clearAccount() }
        }
    }

    @Test fun aDeletedBookCannotReappearAfterItsDownloadCompletes() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val setup = Setup(f)
            setup.initialize()
            setup.cacheReadingData()
            val downloading = async { setup.service.ensure(setup.uuid, background = true) }
            try {
                withTimeout(5_000) { setup.entered.await() }
                f.books.deleteBook(setup.uuid)
                setup.release.complete(Unit)
                assertTrue(downloading.await().isFailure)
                assertFalse(f.books.bookExists(setup.uuid))
                assertEquals(0, f.books.getChapterCount(setup.uuid))
            } finally { setup.release.complete(Unit); downloading.cancelAndJoin(); f.preferences.clearAccount() }
        }
    }

    @Test fun aFailedTransferKeepsItsReceiptAndCanRetryIndependently() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val setup = Setup(f)
            setup.initialize()
            setup.cacheReadingData()
            setup.fail = true
            setup.release.complete(Unit)
            try {
                assertTrue(setup.service.ensure(setup.uuid, background = true).isFailure)
                val task = f.sync.receivedObject(setup.source.objectKey)!!
                assertEquals("FAILED", task.transferState)
                assertEquals(1, task.transferAttempts)
                assertNull(f.sync.objectState(setup.source.objectKey))
                setup.fail = false
                assertTrue(setup.service.ensure(setup.uuid, background = false).isSuccess)
                assertNull(f.sync.receivedObject(setup.source.objectKey))
                assertNotNull(f.sync.objectState(setup.source.objectKey))
            } finally { f.preferences.clearAccount() }
        }
    }

    @Test fun aReadingSnapshotFailureDoesNotMakeTheNextRetryDownloadTheCompletedBinaryAgain() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val setup = Setup(f)
            setup.initialize()
            setup.cacheReadingData()
            val key = "progress/${setup.uuid}"
            f.sync.retainRemoteChanges(listOf(f.sync.receivedObject(key)!!.copy(payloadJson = null)))
            setup.failAnchors = true
            setup.release.complete(Unit)
            try {
                assertTrue(setup.service.ensure(setup.uuid, background = true).isFailure)
                assertTrue(File(f.books.getBook(setup.uuid)!!.storagePath).isFile)
                assertEquals(1, setup.binaryDownloads.get())
                assertNotNull(f.sync.receivedObject(setup.source.objectKey))
                setup.failAnchors = false
                assertTrue(setup.service.ensure(setup.uuid, background = false).isSuccess)
                assertEquals(1, setup.binaryDownloads.get())
                assertEquals(7, f.books.getProgress(setup.uuid)!!.charOffset)
            } finally { f.preferences.clearAccount() }
        }
    }

    private class Setup(val f: RemoteApplyFixture) {
        val uuid = UUID.randomUUID().toString()
        val gate = CloudSyncAccountGate(f.database, f.preferences)
        val foreground = CloudForegroundRequests()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val binaryDownloads = AtomicInteger()
        val anchorDownloads = AtomicInteger()
        var fail = false
        var failAnchors = false
        val source = remoteTestObject("books/$uuid/source").copy(size = 42)
        private val repository = object : BookRepository by f.bookRepository {
            override suspend fun hydrateSyncedBook(book: SyncedBook, sourceFilePath: String): Boolean {
                if (!f.books.bookExists(uuid)) return false
                if (f.books.getChapterCount(uuid) > 0) return true
                val target = File(f.context.filesDir, "transfer-test-$uuid.txt").apply {
                    File(sourceFilePath).copyTo(this, overwrite = true)
                }
                f.books.insertChapter(ChapterEntity(bookUuid = uuid, title = "第一章", chapterIndex = 0, chapterKey = "first"))
                f.books.installBookSource(uuid, target.path, null)
                return true
            }
        }
        val service = CloudBookTransferService(f.context, f.database, repository, f.mutations, f.preferences,
            GoogleAccountClient(f.context, f.preferences, gate), gate, f.drive, f.applier, foreground,
            object : CloudBookTransferTransport {
                override suspend fun authorize() = "test-token"
                override suspend fun find(token: String, key: String): DriveObject? = null
                override suspend fun download(token: String, info: DriveObject, target: File, onProgress: (Long) -> Unit) {
                    if (info.objectKey == source.objectKey) {
                        binaryDownloads.incrementAndGet()
                        entered.complete(Unit)
                        release.await()
                        currentCoroutineContext()[CloudTransferLease]?.check?.invoke()
                        currentCoroutineContext()[BackgroundSyncCheckpoint]?.check?.invoke()
                        if (fail) throw IOException("offline")
                        target.writeText("第一章\n正文内容")
                        onProgress(42)
                    } else {
                        anchorDownloads.incrementAndGet()
                        if (failAnchors) throw IOException("anchor snapshot unavailable")
                        target.writeText(f.payloads.getValue(info.objectKey).toString())
                    }
                }
            })

        suspend fun initialize() {
            f.preferences.clearAccount()
            gate.commitAuthorization(SyncAccount("transfer-account", "a@example.com", "A", null)) {}
            f.preferences.approveInitialSync()
            f.bookRepository.registerSyncedBook(SyncedBook(uuid, "书籍", "作者", "", BookFormat.TXT, 1, "hash", ""))
            CloudRemoteInbox(f.database, f.sync).receive(mapOf(source.objectKey to source))
        }

        suspend fun cacheReadingData() {
            val progress = "progress/$uuid"
            val bookmarks = "bookmarks/$uuid"
            f.payloads[progress] = JSONObject().put("bookUuid", uuid).put("chapterKey", "first")
                .put("paragraphIndex", 3).put("charOffset", 7).put("progression", .5).put("updatedTime", 2)
            f.payloads[bookmarks] = JSONObject().put("bookUuid", uuid).put("items", JSONArray().put(
                JSONObject().put("uuid", UUID.randomUUID().toString()).put("chapterKey", "first").put("paragraphIndex", 3)))
            val received = listOf(progress, bookmarks).associateWith(::remoteTestObject)
            CloudRemoteInbox(f.database, f.sync).receive(received)
            // Exercise the real metadata/reading-data pipeline before the independent file lane.
            f.pipeline().applyRemoteChanges("test", received, received, true, null) {}
            assertEquals("", f.books.getBook(uuid)!!.storagePath)
            assertEquals(0, f.books.getChapterCount(uuid))
            assertNotNull(f.sync.receivedObject(progress)!!.payloadJson)
            assertNotNull(f.sync.receivedObject(bookmarks)!!.payloadJson)
        }
    }
}
