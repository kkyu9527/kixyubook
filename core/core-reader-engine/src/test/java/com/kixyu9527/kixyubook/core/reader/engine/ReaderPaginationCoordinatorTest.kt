package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.ui.unit.LayoutDirection
import com.kixyu9527.kixyubook.core.common.model.Paragraph
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaginationCoordinatorTest {
    @Test
    fun currentPaginationPublishesReadablePagesThenWaitsForAnimationBudget() = runBlocking {
        val chapter = ReaderChapter(
            id = 10,
            bookUuid = "book",
            title = "第一章",
            index = 0,
            paragraphs = listOf(Paragraph(1, 10, 0, "正文")),
        )
        val first = ReaderPage(0, 0, chapter.title, true, emptyList())
        val second = ReaderPage(1, 0, chapter.title, false, emptyList())
        val coordinator = ReaderPaginationCoordinator()
        coordinator.setPaused(true)

        try {
            val snapshots = coordinator.getOrLoad(key(chapter), chapter, prefetch = false) { publish, awaitPermit ->
                publish(listOf(first))
                awaitPermit()
                listOf(first, second)
            }

            val partial = withTimeout(1_000) { snapshots.first { it.pages.isNotEmpty() } }
            assertEquals(listOf(first), partial.pages)
            assertFalse(partial.isComplete)
            assertEquals(partial, coordinator.currentSnapshot(key(chapter)))
            delay(25)
            assertFalse(snapshots.value.isComplete)

            coordinator.setPaused(false)
            val complete = withTimeout(1_000) { snapshots.first { it.isComplete } }
            assertEquals(listOf(first, second), complete.pages)
            assertTrue(complete.isComplete)
            assertEquals(complete, coordinator.currentSnapshot(key(chapter)))
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun aLateOlderLayoutCannotReplaceTheNewerCurrentLayout() = runBlocking {
        val chapter = ReaderChapter(
            id = 10,
            bookUuid = "book",
            title = "第一章",
            index = 0,
            paragraphs = listOf(Paragraph(1, 10, 0, "正文")),
        )
        val oldPages = listOf(ReaderPage(0, 0, chapter.title, false, emptyList()))
        val newPages = listOf(
            ReaderPage(0, 0, chapter.title, false, emptyList()),
            ReaderPage(1, 0, chapter.title, false, emptyList()),
        )
        val coordinator = ReaderPaginationCoordinator()
        try {
            // A speculative layout for the old font size, still blocked inside the loader.
            val release = CompletableDeferred<Unit>()
            val stale = coordinator.getOrLoad(key(chapter, layoutSpec(16f)), chapter, prefetch = true) { _, _ ->
                release.await()
                oldPages
            }
            // The user changes the font: a non-prefetch load for the new size supersedes (cancels) it.
            val current = coordinator.getOrLoad(key(chapter, layoutSpec(30f)), chapter, prefetch = false) { _, _ -> newPages }

            val complete = withTimeout(1_000) { current.first { it.isComplete } }
            assertEquals(newPages, complete.pages)
            // The stale layout was cancelled while blocked, so it can never publish over the new one.
            release.complete(Unit)
            delay(25)
            assertTrue(stale.value.pages.isEmpty())
            assertEquals(newPages, coordinator.currentSnapshot(key(chapter, layoutSpec(30f)))?.pages)
        } finally {
            coordinator.close()
        }
    }

    private fun layoutSpec(fontSizeSp: Float) = ReaderLayoutSpec(400f, 760f, fontSizeSp, 1.6f, 0f, 16f)

    private fun key(chapter: ReaderChapter, spec: ReaderLayoutSpec = layoutSpec(18f)) = PaginationCacheKey(
        bookUuid = chapter.bookUuid,
        contentHash = "hash",
        chapterId = chapter.id,
        chapterTitle = chapter.title,
        spec = spec,
        fontIdentity = null,
        showRegularChapterTitle = true,
        density = 3f,
        fontScale = 1f,
        layoutDirection = LayoutDirection.Ltr,
    )
}
