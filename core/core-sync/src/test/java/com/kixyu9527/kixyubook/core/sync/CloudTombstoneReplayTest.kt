package com.kixyu9527.kixyubook.core.sync

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudTombstoneReplayTest {
    @Test fun aFreshDeviceDoesNotDownloadHundredsOfIrrelevantOldDeletions() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val remote = (1..683).associate {
                val key = "tombstones/book/${UUID.randomUUID()}"
                key to remoteTestObject(key)
            }.toMutableMap()
            var downloads = 0
            val pipeline = f.pipeline(tombstoneDownload = { _, _ -> downloads++; error("no body is needed") })
            pipeline.applyRemoteTombstones("test", remote)
            assertEquals(0, downloads)
            assertEquals(683, f.sync.allObjectStates().size)
            assertTrue(f.sync.remoteInbox().isEmpty())
            // Even a full listing after a failed run must not replay applied deletions.
            pipeline.applyRemoteTombstones("test", remote)
            assertEquals(0, downloads)
        }
    }

    @Test fun anAppliedDeletionIsNotDownloadedAgainWhenTheGlobalCursorDidNotAdvance() = runBlocking<Unit>(Dispatchers.IO) {
        RemoteApplyFixture().use { f ->
            val id = UUID.randomUUID().toString()
            val key = "tombstones/session/$id"
            val remote = mutableMapOf(key to remoteTestObject(key))
            val known = remote + ("sessions/$id" to remoteTestObject("sessions/$id"))
            var downloads = 0
            val pipeline = f.pipeline(tombstoneDownload = { _, _ ->
                downloads++
                JSONObject().put("type", "SESSION").put("entityId", id).put("deletedAt", 1)
            })
            pipeline.applyRemoteTombstones("test", remote, known)
            pipeline.applyRemoteTombstones("test", remote, known)
            assertEquals(1, downloads)
            pipeline.applyRemoteTombstones("test", mutableMapOf(key to remoteTestObject(key, 2)), known)
            assertEquals("a genuinely newer deletion must still be read", 2, downloads)
        }
    }

    @Test fun successesInAFailedBatchAreCheckpointedAndNotRedownloadedOnRetry() = runBlocking<Unit> {
        val entries = (1..8).map { remoteTestObject("tombstones/session/$it") }
        val applied = mutableSetOf<String>()
        val downloaded = mutableListOf<String>()
        var fail = true
        suspend fun run() = replayRemoteTombstones(entries,
            needsPayload = { it.objectKey !in applied },
            download = {
                downloaded += it.objectKey
                if (it == entries[2] && fail) throw DriveHttpException(401, "expired")
                JSONObject()
            },
            apply = { _, _ -> },
            acknowledge = { applied += it.objectKey })
        assertTrue(runCatching { run() }.isFailure)
        assertEquals(setOf(entries[0].objectKey, entries[1].objectKey, entries[3].objectKey), applied)
        fail = false
        run()
        assertEquals(8, applied.size)
        assertEquals(1, downloaded.count { it == entries[0].objectKey })
        assertEquals(2, downloaded.count { it == entries[2].objectKey })
    }

    @Test fun deletionNetworkConcurrencyIsBoundedButNotSerial() = runBlocking<Unit> {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        replayRemoteTombstones((1..12).map { remoteTestObject("tombstones/session/$it") },
            needsPayload = { true },
            download = {
                val count = active.incrementAndGet()
                peak.updateAndGet { before -> maxOf(before, count) }
                try { delay(10); JSONObject() } finally { active.decrementAndGet() }
            }, apply = { _, _ -> }, acknowledge = {})
        assertTrue("expected overlapping network requests", peak.get() > 1)
        assertTrue(peak.get() <= 4)
    }
}
