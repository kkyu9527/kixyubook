package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.reader.engine.*
import com.kixyu9527.kixyubook.feature.reader.browser.BrowserSelection
import com.kixyu9527.kixyubook.feature.reader.browser.LocalBrowserSelectionHandler
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp", shadows = [HostMagnifierShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderSelectionInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialLongPressActionsAndOutsideDismissStayInsideReader() = exerciseSelection(AppUiStyle.MATERIAL)
    @Test fun miuixLongPressActionsAndOutsideDismissStayInsideReader() = exerciseSelection(AppUiStyle.MIUIX)
    @Test fun materialBrowserRangesUseTheSameMenuAndClearLifecycle() = exerciseBrowserSelection(AppUiStyle.MATERIAL)
    @Test fun miuixBrowserRangesUseTheSameMenuAndClearLifecycle() = exerciseBrowserSelection(AppUiStyle.MIUIX)

    private fun exerciseBrowserSelection(style: AppUiStyle) {
        val selection = ReaderTextInteractionState()
        var publish: (BrowserSelection?) -> Unit = {}
        var highlights = 0
        var clears = 0
        var copies = 0
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style, glassEffectEnabled = true) {
                ReaderTextInteractionHost(selection, {}, { highlights++ }, {}, {}) {
                    publish = LocalBrowserSelectionHandler.current
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
        fun request(target: ReaderTextActionTarget) = compose.runOnIdle {
            publish(BrowserSelection(target, Rect(40f, 200f, 260f, 230f), { copies++ }, { clears++ }))
        }
        request(ReaderTextActionTarget(5, 7, "Selected text.", 0, 8))
        compose.onNodeWithText("标注").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithText("高亮").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, highlights); assertTrue(clears > 0); assertNull(selection.target) }
        // Copy-only multi-paragraph targets must never inherit the previous actionable range.
        request(ReaderTextActionTarget(5, 7, "First\nSecond"))
        compose.onNodeWithText("标注").assertDoesNotExist()
        val copyLabel = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(android.R.string.copy)
        compose.onNodeWithText(copyLabel).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, copies); assertNull(selection.target) }
    }

    /**
     * Menu content is added by a coroutine after the platform context-menu callback. On a cold
     * Linux/Robolectric first test the first layout pass can still report a zero-sized toolbar
     * even after waitForIdle, so wait for real bounds before asserting display.
     */
    private fun awaitDisplayed(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().any { node ->
                val bounds = node.boundsInRoot
                bounds.width > 0f && bounds.height > 0f
            }
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun exerciseSelection(style: AppUiStyle) {
        val selection = ReaderTextInteractionState()
        val text = "Golden passage for selecting and annotating."
        var highlights = 0
        var pageTaps = 0
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style, glassEffectEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    ReaderTextInteractionHost(
                        state = selection, onCorrectText = {}, onUnderlineText = {}, onNoteText = {},
                        onHighlightText = { highlights++ },
                    ) {
                        ReaderPageRenderer(
                            page = ReaderPage(0, 4, "正文", false, listOf(DocumentBlock(
                                paragraphIndex = 3, fullText = text, visibleText = text, continuation = false,
                            ))),
                            spec = ReaderLayoutSpec(412f, 915f, 20f, 1.4f, 0f, 16f),
                            palette = ReaderRenderPalette(Color.White, Color.Black, Color.Gray, Color.Blue, Color.Gray),
                            fontPath = null, onTapFraction = { pageTaps++ },
                            modifier = Modifier.fillMaxSize(), showRegularChapterTitle = false,
                            onTextActionTarget = selection::publishTarget,
                        )
                    }
                }
            }
        }
        fun selectWord() {
            compose.onNodeWithText(text).performTouchInput { longClick(Offset(60f, centerY)) }
            compose.waitForIdle()
            awaitDisplayed("标注")
            compose.runOnIdle { assertTrue(selection.target?.isAnnotatable == true) }
        }
        selectWord()
        awaitDisplayed("标注")
        compose.onNodeWithText("标注").performTouchInput { click() }
        awaitDisplayed("返回操作")
        compose.onNodeWithText("返回操作").performTouchInput { click() }
        awaitDisplayed("标注")
        compose.onNodeWithText("标注").performTouchInput { click() }
        awaitDisplayed("高亮")
        compose.onNodeWithText("高亮").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("高亮").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, highlights)
            // Renderer may retain a zero-length pointer hint; no actionable selection may remain.
            assertFalse(selection.target?.isAnnotatable == true)
            assertTrue(selection.clearRequest > 0)
            pageTaps = 0
        }
        selectWord()
        compose.onAllNodes(isRoot())[0].performTouchInput { click(Offset(centerX, height - 100f)) }
        compose.waitForIdle()
        compose.onNodeWithText("标注").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(selection.target?.isAnnotatable == true)
            assertEquals(ReaderTextActionPanel.PRIMARY, selection.panel)
            assertEquals(0, pageTaps) // Dismissing selection must not also turn a page.
        }
    }
}
