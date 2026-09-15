package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncTombstoneEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
