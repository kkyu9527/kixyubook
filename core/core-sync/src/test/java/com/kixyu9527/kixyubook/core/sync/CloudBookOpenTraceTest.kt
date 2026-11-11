package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.model.*
import org.junit.Assert.*
import org.junit.Test

class CloudBookOpenTraceTest {
    @Test fun aStalledStageAndByteProgressHaveCorrelatedPrivacySafeDiagnostics() {
        var time = 0L
        val progress = mutableListOf<BookPreparationProgress>()
        val entries = mutableListOf<Triple<Long, String?, Map<String, Any?>>>()
        val trace = CloudBookOpenTrace("12345678-private-book-identity", progress::add, { time }) { elapsed, result, fields ->
            entries += Triple(elapsed, result, fields)
        }
        assertEquals(BookPreparationStage.WAITING_SYNC, progress.first().stage)
        trace.describe(BookFormat.EPUB, 4_000, false, 0)
        assertEquals("EPUB", entries.last().third["format"])
        assertEquals(4_000L, entries.last().third["totalBytes"])
        assertEquals(false, entries.last().third["localFile"])
        assertEquals(0, entries.last().third["directoryChapters"])
        assertEquals(false, entries.last().third["background"])
        time = 5_000
        trace.heartbeat()
        assertEquals(5_000L, entries.last().first)
        assertEquals("WAITING_SYNC", entries.last().third["openStage"])
        trace.update(BookPreparationProgress(BookPreparationStage.DOWNLOADING, 1_000, 4_000))
        time += 10_000
        trace.heartbeat()
        trace.retry(2, 503)
        assertEquals(2, entries.last().third["attempt"])
        assertEquals(503, entries.last().third["statusCode"])
        assertEquals(1_000L, entries.last().third["receivedBytes"])
        assertEquals(4_000L, entries.last().third["totalBytes"])
        assertEquals(1, entries.map { it.third["run"] }.distinct().size)
        assertTrue(entries.all { it.third["book"] == "12345678" })
        assertFalse(entries.toString().contains("private-book-identity"))
    }

    @Test fun automaticDownloadsAreDistinguishableFromReaderOpens() {
        val entries = mutableListOf<Map<String, Any?>>()
        CloudBookOpenTrace("book", {}, clock = { 0L }, background = true, record = { _, _, fields -> entries += fields })
        assertTrue(entries.all { it["background"] == true })
    }

    @Test fun progressUpdatesAreThrottledButStageChangesAndFailuresAreNeverHidden() {
        var now = 0L
        val updates = mutableListOf<BookPreparationProgress>()
        val results = mutableListOf<String?>()
        val trace = CloudBookOpenTrace("book", updates::add, { now }) { _, outcome, _ -> results += outcome }
        repeat(1_000) { trace.update(BookPreparationProgress(BookPreparationStage.DOWNLOADING, it.toLong(), 1_000)) }
        assertEquals(2, updates.size)
        now = 300
        trace.update(BookPreparationProgress(BookPreparationStage.VERIFYING))
        assertEquals(BookPreparationStage.VERIFYING, updates.last().stage)
        trace.finish(java.io.IOException("network disconnected"))
        assertEquals("io_error", results.last())
    }
}
