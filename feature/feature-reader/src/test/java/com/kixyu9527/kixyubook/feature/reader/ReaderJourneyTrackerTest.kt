package com.kixyu9527.kixyubook.feature.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderJourneyTrackerTest {
    @Test fun measuresThroughReadyAndIgnoresStaleFrames() {
        var now = 100L
        val reports = mutableListOf<Triple<ReaderLocationSource, Long, String>>()
        val tracker = ReaderJourneyTracker({ now }) { source, time, outcome -> reports += Triple(source, time, outcome) }
        tracker.begin(ReaderLocationSource.SEARCH, 4, 8)
        now = 125
        tracker.rendered(3, 9)
        tracker.rendered(4, 8)
        assertTrue(reports.isEmpty())
        now = 150
        tracker.rendered(4, 9)
        tracker.rendered(4, 9)
        assertEquals(listOf(Triple(ReaderLocationSource.SEARCH, 50L, "ready")), reports)
    }

    @Test fun supersededAndFailedJumpsAreNotCountedAsSuccessfulLatency() {
        val reports = mutableListOf<String>()
        val tracker = ReaderJourneyTracker({ 100 }) { _, _, outcome -> reports += outcome }
        tracker.begin(ReaderLocationSource.BOOKMARK, 1, 0)
        tracker.begin(ReaderLocationSource.ANNOTATION, 2, 0)
        tracker.failed(1)
        tracker.failed(2)
        tracker.finish()
        assertEquals(listOf("superseded", "failed"), reports)
    }
}
