package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.material3.Icon
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
