package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.BookSearchProgress
import com.kixyu9527.kixyubook.core.common.model.BookSearchStage
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderSearchOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialSearchButtonsReachControllerAndCanReturn() = exerciseSearch(AppUiStyle.MATERIAL)
    @Test fun miuixSearchButtonsReachControllerAndCanReturn() = exerciseSearch(AppUiStyle.MIUIX)

    private fun exerciseSearch(style: AppUiStyle) {
        ReaderSearchFixture().use { fixture ->
            var visible by mutableStateOf(true)
            compose.setContent {
                val state by fixture.state.collectAsState()
                KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style, glassEffectEnabled = true) {
                    Box(Modifier.fillMaxSize()) {
                        ReaderSearchOverlay(
                            visible = visible, progress = { 0f }, state = state,
                            onDismiss = { visible = false }, onSearch = fixture.controller::search,
                            onClearHistory = {}, onMove = fixture.controller::move,
                            onReturn = fixture.controller::returnToReadingPosition,
                            onSelect = fixture.controller::select,
                        )
                    }
                }
            }
            // MIUIX merges the trailing icon into the editable field in the merged tree.
            // Address the actual icon bounds so a test cannot accidentally tap the input itself.
            compose.onNodeWithContentDescription("搜索", useUnmergedTree = true).performTouchInput { click() }
            compose.runOnIdle { assertTrue(fixture.repository.requests.isEmpty()) }
            compose.onNode(hasSetTextAction()).performTextInput("黄金")
            // Pointer events, not direct ViewModel calls: catch surfaces intercepting user taps.
            compose.onNodeWithContentDescription("搜索", useUnmergedTree = true).performTouchInput { click() }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals("黄金", fixture.repository.requests.single().query)
                runBlocking { fixture.repository.requests.single().progress(BookSearchProgress(BookSearchStage.SEARCHING, 1, 4)) }
            }
            compose.onNodeWithText("正在搜索正文 · 1/4 章 · 25%").assertIsDisplayed()
            compose.runOnIdle {
                runBlocking { fixture.repository.requests.single().progress(BookSearchProgress(BookSearchStage.SEARCHING, 3, 4)) }
            }
            compose.onNodeWithText("正在搜索正文 · 3/4 章 · 75%").assertIsDisplayed()
            compose.runOnIdle {
                fixture.repository.requests.single().result.complete(emptyList())
            }
            compose.onNodeWithText("1 个匹配结果").assertIsDisplayed()
            compose.onNodeWithText("当前章节").performTouchInput { click() }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(listOf(5 to 3), fixture.jumps) }
            compose.onNodeWithContentDescription("返回跳转前位置").performTouchInput { click() }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(1, fixture.returns) }
            compose.onNode(hasSetTextAction()).performTextReplacement("不存在的词")
            compose.onNodeWithText("1 个匹配结果").assertDoesNotExist()
            compose.onNode(hasSetTextAction()).performImeAction()
            compose.runOnIdle {
                assertEquals("不存在的词", fixture.repository.requests.last().query)
                fixture.repository.requests.last().result.complete(emptyList())
            }
            compose.onNodeWithText("没有找到匹配内容").assertIsDisplayed()
            compose.onNodeWithContentDescription("关闭搜索").performTouchInput { click() }
            compose.waitForIdle()
            compose.onNodeWithText("全文搜索").assertDoesNotExist()
            compose.runOnIdle { assertFalse(visible) }
        }
    }
}
