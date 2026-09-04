package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.ui.unit.LayoutDirection
import com.kixyu9527.kixyubook.core.common.model.Paragraph
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

    private fun key(chapter: ReaderChapter) = PaginationCacheKey(
        bookUuid = chapter.bookUuid,
        contentHash = "hash",
        chapterId = chapter.id,
        chapterTitle = chapter.title,
        spec = ReaderLayoutSpec(400f, 760f, 18f, 1.6f, 0f, 16f),
        fontIdentity = null,
        showRegularChapterTitle = true,
        density = 3f,
        fontScale = 1f,
        layoutDirection = LayoutDirection.Ltr,
    )
}
