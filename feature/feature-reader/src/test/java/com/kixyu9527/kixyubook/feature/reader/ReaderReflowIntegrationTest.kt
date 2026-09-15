package com.kixyu9527.kixyubook.feature.reader

import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.PageMode
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * End-to-end layout coverage on the real [ReaderContent]:
 *
 * 1. a natural page turn obtains a mid-paragraph character,
 * 2. the reflow after a font change keeps that character visible,
 * 3. a reopened session restores a page that still shows it,
 * 4. scroll mode keeps persisting progress after a layout-only setting change.
 *
 * The character is a unique marker so visibility comes from the page's rendered text, not from a
 * paragraph-level index that a page falling back to the paragraph start would also satisfy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderReflowIntegrationTest {
    @get:Rule val compose = createComposeRule()

    private val target = 15
    private val paragraphLength = 1_200

    /** -1 keeps the target paragraph uniform while the real pagination is probed. */
    private var markerOffset = -1

    private fun targetParagraph(): String =
        if (markerOffset < 0) {
            "文".repeat(paragraphLength)
        } else {
            "文".repeat(markerOffset) + MARKER + "文".repeat(paragraphLength - markerOffset - 1)
        }

    @Test
    fun aNaturalTurnThenFontChangeReflowsAndReopensOnTheSameCharacter() {
        // Phase 1 probes the real pagination with a uniform paragraph. The second page's start is
        // where the marker will sit, so in phase 2 the marker is part of the body from the first
        // composition and the defect condition (font change after natural reading) is never
        // bypassed by reopening to inject it.
        markerOffset = -1
        var harness = readerRecoveryHarness(
            paragraphCount = 30,
            paragraphText = { index -> if (index == target) targetParagraph() else "第 $index 段短正文" },
            initialSettings = ReaderSettings(fontSize = 30f, pageMode = PageMode.PAGED),
            initialProgress = ReadingProgress(
                bookUuid = "book",
                chapterId = 1,
                position = target,
                offset = 0,
                updatedTime = 1,
                fraction = 0f,
                chapterKey = "first",
                paragraphIndex = target,
                charOffset = 0,
            ),
        )
        var viewModel = harness.open()
        val visible = mutableStateOf(true)
        var settled = 0
        var settledStart = -1
        var settledChar = -1
        var visibleEnd = -1

        compose.setContent {
            val showing = visible.value
            val state by viewModel.contentState.collectAsState()
            val uiState by viewModel.uiState.collectAsState()
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                if (showing && state.chapter != null) {
                    ReaderContent(
                        state = state,
                        palette = readerPalette(uiState.settings, systemDark = false),
                        savePosition = { position, offset, complete, end, version ->
                            viewModel.onPageSettled(position, offset, complete, end, version)
                            settledStart = position
                            settledChar = offset
                            visibleEnd = end
                            settled++
                        },
                        moveChapterFromPage = { _, _, _ -> },
                        settlePage = viewModel::settlePage,
                        middleTap = {},
                        dismissControls = {},
                        volumeTurns = MutableSharedFlow(),
                        chapterTurns = MutableSharedFlow(),
                        chapterRendered = viewModel::chapterRendered,
                        setPageInteractionActive = {},
                        prioritizeAdjacentChapter = { _, _ -> },
                        resourcePriorityActive = false,
                        onTextActionTarget = {},
                        onDocumentLink = {},
                    )
                }
            }
        }

        // Phase 1: the opening restore settles on a page containing the long paragraph, then one
        // real page turn obtains the next page's first character.
        waitUntilIdling { settled > 0 }
        assertTrue(
            "the opening page must contain the restored paragraph (start=$settledStart end=$visibleEnd)",
            settledStart <= target && visibleEnd >= target,
        )
        swipeToNextPage { settled }
        val reachedCharacter = settledChar
        assertTrue("a natural page turn must advance the character anchor", reachedCharacter > 0)
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { harness.closeAll() }

        // Phase 2: fresh session with the marker already in the body, opening at the paragraph
        // start. The same natural turn must reach the marked character.
        markerOffset = reachedCharacter
        harness = readerRecoveryHarness(
            paragraphCount = 30,
            paragraphText = { index -> if (index == target) targetParagraph() else "第 $index 段短正文" },
            initialSettings = ReaderSettings(fontSize = 30f, pageMode = PageMode.PAGED),
            initialProgress = ReadingProgress(
                bookUuid = "book",
                chapterId = 1,
                position = target,
                offset = 0,
                updatedTime = 1,
                fraction = 0f,
                chapterKey = "first",
                paragraphIndex = target,
                charOffset = 0,
            ),
        )
        compose.runOnIdle {
            settled = 0
            settledChar = -1
            viewModel = harness.open()
            visible.value = true
        }
        waitUntilIdling { settled > 0 }
        assertEquals("the opening page must start at the paragraph start", 0, settledChar)
        assertFalse("the marker is past the opening page", markerDisplayed())

        swipeToNextPage { settled }
        assertEquals("the turned page start must match the probed character", reachedCharacter, settledChar)
        assertTrue("the character reached by the page turn must be visible", markerDisplayed())

        // The reflow must keep the reached character on the visible page in the same session.
        compose.runOnIdle { viewModel.updateSettings { it.copy(fontSize = 40f) } }
        waitUntilIdling { viewModel.uiState.value.settings.fontSize == 40f && markerDisplayed() }
        assertTrue("the reflow must keep the reached character visible", markerDisplayed())

        // A fresh session must restore a page that still shows the character.
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle {
            harness.closeAll()
            settled = 0
            viewModel = harness.open()
            visible.value = true
        }
        waitUntilIdling { settled > 0 && markerDisplayed() }
        assertTrue(
            "the reopened page must not start after the target paragraph",
            viewModel.uiState.value.restorePosition <= target,
        )
        assertTrue("reopening must show the reached character", markerDisplayed())
        harness.closeAll()
    }

    @Test
    fun scrollModeKeepsPersistingProgressAfterALayoutSettingChange() {
        val harness = readerRecoveryHarness(
            paragraphCount = 80,
            paragraphText = { index -> "第 $index 段正文。" + "内容".repeat(60) },
            // Explicitly off: toggling it must really change the layout version.
            initialSettings = ReaderSettings(fontSize = 19f, pageMode = PageMode.SCROLL, showChapterTitle = false),
        )
        var viewModel = harness.open()
        val visible = mutableStateOf(true)
        var settled = 0
        var lastParagraph = -1

        compose.setContent {
            val showing = visible.value
            val state by viewModel.contentState.collectAsState()
            val uiState by viewModel.uiState.collectAsState()
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                if (showing && state.chapter != null) {
                    ReaderContent(
                        state = state,
                        palette = readerPalette(uiState.settings, systemDark = false),
                        savePosition = { position, offset, complete, end, version ->
                            viewModel.onPageSettled(position, offset, complete, end, version)
                            lastParagraph = position
                            settled++
                        },
                        moveChapterFromPage = { _, _, _ -> },
                        settlePage = viewModel::settlePage,
                        middleTap = {},
                        dismissControls = {},
                        volumeTurns = MutableSharedFlow(),
                        chapterTurns = MutableSharedFlow(),
                        chapterRendered = viewModel::chapterRendered,
                        setPageInteractionActive = {},
                        prioritizeAdjacentChapter = { _, _ -> },
                        resourcePriorityActive = false,
                        onTextActionTarget = {},
                        onDocumentLink = {},
                    )
                }
            }
        }

        waitUntilIdling { settled > 0 }
        val beforeScrolling = lastParagraph

        // A layout-only setting change used to arm a guard that scroll mode could never clear.
        val versionBefore = viewModel.uiState.value.layoutVersion
        compose.runOnIdle { viewModel.updateSettings { it.copy(showChapterTitle = true) } }
        waitUntilIdling { viewModel.uiState.value.settings.showChapterTitle }
        assertTrue(
            "toggling the chapter title must change the layout version",
            viewModel.uiState.value.layoutVersion > versionBefore,
        )

        compose.onRoot().performTouchInput {
            swipe(start = bottomCenter, end = topCenter, durationMillis = 400)
        }
        waitUntilIdling { lastParagraph > beforeScrolling }
        waitUntilIdling { harness.durable.value?.paragraphIndex == lastParagraph }
        assertTrue(
            "scrolling after the setting change must persist progress",
            (harness.durable.value?.paragraphIndex ?: -1) > beforeScrolling,
        )
        harness.closeAll()
    }

    /** True when the unique marker is part of a visible page's rendered text. */
    private fun markerDisplayed(): Boolean = runCatching {
        compose.onAllNodesWithText(MARKER, substring = true, useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
    }.isSuccess

    /** One deliberate, slow page turn that snaps to the next page instead of flinging past it. */
    private fun swipeToNextPage(settled: () -> Int) {
        val before = settled()
        compose.onRoot().performTouchInput {
            swipe(start = centerRight, end = centerLeft, durationMillis = 500)
        }
        waitUntilIdling { settled() > before }
    }

    /**
     * Drives the Robolectric main looper explicitly so coroutine work and the 180ms settings
     * debounce actually complete between frames, then pumps Compose.
     */
    private fun waitUntilIdling(timeoutMillis: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            compose.waitForIdle()
            if (condition()) return
        }
        assertTrue("condition not satisfied within ${timeoutMillis}ms", condition())
    }

    private companion object {
        const val MARKER = "龘"
    }
}
