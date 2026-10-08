package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.database.KixyuDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
class CloudRemoteInboxTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun receivedObjectsSurviveDatabaseReopenAndRemovedDriveFilesCannotReplay() = runBlocking(Dispatchers.IO) {
        val context: Context = RuntimeEnvironment.getApplication()
        val file = folder.newFile("sync.db")
        fun open() = Room.databaseBuilder(context, KixyuDatabase::class.java, file.absolutePath).build()
        val key = "progress/book"
        var database = open()
        CloudRemoteInbox(database, database.syncDao()).receive(mapOf(key to remoteTestObject(key)))
        database.close()
        database = open()
        try {
            val inbox = CloudRemoteInbox(database, database.syncDao())
            assertEquals(key, inbox.receive(emptyMap()).keys.single())
            assertEquals(2L, inbox.receive(mapOf(key to remoteTestObject(key, 2))).getValue(key).version)
            database.syncDao().removeRemoteFile("id-$key")
            assertTrue(inbox.pending().isEmpty())
        } finally { database.close() }
    }
}
