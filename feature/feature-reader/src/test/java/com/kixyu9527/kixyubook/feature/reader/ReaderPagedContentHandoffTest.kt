package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureLevel
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.reader.engine.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReaderPagedContentHandoffTest {
    @get:Rule val rule = createComposeRule()

    @Test fun chapterButtonKeepsImageOpeningAcrossCacheEvictionThenGestureReachesText() =
        exerciseHandoff(PageTurnAnimation.HORIZONTAL_SLIDE, enterByGesture = false)

    @Test fun coverGesturesKeepImageOpeningAndCanReturnToPreviousChapter() =
        exerciseHandoff(PageTurnAnimation.COVER, enterByGesture = true)

    private fun exerciseHandoff(animation: PageTurnAnimation, enterByGesture: Boolean) {
        val chapters = listOf(Chapter(10, "book", "旧章", 0), Chapter(20, "book", "新章", 1))
        val current = ReaderChapter(10, "book", "旧章", 0, listOf(Paragraph(1, 10, 0, "旧章正文")))
        val next = ReaderChapter(20, "book", "新章", 1, listOf(
            Paragraph(2, 20, 0, "插画", kind = ParagraphKind.IMAGE,
                resourcePath = "illustration.png", mediaType = "image/png",
                intrinsicWidth = 300, intrinsicHeight = 400, isFullPageImage = true),
            Paragraph(3, 20, 0, "书签和批注的目标正文"),
        ))
        var state by mutableStateOf(ReaderUiState(
            chapter = current, chapters = chapters,
            prefetchedChapters = mapOf(0 to current, 1 to next),
            settings = ReaderSettings(showChapterTitle = false, pageTurnAnimation = animation), loading = false,
        ))
        val turns = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        val volumeTurns = MutableSharedFlow<Int>()
        val destinations = mutableListOf<ReaderPageDestination>()
        val spec = ReaderLayoutSpec(300f, 400f, 18f, 1.4f, 0f, 16f)
        rule.setContent {
            val coordinator = rememberReaderPaginationCoordinator()
            val measurer = rememberTextMeasurer()
            MaterialTheme {
                Box(Modifier.size(300.dp, 400.dp)) {
                    PagedReader(
                        state = state.toReaderContentState(), chapter = state.chapter!!,
                        spec = spec, palette = ReaderRenderPalette(Color.White, Color.Black, Color.Black, Color.Blue, Color.Gray),
                        savePosition = { _, _, _, _ -> },
                        settlePage = { destination ->
                            destinations += destination
                            coordinator.onMemoryPressure(MemoryPressureLevel.CRITICAL)
                            state = state.copy(
                                chapter = if (destination.chapterIndex == 0) current else next,
                                chapterIndex = destination.chapterIndex,
                                restorePosition = destination.paragraphIndex, restoreCharOffset = destination.charOffset,
                                settledPageIndex = destination.pageIndex, navigationVersion = state.navigationVersion + 1,
                            )
                        },
                        middleTap = {}, dismissControls = {}, volumeTurns = volumeTurns, chapterTurns = turns,
                        paginationCoordinator = coordinator, paginationMeasurer = measurer,
                        chapterRendered = {}, setPageInteractionActive = {}, resourcePriorityActive = false,
                        twoPageSpread = false, prioritizeAdjacentChapter = { _, _ -> }, spreadGutter = 0.dp,
                        topInsetDp = 0f, bottomInsetDp = 0f, physicalViewportHeightDp = 400f,
                        onTextActionTarget = {}, onDocumentLink = {},
                    )
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("reader-leaf:0:0").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("reader-leaf:1:0").fetchSemanticsNodes().isNotEmpty() }
        if (enterByGesture) rule.onRoot().performTouchInput { swipeLeft() }
        else rule.runOnIdle { turns.tryEmit(1) }
        rule.waitUntil(10_000) { destinations.isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithTag("reader-leaf:1:0").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(1, destinations.size)
            assertEquals(0, destinations.single().pageIndex)
        }
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("reader-leaf:1:1").assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, destinations.size) }
        rule.onRoot().performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithTag("reader-leaf:1:0").assertIsDisplayed()
        rule.onRoot().performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithTag("reader-leaf:0:0").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(2, destinations.size)
            assertEquals(0, destinations.last().chapterIndex)
            // Bookmark/search navigation is a text anchor, unlike an acknowledged image leaf.
            state = state.copy(
                chapter = next, chapterIndex = 1, restorePosition = 0, restoreCharOffset = 3,
                settledPageIndex = null, navigationVersion = state.navigationVersion + 1,
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("reader-leaf:1:1").assertIsDisplayed()
        rule.runOnIdle { assertEquals(2, destinations.size) }
    }
}
