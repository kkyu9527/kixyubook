package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KixyuAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun materialSelectedResultIsAnnounced() = selectedResult(AppUiStyle.MATERIAL)
    @Test fun miuixSelectedResultIsAnnounced() = selectedResult(AppUiStyle.MIUIX)

    private fun selectedResult(style: AppUiStyle) {
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                KixyuListRow(title = "Chapter", supportingText = "Search result", selected = true, onClick = {})
            }
        }
        compose.onNodeWithText("Chapter").assertIsSelected().assertHasClickAction()
    }

    @Test
    fun materialPrimaryButtonKeepsMinimumTouchTarget() = primaryButtonTouchTarget(AppUiStyle.MATERIAL)

    @Test
    fun miuixPrimaryButtonKeepsMinimumTouchTarget() = primaryButtonTouchTarget(AppUiStyle.MIUIX)

    private fun primaryButtonTouchTarget(style: AppUiStyle) {
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                KixyuButton(text = "继续", onClick = {})
            }
        }

        compose.onNodeWithText("继续").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun materialActionDialogKeepsActionsReachable() = actionDialog(AppUiStyle.MATERIAL)

    @Test
    fun miuixActionDialogKeepsActionsReachable() = actionDialog(AppUiStyle.MIUIX)

    private fun actionDialog(style: AppUiStyle) {
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                KixyuActionDialog(
                    show = true,
                    title = "删除书籍",
                    onDismissRequest = {},
                    confirmLabel = "确认",
                    onConfirm = {},
                ) {
                    Text("此操作不可撤销")
                }
            }
        }
        compose.onNodeWithText("此操作不可撤销").assertExists()
        compose.onNodeWithText("确认").assertHasClickAction()
    }

    @Test
    fun sharedIconButtonKeepsMinimumTouchTarget() {
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                KixyuIconButton(onClick = {}) {
                    Icon(KixyuSymbols.Close, contentDescription = "关闭")
                }
            }
        }

        compose.onNodeWithContentDescription("关闭")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
