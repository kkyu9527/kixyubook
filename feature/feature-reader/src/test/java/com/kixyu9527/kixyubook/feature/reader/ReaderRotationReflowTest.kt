package com.kixyu9527.kixyubook.feature.reader

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.PageMode
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * A window/spec change must behave like a rotation, not like a chapter jump: the paragraph that is
 * on screen stays on screen. The harness changes the reading viewport size in place (the app is
 * configured to handle orientation itself), which is the real trigger for re-pagination.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w915dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderRotationReflowTest {
    @get:Rule val compose = createComposeRule()

    private val target = 15
    private val paragraphLength = 4_000

    /** -1 keeps the target paragraph uniform while the real pagination is probed. */
    private var markerOffset = -1

    private fun targetParagraph(): String =
        if (markerOffset < 0) {
            "文".repeat(paragraphLength)
        } else {
            "文".repeat(markerOffset) + MARKER + "文".repeat(paragraphLength - markerOffset - 1)
        }

    @Test
    fun aWindowSizeChangeKeepsTheScrolledParagraphInsteadOfTheChapterOpening() {
        val harness = readerRecoveryHarness(
            paragraphCount = 60,
            paragraphText = { index -> "第 $index 段正文。" + "内容".repeat(60) },
            initialSettings = ReaderSettings(fontSize = 19f, pageMode = PageMode.SCROLL, showChapterTitle = false),
        )
        val viewModel = harness.open()
        val visible = mutableStateOf(true)
        val landscape = mutableStateOf(false)
        var settled = 0
        var lastParagraph = -1

        compose.setContent {
            val showing = visible.value
            if (!showing) return@setContent
            val isLandscape = landscape.value
            val state by viewModel.contentState.collectAsState()
            val uiState by viewModel.uiState.collectAsState()
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                Box(
                    Modifier.size(
                        width = if (isLandscape) 915.dp else 412.dp,
                        height = if (isLandscape) 412.dp else 915.dp,
                    ),
                ) {
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
        }

        waitUntilIdling { settled > 0 }
        val openingParagraph = lastParagraph
        compose.onRoot().performTouchInput {
            swipe(start = bottomCenter, end = topCenter, durationMillis = 400)
        }
        waitUntilIdling { lastParagraph > openingParagraph }
        val settledBeforeRotation = settled

        compose.runOnIdle { landscape.value = true }
        waitUntilIdling { settled > settledBeforeRotation && lastParagraph > openingParagraph }
        assertTrue(
            "rotation must keep the scrolled paragraph instead of reopening the chapter (paragraph=$lastParagraph)",
            lastParagraph > openingParagraph,
        )
        harness.closeAll()
    }

    @Test
    fun aWindowSizeChangeKeepsTheVisibleCharacterInsteadOfTheChapterOpening() {
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
        val landscape = mutableStateOf(false)
        var settled = 0
        var settledChar = -1

        compose.setContent {
            val showing = visible.value
            if (!showing) return@setContent
            val isLandscape = landscape.value
            val state by viewModel.contentState.collectAsState()
            val uiState by viewModel.uiState.collectAsState()
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                Box(
                    Modifier.size(
                        width = if (isLandscape) 915.dp else 412.dp,
                        height = if (isLandscape) 412.dp else 915.dp,
                    ),
                ) {
                    if (showing && state.chapter != null) {
                        ReaderContent(
                            state = state,
                            palette = readerPalette(uiState.settings, systemDark = false),
                            savePosition = { position, offset, complete, end, version ->
                                viewModel.onPageSettled(position, offset, complete, end, version)
                                settledChar = offset
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
        }

        // Phase 1 probes the portrait pagination: the second page's first character is where the
        // marker will sit in phase 2, so the assertion depends on the real layout, not a guess.
        waitUntilIdling { settled > 0 }
        swipeToNextPage { settled }
        val reachedCharacter = settledChar
        assertTrue("a page turn must advance the character anchor", reachedCharacter > 0)
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { harness.closeAll() }

        // Phase 2 opens at the paragraph start, turns to the marked character and then rotates.
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
        swipeToNextPage { settled }
        assertTrue("the character reached by the page turn must be visible", markerDisplayed())

        // The pre-rotation page stays as a reflow placeholder until the new layout is measured, so
        // the marker alone would also be visible on the placeholder. Wait for a real settle from the
        // new window as well.
        val settledBeforeRotation = settled
        compose.runOnIdle { landscape.value = true }
        waitUntilIdling { settled > settledBeforeRotation && markerDisplayed() }
        val restoredCharacter = settledChar
        assertTrue(
            "rotation must restore a page inside the paragraph, not its opening (char=$restoredCharacter)",
            restoredCharacter > 0,
        )
        assertTrue("rotation must keep the character that was on screen", markerDisplayed())

        // Rotating back and forth must not ratchet the anchor backwards page by page.
        repeat(4) { index ->
            val before = settled
            compose.runOnIdle { landscape.value = !landscape.value }
            waitUntilIdling { settled > before && markerDisplayed() }
            assertTrue("rotation ${index + 2} must keep the character on screen", markerDisplayed())
        }

        // The retained Compose anchor must also be the durable anchor: otherwise rotations look
        // correct until process death, then a narrower viewport reopens before the marked character.
        compose.runOnIdle {
            visible.value = false
            harness.closeAll()
        }
        compose.waitForIdle()
        assertTrue("the exact character must survive the progress flush", harness.durable.value?.charOffset == markerOffset)
        compose.runOnIdle {
            settled = 0
            landscape.value = false
            viewModel = harness.open()
            visible.value = true
        }
        waitUntilIdling { settled > 0 }
        assertTrue("reopening in portrait must retain the marked character", markerDisplayed())
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
