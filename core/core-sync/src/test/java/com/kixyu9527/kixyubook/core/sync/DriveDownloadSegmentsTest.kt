package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.model.BookPreparationProgress
import com.kixyu9527.kixyubook.core.common.model.BookPreparationStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveDownloadSegmentsTest {
    @Test
    fun unknownOrSmallFilesStayOnTheSingleStreamPath() {
        assertEquals(listOf(DownloadSegment(0, 0L, 0L)), planDownloadSegments(0L))
        assertEquals(listOf(DownloadSegment(0, 0L, 512L * 1024L)), planDownloadSegments(512L * 1024L))
    }

    @Test
    fun largeFilesSplitIntoContiguousRangesThatCoverEveryByte() {
        val size = 10L * 1024L * 1024L
        val segments = planDownloadSegments(size)
        assertEquals(MAX_DOWNLOAD_SEGMENTS, segments.size)
        assertEquals(0L, segments.first().start)
        assertEquals(size, segments.last().endExclusive)
        segments.zipWithNext().forEach { (left, right) ->
            assertEquals(left.endExclusive, right.start)
            assertTrue(left.length >= MIN_PARALLEL_DOWNLOAD_SEGMENT_BYTES)
        }
        assertEquals(size, segments.sumOf { it.length })
    }

    @Test
    fun remainderBytesAreDistributedWithoutGapsOrOverlap() {
        val size = 8L * 1024L * 1024L + 3L
        val segments = planDownloadSegments(size)
        assertEquals(size, segments.sumOf { it.length })
        assertEquals(3, segments.count { it.length == segments.first().length })
        assertTrue(segments.drop(3).all { it.length == segments.first().length - 1L })
        assertEquals(0L, segments.first().start)
        assertEquals(size, segments.last().endExclusive)
        segments.zipWithNext().forEach { (left, right) -> assertEquals(left.endExclusive, right.start) }
    }

    @Test
    fun requestedSegmentCapIsRespected() {
        assertEquals(1, planDownloadSegments(64L * 1024L * 1024L, maxSegments = 1).size)
        assertEquals(2, planDownloadSegments(64L * 1024L * 1024L, maxSegments = 2).size)
        assertEquals(MAX_DOWNLOAD_SEGMENTS, planDownloadSegments(64L * 1024L * 1024L).size)
    }

    @Test
    fun persistedTransferStatesMapToTheClosedStatusSet() {
        CloudBookDownloadStatus.entries.forEach { status ->
            assertEquals(status, CloudBookDownloadStatus.fromPersisted(status.name))
        }
        assertEquals(CloudBookDownloadStatus.PENDING, CloudBookDownloadStatus.fromPersisted(null))
        assertEquals(CloudBookDownloadStatus.PENDING, CloudBookDownloadStatus.fromPersisted("UNKNOWN"))
    }

    @Test
    fun onlyKnownByteTotalsExposeAProgressFraction() {
        val task = CloudBookDownload(
            bookUuid = "book",
            title = "Book",
            status = CloudBookDownloadStatus.RUNNING,
            attempts = 1,
            progress = BookPreparationProgress(BookPreparationStage.DOWNLOADING, 25, 100),
        )
        assertEquals(0.25f, task.fraction!!, 0.0001f)
        assertEquals(null, task.copy(progress = BookPreparationProgress(
            BookPreparationStage.DOWNLOADING, 25, 0,
        )).fraction)
    }
}
