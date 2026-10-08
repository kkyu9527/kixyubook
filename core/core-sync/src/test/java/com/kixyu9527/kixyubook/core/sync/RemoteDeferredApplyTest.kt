package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteDeferredApplyTest {
    @Test fun childObjectsSurviveCursorAdvancementAndApplyAfterTheBookAppears() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val book = UUID.randomUUID().toString()
            val annotation = UUID.randomUUID().toString()
            val correction = UUID.randomUUID().toString()
            val session = UUID.randomUUID().toString()
            val bookmark = UUID.randomUUID().toString()
            f.payloads["annotations/$annotation"] = JSONObject().put("uuid", annotation).put("bookUuid", book)
                .put("exactText", "正文").put("endOffset", 2).put("updatedTime", 2)
            f.payloads["corrections/$correction"] = JSONObject().put("uuid", correction).put("bookUuid", book)
                .put("exactText", "正文").put("endOffset", 2).put("replacementText", "修正").put("updatedTime", 2)
            f.payloads["sessions/$session"] = JSONObject().put("uuid", session).put("bookUuid", book)
                .put("durationMillis", 200).put("epochDay", 1)
            f.payloads["progress/$book"] = JSONObject().put("bookUuid", book).put("chapterKey", "first")
                .put("paragraphIndex", 3).put("charOffset", 7).put("progression", .5).put("updatedTime", 2)
            f.payloads["bookmarks/$book"] = JSONObject().put("bookUuid", book).put("items", JSONArray().put(
                JSONObject().put("uuid", bookmark).put("chapterKey", "first").put("paragraphIndex", 3).put("preview", "正文")))
            val metadataKey = "books/$book/metadata"
            val sourceKey = "books/$book/source"
            val delayedRestore = object : CloudRemoteObjectApplier by f.applier {
                override suspend fun restoreBook(token: String, uuid: String, knownRemote: Map<String, DriveObject>): Boolean {
                    if (sourceKey !in knownRemote || metadataKey !in knownRemote) return false
                    if (!f.books.bookExists(uuid)) f.addBook(uuid)
                    return true
                }
            }
            val changes = (f.payloads.keys + metadataKey).associateWith(::remoteTestObject)
            f.pipeline(delayedRestore).applyRemoteChanges("test", changes, changes, true, book) {}
            assertEquals(changes.keys, f.sync.remoteInbox().map { it.objectKey }.toSet())
            assertTrue("receipt must not become an applied baseline", f.sync.allObjectStates().isEmpty())

            // Only the source arrives next. The metadata and all children must come from Room,
            // not from a repeated Drive event or an in-memory retry list.
            val sourceChange = mapOf(sourceKey to remoteTestObject(sourceKey))
            f.pipeline(delayedRestore).applyRemoteChanges("test", sourceChange, sourceChange, true, book) {}
            assertEquals(7, f.books.getProgress(book)!!.charOffset)
            assertEquals(bookmark, f.books.getBookmarks(book).single().uuid)
            assertEquals(annotation, f.annotations.getBookAnnotations(book).single().uuid)
            assertEquals(correction, f.corrections.getBookCorrections(book).single().uuid)
            assertNotNull(f.books.getSessionBySyncUuid(session))
            assertTrue(f.sync.remoteInbox().isEmpty())
            assertEquals(changes.keys + sourceKey, f.sync.allObjectStates().map { it.objectKey }.toSet())
        }
    }

    @Test fun aDeferredFontPairDoesNotFallThroughAndAdvanceItsBaseline() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val font = UUID.randomUUID().toString()
            val changes = listOf("fonts/$font/metadata", "fonts/$font/source").associateWith(::remoteTestObject)
            f.preferences.setSyncFonts(false)
            f.pipeline().applyRemoteChanges("test", changes, changes, true, null) {}
            assertTrue(f.sync.allObjectStates().isEmpty())
            assertEquals(2, f.sync.remoteInbox().size)
        }
    }

    @Test fun aLocalUploadSupersedesAnOlderQueuedRemoteObject() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val book = UUID.randomUUID().toString()
            val key = "progress/$book"
            CloudRemoteInbox(f.database, f.sync).receive(mapOf(key to remoteTestObject(key)))
            f.sync.upsertObjectState(com.kixyu9527.kixyubook.core.database.entity.SyncObjectStateEntity(key, "new", "hash", 2, 2, 2))
            f.pipeline().applyRemoteChanges("test", emptyMap(), emptyMap(), true, null) {}
            assertTrue(f.sync.remoteInbox().isEmpty())
        }
    }

    @Test fun aPendingLocalEditKeepsTheRemoteObjectQueuedWithoutOverwritingTheNote() = runBlocking(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val id = UUID.randomUUID().toString()
            f.sync.upsertOutbox(SyncOutboxEntity("local", SyncEntityType.ANNOTATION.name, id, "UPSERT", 2, 2, "device"))
            val key = "annotations/$id"
            f.pipeline().applyRemoteChanges("test", mapOf(key to remoteTestObject(key)), emptyMap(), true, null) {}
            assertEquals(key, f.sync.remoteInbox().single().objectKey)
            assertEquals(1, f.sync.allPending().size)
            assertTrue(f.sync.allObjectStates().isEmpty())
        }
    }
}
