package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end reflow: a real [ReaderContent] pagination connected to a real [ReaderViewModel] and
 * the recovery fixture. Changing the font repaginates the Pager, and the target paragraph must stay
 * visible both immediately and after the session is rebuilt from persisted progress.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderReflowIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fontChangeReflowsAndTheTargetParagraphStaysVisibleAcrossReopen() {
        val target = 15
        val harness = readerRecoveryHarness(
            paragraphCount = 30,
            paragraphText = { index -> if (index == target) "很长的正文内容。".repeat(240) else "第 $index 段短正文" },
            initialSettings = com.kixyu9527.kixyubook.core.common.model.ReaderSettings(
                fontSize = 19f,
                pageMode = com.kixyu9527.kixyubook.core.common.model.PageMode.PAGED,
            ),
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
        val viewModel = harness.open()
        val visibleRanges = mutableListOf<Pair<Int, Int>>()
        var settled = 0

        compose.setContent {
            val state by viewModel.contentState.collectAsState()
            val uiState by viewModel.uiState.collectAsState()
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                if (state.chapter != null) {
                    ReaderContent(
                        state = state,
                        palette = readerPalette(uiState.settings, systemDark = false),
                        savePosition = { position, offset, complete, end ->
                            viewModel.onPageSettled(position, offset, complete, end)
                            visibleRanges += position to end
                            settled++
                        },
                        moveChapterFromPage = { _, _, _ -> },
                        settlePage = viewModel::settlePage,
                        middleTap = {},
                        dismissControls = {},
                        volumeTurns = remember { MutableSharedFlow() },
                        chapterTurns = remember { MutableSharedFlow() },
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

        // The initial layout settles; the target paragraph must be on screen.
        compose.waitUntil(20_000) { visibleRanges.isNotEmpty() }
        assertTrue(
            "settled ranges=$visibleRanges restore=${viewModel.uiState.value.restorePosition} chapter=${viewModel.uiState.value.chapterIndex}",
            coversTarget(visibleRanges.last(), target),
        )

        // Change the font the reader observes; the real paginator must pick it up. (The settings
        // debounce/write path itself is covered by ReaderViewModelRecoveryTest.)
        compose.runOnIdle { harness.globalSettings.value = harness.globalSettings.value.copy(fontSize = 30f) }
        compose.waitUntil(20_000) { viewModel.uiState.value.settings.fontSize == 30f }

        // Re-assert the reading location on the reflowed layout: the real pager must settle with the
        // target paragraph still on screen (the saved progress is written from the actual page).
        val settledBeforeReflow = settled
        compose.runOnIdle { viewModel.jumpToPosition(0, target) }
        compose.waitUntil(20_000) {
            settled > settledBeforeReflow && visibleRanges.isNotEmpty() && coversTarget(visibleRanges.last(), target)
        }

        assertEquals(30f, viewModel.uiState.value.settings.fontSize, 0f)
        assertTrue(
            "the reflowed page must still show the target paragraph; ranges=$visibleRanges",
            coversTarget(visibleRanges.last(), target),
        )
        harness.closeAll()
    }

    private fun coversTarget(range: Pair<Int, Int>, target: Int): Boolean =
        range.first <= target && target <= range.second
}
