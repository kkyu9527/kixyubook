package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncObjectStateEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncTombstoneEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteTombstoneTransactionTest {
    private fun database(): KixyuDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication() as Context,
        KixyuDatabase::class.java,
    ).build()

    @Test
    fun anEditThatLandsWhileTheTombstoneRunsIsNotDiscarded() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val syncDao = database.syncDao()
            val checkReached = CompletableDeferred<Unit>()
            val editStarted = CompletableDeferred<Unit>()

            val edit = launch {
                editStarted.complete(Unit)
                // The edit only gets a writer connection after the tombstone transaction commits.
                withTimeoutOrNull(2_000) { checkReached.await() }
                syncDao.upsertOutbox(
                    SyncOutboxEntity("m", SyncEntityType.ANNOTATION.name, "a1", "UPSERT", 1, 1, "device"),
                )
            }

            val applied = applyRemoteTombstoneAtomically(
                database = database,
                syncDao = syncDao,
                type = SyncEntityType.ANNOTATION,
                id = "a1",
                tombstone = SyncTombstoneEntity("tombstones/a1", 1, "device", Long.MAX_VALUE),
            ) {
                checkReached.complete(Unit)
                // Hold the transaction open long enough for the racing edit to try to interleave.
                delay(400)
            }
            edit.join()

            assertTrue(applied)
            assertEquals(
                "an edit racing the tombstone must survive to be pushed again",
                1,
                syncDao.pendingCount(SyncEntityType.ANNOTATION.name, "a1"),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun aBookWithAnUnsyncedNoteIsNotDeletedByTheRemoteTombstone() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val syncDao = database.syncDao()
            syncDao.upsertOutbox(
                SyncOutboxEntity("m", SyncEntityType.ANNOTATION.name, "note-1", "UPSERT", 1, 1, "device"),
            )
            var deleted = false

            val applied = applyRemoteTombstoneAtomically(
                database = database,
                syncDao = syncDao,
                type = SyncEntityType.BOOK,
                id = "book-1",
                tombstone = SyncTombstoneEntity("tombstones/book-1", 1, "device", Long.MAX_VALUE),
                hasLocalConflict = {
                    hasRemoteTombstoneConflict(
                        type = SyncEntityType.BOOK,
                        id = "book-1",
                        syncDao = syncDao,
                        annotationUuidsForBook = { listOf("note-1") },
                        correctionUuidsForBook = { emptyList() },
                    )
                },
            ) { deleted = true }

            assertFalse("the cascade would discard the unsynced note", applied)
            assertFalse(deleted)
            assertEquals(1, syncDao.pendingCount(SyncEntityType.ANNOTATION.name, "note-1"))
        } finally {
            database.close()
        }
    }

    @Test
    fun aPendingBookmarkAlsoBlocksABookTombstone() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val syncDao = database.syncDao()
            syncDao.upsertOutbox(
                SyncOutboxEntity("m", SyncEntityType.BOOKMARKS.name, "book-1", "UPSERT", 1, 1, "device"),
            )
            var deleted = false

            val applied = applyRemoteTombstoneAtomically(
                database = database,
                syncDao = syncDao,
                type = SyncEntityType.BOOK,
                id = "book-1",
                tombstone = SyncTombstoneEntity("tombstones/book-1", 1, "device", Long.MAX_VALUE),
                hasLocalConflict = {
                    hasRemoteTombstoneConflict(
                        type = SyncEntityType.BOOK,
                        id = "book-1",
                        syncDao = syncDao,
                        annotationUuidsForBook = { emptyList() },
                        correctionUuidsForBook = { emptyList() },
                    )
                },
            ) { deleted = true }

            assertFalse(applied)
            assertFalse(deleted)
            assertEquals(1, syncDao.pendingCount(SyncEntityType.BOOKMARKS.name, "book-1"))
        } finally {
            database.close()
        }
    }

    @Test
    fun aBookWithoutAnyRelatedPendingChangeIsStillDeleted() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val syncDao = database.syncDao()
            syncDao.upsertOutbox(
                SyncOutboxEntity("m", SyncEntityType.ANNOTATION.name, "other-note", "UPSERT", 1, 1, "device"),
            )
            var deleted = false

            val applied = applyRemoteTombstoneAtomically(
                database = database,
                syncDao = syncDao,
                type = SyncEntityType.BOOK,
                id = "book-1",
                tombstone = SyncTombstoneEntity("tombstones/book-1", 1, "device", Long.MAX_VALUE),
                hasLocalConflict = {
                    hasRemoteTombstoneConflict(
                        type = SyncEntityType.BOOK,
                        id = "book-1",
                        syncDao = syncDao,
                        annotationUuidsForBook = { emptyList() },
                        correctionUuidsForBook = { emptyList() },
                    )
                },
            ) { deleted = true }

            assertTrue(applied)
            assertTrue(deleted)
            assertEquals(1, syncDao.pendingCount(SyncEntityType.ANNOTATION.name, "other-note"))
        } finally {
            database.close()
        }
    }

    @Test
    fun aConflictingBookTombstoneRunsTheLocalWinsRepairBeforeRecordingIt() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val syncDao = database.syncDao()
            syncDao.upsertOutbox(
                SyncOutboxEntity("m", SyncEntityType.ANNOTATION.name, "note-1", "UPSERT", 1, 1, "device"),
            )
            var reenqueued = false

            val applied = applyRemoteTombstoneAtomically(
                database = database,
                syncDao = syncDao,
                type = SyncEntityType.BOOK,
                id = "book-1",
                tombstone = SyncTombstoneEntity("tombstones/book-1", 1, "device", Long.MAX_VALUE),
                hasLocalConflict = { true },
                onConflict = { reenqueued = true },
            ) { error("the conflicting book must not be deleted") }

            assertFalse(applied)
            assertTrue("local wins must be closed inside the same transaction", reenqueued)
            assertEquals(1, syncDao.pendingCount(SyncEntityType.ANNOTATION.name, "note-1"))
        } finally {
            database.close()
        }
    }

    @Test
    fun anObjectReuploadedAfterTheRemoteDeletionMakesTheTombstoneStale() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val syncDao = database.syncDao()
            syncDao.upsertObjectState(
                SyncObjectStateEntity(
                    objectKey = "books/book-1/metadata",
                    driveFileId = "file",
                    localHash = "hash",
                    localChangedAt = 0,
                    remoteModifiedAt = 200,
                    remoteVersion = 2,
                ),
            )

            assertTrue(
                tombstoneSupersededByLocalUpload(
                    syncDao,
                    SyncEntityType.BOOK,
                    "book-1",
                    deletedAt = 100,
                ),
            )
            assertFalse(
                "a deletion newer than the local upload still applies",
                tombstoneSupersededByLocalUpload(
                    syncDao,
                    SyncEntityType.BOOK,
                    "book-1",
                    deletedAt = 300,
                ),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun aMutableObjectMapsToTheTombstoneThatGuardsIt() {
        assertEquals("tombstones/book/b1", tombstoneKeyForObjectKey("books/b1/metadata"))
        assertEquals("tombstones/book/b1", tombstoneKeyForObjectKey("books/b1/source"))
        assertEquals("tombstones/annotation/a1", tombstoneKeyForObjectKey("annotations/a1"))
        assertEquals("tombstones/progress/b1", tombstoneKeyForObjectKey("progress/b1"))
        assertEquals("tombstones/settings/global", tombstoneKeyForObjectKey("settings/global"))
        assertNull(tombstoneKeyForObjectKey("tombstones/book/b1"))
    }

    @Test
    fun aStaleTombstoneCanBeRemovedWhenTheObjectIsRecreated() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val syncDao = database.syncDao()
            syncDao.upsertTombstone(
                SyncTombstoneEntity("tombstones/book/b1", 100, "device", Long.MAX_VALUE),
            )
            assertEquals(100, syncDao.tombstone("tombstones/book/b1")!!.deletedAt)

            syncDao.deleteTombstone("tombstones/book/b1")

            assertNull(syncDao.tombstone("tombstones/book/b1"))
        } finally {
            database.close()
        }
    }

    @Test
    fun aPendingEditSkipsTheTombstoneEntirely() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            val syncDao = database.syncDao()
            syncDao.upsertOutbox(
                SyncOutboxEntity("m", SyncEntityType.ANNOTATION.name, "a1", "UPSERT", 1, 1, "device"),
            )
            var deleted = false

            val applied = applyRemoteTombstoneAtomically(
                database = database,
                syncDao = syncDao,
                type = SyncEntityType.ANNOTATION,
                id = "a1",
                tombstone = SyncTombstoneEntity("tombstones/a1", 1, "device", Long.MAX_VALUE),
            ) {
                deleted = true
            }

            assertFalse(applied)
            assertFalse(deleted)
            assertEquals(1, syncDao.pendingCount(SyncEntityType.ANNOTATION.name, "a1"))
        } finally {
            database.close()
        }
    }
}
