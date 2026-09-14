package com.kixyu9527.kixyubook.feature.reader

import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.PageMode
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
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
 * End-to-end reflow coverage on the real [ReaderContent]: natural page turn to a mid-paragraph
 * offset, change the font through the ViewModel, let real pagination re-measure and settle, then
 * reopen from the persisted progress. The settled page must contain the target paragraph, and a
 * fresh session must land back inside it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderReflowIntegrationTest {
    @get:Rule val compose = createComposeRule()

    private val target = 15
    private val targetOffset = 190

    @Test
    fun aNaturalTurnThenFontChangeReflowsAndReopensOnTheSameParagraph() {
        val harness = readerRecoveryHarness(
            paragraphCount = 30,
            paragraphText = { index ->
                if (index == target) "很长的正文内容。".repeat(240) else "第 $index 段短正文"
            },
            initialSettings = ReaderSettings(fontSize = 19f, pageMode = PageMode.PAGED),
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
        var settledEnd = -1
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
                        savePosition = { position, offset, complete, end ->
                            viewModel.onPageSettled(position, offset, complete, end)
                            settledStart = position
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

        // The opening restore settles on the target paragraph.
        waitUntilIdling { settled > 0 && coversTarget(settledStart, visibleEnd) }
        settledEnd = visibleEnd
        assertTrue("the opening page must contain the restored paragraph", coversTarget(settledStart, settledEnd))

        // Natural reading reaches a mid-paragraph offset on the same page.
        compose.runOnIdle {
            viewModel.onPageSettled(target, targetOffset, chapterComplete = false, visibleEndPosition = target)
        }
        waitUntilIdling { harness.durable.value?.charOffset == targetOffset }

        // Change the font through the real debounced ViewModel path, then let real pagination settle.
        val settledBeforeReflow = settled
        compose.runOnIdle { viewModel.updateSettings { it.copy(fontSize = 30f) } }
        waitUntilIdling {
            val ui = viewModel.uiState.value
            ui.settings.fontSize == 30f && ui.restorePosition == target && ui.restoreCharOffset == targetOffset
        }
        waitUntilIdling { settled > settledBeforeReflow }
        assertTrue(
            "the reflowed page must still contain the target paragraph",
            coversTarget(settledStart, visibleEnd),
        )
        assertEquals(30f, viewModel.uiState.value.settings.fontSize, 0f)

        // Reopen a fresh session from the persisted progress and render the reflowed layout.
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle {
            harness.closeAll()
            settled = 0
            settledStart = -1
            visibleEnd = -1
            viewModel = harness.open()
            visible.value = true
        }
        waitUntilIdling {
            viewModel.uiState.value.chapter != null && settled > 0 && coversTarget(settledStart, visibleEnd)
        }

        assertEquals(
            "the reopened session must restore the target paragraph",
            target,
            viewModel.uiState.value.restorePosition,
        )
        assertTrue(
            "the reopened page must contain the target paragraph (start=$settledStart end=$visibleEnd)",
            coversTarget(settledStart, visibleEnd),
        )
        harness.closeAll()
    }

    /** The settled page's paragraph window, not just its end, must contain the target. */
    private fun coversTarget(start: Int, end: Int): Boolean = start in 0..target && end >= target

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
}
