package com.kixyu9527.kixyubook.core.sync

import androidx.room.Room
import android.content.Context
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudReadingPayloadsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun aPositionReceivedBeforeTheBookSurvivesProcessRestartAndNeedsNoSecondDownload() = runBlocking(Dispatchers.IO) {
        val context: Context = RuntimeEnvironment.getApplication()
        val file = folder.newFile("anchors.db")
        fun open() = Room.databaseBuilder(context, KixyuDatabase::class.java, file.path).build()
        var db = open()
        val info = remoteTestObject("progress/book")
        CloudRemoteInbox(db, db.syncDao()).receive(mapOf(info.objectKey to info))
        CloudReadingPayloads(db.syncDao()).read(info) { JSONObject().put("charOffset", 419) }
        assertNull(db.syncDao().objectState(info.objectKey))
        db.close()
        db = open()
        try {
            val position = CloudReadingPayloads(db.syncDao()).read(info) { error("must not download again") }
            assertEquals(419, position.getInt("charOffset"))
            assertEquals(1, db.syncDao().remoteInbox().size)
            assertNull("receiving an anchor is not applying it", db.syncDao().objectState(info.objectKey))
        } finally { db.close() }
    }

    @Test fun repeatedReceiptsKeepPayloadAndTransferStateButANewerRevisionInvalidatesBoth() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val key = "bookmarks/book"
            val info = remoteTestObject(key)
            val inbox = CloudRemoteInbox(f.database, f.sync)
            inbox.receive(mapOf(key to info))
            CloudReadingPayloads(f.sync).read(info) { JSONObject().put("items", "old") }
            f.sync.updateBookTransfer(key, "FAILED", "network", 1)
            inbox.receive(mapOf(key to info))
            assertNotNull(f.sync.receivedObject(key)!!.payloadJson)
            assertEquals("FAILED", f.sync.receivedObject(key)!!.transferState)
            assertEquals(1, f.sync.receivedObject(key)!!.transferAttempts)
            val newer = remoteTestObject(key, 2)
            inbox.receive(mapOf(key to newer))
            val changed = f.sync.receivedObject(key)!!
            assertNull(changed.payloadJson)
            assertEquals("PENDING", changed.transferState)
            assertEquals(0, changed.transferAttempts)
            assertEquals("new", CloudReadingPayloads(f.sync).read(newer) { JSONObject().put("items", "new") }.getString("items"))
        }
    }

    @Test fun aLatePayloadCannotOverwriteANewerReceivedRevision() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val key = "progress/book"
            val old = remoteTestObject(key)
            val newer = remoteTestObject(key, 2)
            val inbox = CloudRemoteInbox(f.database, f.sync)
            inbox.receive(mapOf(key to old))
            CloudReadingPayloads(f.sync).read(old) {
                inbox.receive(mapOf(key to newer))
                JSONObject().put("charOffset", 10)
            }
            assertEquals(2L, f.sync.receivedObject(key)!!.version)
            assertNull(f.sync.receivedObject(key)!!.payloadJson)
            val latest = CloudReadingPayloads(f.sync).read(newer) { JSONObject().put("charOffset", 20) }
            assertEquals(20, latest.getInt("charOffset"))
        }
    }
}
