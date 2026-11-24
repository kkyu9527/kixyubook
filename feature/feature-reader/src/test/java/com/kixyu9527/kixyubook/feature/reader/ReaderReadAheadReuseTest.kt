package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderReadAheadReuseTest {
    @Test fun aDistantExplicitJumpCancelsObsoleteWorkInsteadOfRetainingTheOldWindow() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var obsoleteCancelled = false
        val harness = readerRecoveryHarness(chapterCount = 8, chapterLoader = { chapter, _ ->
            if (chapter.index == 1) {
                try { CompletableDeferred<Unit>().await() }
                finally { obsoleteCancelled = true }
            }
            ChapterContent(chapter, listOf(Paragraph(chapter.id, chapter.id, 0, "正文")))
        })
        try {
            val reader = harness.open()
            advanceUntilIdle()
            assertFalse(obsoleteCancelled)
            reader.jumpToChapter(6)
            advanceUntilIdle()
            assertTrue(obsoleteCancelled)
            assertEquals(6, reader.uiState.value.chapterIndex)
        } finally { harness.closeAllAndAwait(); Dispatchers.resetMain() }
    }
    @Test fun aForegroundJumpReusesTheReadAheadAlreadyParsingTheSameChapter() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<Pair<Int, ChapterLoadPriority>>()
        val harness = readerRecoveryHarness(chapterCount = 3, chapterLoader = { chapter, priority ->
            calls += chapter.index to priority
            if (chapter.index == 1) release.await()
            ChapterContent(chapter, listOf(Paragraph(chapter.id, chapter.id, 0, "正文")))
        })
        try {
            val reader = harness.open()
            advanceUntilIdle()
            assertEquals(1, calls.count { it.first == 1 })
            reader.jumpToChapter(1)
            runCurrent()
            assertEquals("Becoming current must not restart the same parse", 1, calls.count { it.first == 1 })
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, reader.uiState.value.chapterIndex)
            assertEquals(1, calls.count { it.first == 1 })
        } finally { harness.closeAllAndAwait(); Dispatchers.resetMain() }
    }

    @Test fun aCachedBoundaryHandoffKeepsTheSecondNeighbourInFlightAndReusesCompletedChapters() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<Pair<Int, ChapterLoadPriority>>()
        val harness = readerRecoveryHarness(chapterCount = 4, chapterLoader = { chapter, priority ->
            calls += chapter.index to priority
            if (chapter.index == 2) release.await()
            ChapterContent(chapter, listOf(Paragraph(chapter.id, chapter.id, 0, "正文")))
        })
        try {
            val reader = harness.open()
            advanceUntilIdle()
            reader.chapterRendered(reader.uiState.value.navigationVersion)
            advanceUntilIdle()
            assertEquals(listOf(ChapterLoadPriority.READ_AHEAD), calls.filter { it.first == 2 }.map { it.second })
            reader.moveChapter(1)
            runCurrent()
            assertEquals(1, reader.uiState.value.chapterIndex)
            assertEquals("Retain the next runway request across recentering", 1, calls.count { it.first == 2 })
            release.complete(Unit)
            advanceUntilIdle()
            repeat(8) {
                reader.moveChapter(1)
                runCurrent()
                reader.moveChapter(-1)
                runCurrent()
            }
            assertEquals(1, reader.uiState.value.chapterIndex)
            assertEquals("Prepared adjacent chapters must not be loaded on every alternating skip", 1, calls.count { it.first == 1 })
            assertEquals(1, calls.count { it.first == 2 })
        } finally { harness.closeAllAndAwait(); Dispatchers.resetMain() }
    }
}
