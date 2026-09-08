package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.reader.engine.*
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
            compose.onNodeWithText("标注").assertIsDisplayed()
            compose.runOnIdle { assertTrue(selection.target?.isAnnotatable == true) }
        }
        selectWord()
        compose.onNodeWithText("标注").performTouchInput { click() }
        compose.onNodeWithText("返回操作").performTouchInput { click() }
        compose.onNodeWithText("标注").performTouchInput { click() }
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
