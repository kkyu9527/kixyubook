package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
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
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class ReaderRapidNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun materialChapterCommandDuringDragRunsAsSoonAsTheGestureSettles() = duringDrag(AppUiStyle.MATERIAL)
    @Test fun miuixChapterCommandDuringDragRunsAsSoonAsTheGestureSettles() = duringDrag(AppUiStyle.MIUIX)
    @Test fun materialRapidChapterCommandsKeepTheirDestinationWithoutAnimationBacklog() = burst(AppUiStyle.MATERIAL)
    @Test fun miuixRapidChapterCommandsKeepTheirDestinationWithoutAnimationBacklog() = burst(AppUiStyle.MIUIX)
    @Test fun materialCoverTurnsKeepRapidChapterCommands() = burst(AppUiStyle.MATERIAL, PageTurnAnimation.COVER)
    @Test fun miuixCoverTurnsKeepRapidChapterCommands() = burst(AppUiStyle.MIUIX, PageTurnAnimation.COVER)
    @Test @Config(qualifiers = "w1000dp-h800dp")
    fun materialTabletSpreadsKeepRapidChapterCommands() = burst(AppUiStyle.MATERIAL, twoPage = true)
    @Test @Config(qualifiers = "w1000dp-h800dp")
    fun miuixTabletSpreadsKeepRapidChapterCommands() = burst(AppUiStyle.MIUIX, twoPage = true)

    @Test fun materialExplicitJumpSupersedesAChapterCommandWaitingForTheFinger() = explicitJump(AppUiStyle.MATERIAL)
    @Test fun miuixExplicitJumpSupersedesAChapterCommandWaitingForTheFinger() = explicitJump(AppUiStyle.MIUIX)
    @Test fun materialNewFingerInputDiscardsOlderPendingButtonIntent() = newFinger(AppUiStyle.MATERIAL)
    @Test fun miuixNewFingerInputDiscardsOlderPendingButtonIntent() = newFinger(AppUiStyle.MIUIX)

    private fun newFinger(style: AppUiStyle) {
        val fixture = render(style, longText = false)
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { repeat(4) { assertTrue(fixture.turns.tryEmit(1)) } }
        // Deliver a newer gesture before any old command can be painted. The book's leading
        // boundary holds the page, but must not replay the obsolete forward burst afterwards.
        rule.onRoot().performTouchInput { down(Offset(40f, 250f)); moveTo(Offset(200f, 250f), delayMillis = 40) }
        rule.mainClock.advanceTimeBy(48)
        rule.onRoot().performTouchInput { moveTo(Offset(370f, 250f), delayMillis = 40); up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithTag("reader-leaf:0:0").assertIsDisplayed()
        assertEquals(0, fixture.state.value.chapterIndex)
        assertTrue(fixture.destinations.isEmpty())
    }

    private fun explicitJump(style: AppUiStyle) {
        val fixture = render(style, longText = true)
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(Offset(50f, 250f)); moveTo(Offset(180f, 250f), delayMillis = 40) }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { assertTrue(fixture.turns.tryEmit(1)) }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle {
            val snapshot = fixture.state.value
            fixture.state.value = snapshot.copy(chapter = snapshot.prefetchedChapters[3], chapterIndex = 3,
                restorePosition = 0, restoreCharOffset = 0, settledPageIndex = null,
                navigationVersion = snapshot.navigationVersion + 1)
        }
        rule.onRoot().performTouchInput { cancel() }
        rule.mainClock.autoAdvance = true
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("reader-leaf:3:0").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithTag("reader-leaf:3:0").assertIsDisplayed()
        assertEquals(3, fixture.state.value.chapterIndex)
        assertTrue("An older button target must not override explicit navigation", fixture.destinations.isEmpty())
    }

    private fun duringDrag(style: AppUiStyle) {
        val fixture = render(style, longText = true)
        rule.mainClock.autoAdvance = false
        // Start at the first chapter's last page, then drag back one ordinary page. The next
        // chapter is fully prepared, so input must not remain parked after the finger is up.
        rule.onRoot().performTouchInput { down(Offset(50f, 250f)); moveTo(Offset(180f, 250f), delayMillis = 40) }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { assertTrue(fixture.turns.tryEmit(1)) }
        rule.mainClock.advanceTimeBy(48)
        rule.onRoot().performTouchInput { moveTo(Offset(370f, 250f), delayMillis = 40); up() }
        rule.mainClock.autoAdvance = true
        rule.waitUntil(30_000) { fixture.state.value.chapterIndex == 1 }
        rule.onNodeWithTag("reader-leaf:1:0").assertIsDisplayed()
        assertEquals(listOf(1), fixture.destinations.map { it.chapterIndex })
    }

    private fun burst(style: AppUiStyle, animation: PageTurnAnimation = PageTurnAnimation.HORIZONTAL_SLIDE, twoPage: Boolean = false) {
        val fixture = render(style, longText = false, animation, twoPage)
        repeat(3) {
            // No waitForIdle between presses. Deliberately deliver the complete burst within
            // one frame: all clicks count, but obsolete animations must not be replayed later.
            rule.runOnIdle { repeat(5) { assertTrue(fixture.turns.tryEmit(1)) } }
            rule.waitUntil(30_000) { fixture.state.value.chapterIndex == 5 }
            rule.onNodeWithTag("reader-leaf:5:0").assertIsDisplayed()
            rule.runOnIdle { repeat(5) { assertTrue(fixture.turns.tryEmit(-1)) } }
            rule.waitUntil(30_000) { fixture.state.value.chapterIndex == 0 }
            rule.onNodeWithTag("reader-leaf:0:0").assertIsDisplayed()
        }
        assertEquals(List(3) { (1..5).toList() + (4 downTo 0).toList() }.flatten(), fixture.destinations.map { it.chapterIndex })
    }

    private fun render(style: AppUiStyle, longText: Boolean,
        animation: PageTurnAnimation = PageTurnAnimation.HORIZONTAL_SLIDE, twoPage: Boolean = false,
    ): Fixture {
        val chapters = List(6) { Chapter(it.toLong() + 10, "rapid", "Chapter $it", it) }
        val contents = chapters.map { chapter ->
            ReaderChapter(chapter.id, "rapid", chapter.title, chapter.index,
                if (longText) List(25) { Paragraph(it.toLong(), chapter.id, it, "正文阅读内容。".repeat(30)) }
                else listOf(Paragraph(chapter.id, chapter.id, 0, "Plate ${chapter.index}", kind = ParagraphKind.IMAGE,
                    resourcePath = "plate-${chapter.index}.png", intrinsicWidth = 300, intrinsicHeight = 400, isFullPageImage = true)))
        }
        val state = mutableStateOf(ReaderUiState(chapter = contents.first(), chapters = chapters,
            prefetchedChapters = contents.mapIndexed { i, content -> i to content }.toMap(), loading = false,
            restorePosition = if (longText) Int.MAX_VALUE else 0,
            settings = ReaderSettings(showChapterTitle = false, pageTurnAnimation = animation)))
        val fixture = Fixture(state)
        rule.setContent {
            val coordinator = rememberReaderPaginationCoordinator()
            val measurer = rememberTextMeasurer()
            KixyuBookTheme(uiStyle = style) {
                Box(Modifier.size(if (twoPage) 800.dp else 400.dp, 500.dp)) {
                    PagedReader(state.value.toReaderContentState(), state.value.chapter!!,
                        ReaderLayoutSpec(400f, 500f, 20f, 1.5f, 0f, 0f),
                        ReaderRenderPalette(Color.White, Color.Black, Color.Gray, Color.Blue, Color.Gray),
                        savePosition = { _, _, _, _, _ -> }, settlePage = { destination ->
                            fixture.destinations += destination
                            state.value = state.value.copy(chapter = contents[destination.chapterIndex],
                                chapterIndex = destination.chapterIndex, restorePosition = destination.paragraphIndex,
                                restoreCharOffset = destination.charOffset, settledPageIndex = destination.pageIndex,
                                navigationVersion = state.value.navigationVersion + 1)
                        }, middleTap = {}, dismissControls = {}, volumeTurns = fixture.keys, chapterTurns = fixture.turns,
                        paginationCoordinator = coordinator, paginationMeasurer = measurer, chapterRendered = {},
                        setPageInteractionActive = {}, resourcePriorityActive = false, twoPageSpread = twoPage,
                        prioritizeAdjacentChapter = { _, _ -> }, spreadGutter = 0.dp, topInsetDp = 0f, bottomInsetDp = 0f,
                        physicalViewportHeightDp = 500f, onTextActionTarget = {}, onDocumentLink = {})
                }
            }
        }
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("reader-leaf:1:0").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        return fixture
    }

    private class Fixture(val state: MutableState<ReaderUiState>) {
        val turns = MutableSharedFlow<Int>(extraBufferCapacity = 64)
        val keys = MutableSharedFlow<Int>()
        val destinations = mutableListOf<ReaderPageDestination>()
    }
}
