package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.kixyuPageBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp")
class KixyuDualThemeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialContextBar() = captureContextBar(AppUiStyle.MATERIAL, "material")

    @Test fun miuixContextBar() = captureContextBar(AppUiStyle.MIUIX, "miuix")

    @Test fun materialDarkContextBar() = captureContextBar(AppUiStyle.MATERIAL, "material_dark", ReaderTheme.NIGHT)

    @Test fun miuixDarkContextBar() = captureContextBar(AppUiStyle.MIUIX, "miuix_dark", ReaderTheme.NIGHT)

    @Test
    @Config(qualifiers = "zh-rCN-w1000dp-h700dp")
    fun materialTabletContextBar() = captureContextBar(AppUiStyle.MATERIAL, "material_tablet")

    @Test
    @Config(qualifiers = "zh-rCN-w1000dp-h700dp")
    fun miuixTabletContextBar() = captureContextBar(AppUiStyle.MIUIX, "miuix_tablet")

    private fun captureContextBar(style: AppUiStyle, suffix: String, theme: ReaderTheme = ReaderTheme.DAY) {
        compose.setContent {
            KixyuBookTheme(
                themeMode = theme,
                uiStyle = style,
                // Runtime blur is device/GPU dependent. Screenshot contracts validate the shared
                // geometry, fallback glass tint, typography and semantic colors instead.
                glassEffectEnabled = false,
            ) {
                val background = kixyuPageBackground()
                val backdrop = rememberKixyuNavigationBackdrop(background)
                Box(Modifier.fillMaxSize().background(background)) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val textColor = if (theme == ReaderTheme.NIGHT) MaterialTheme.colorScheme.onSurface else Color.Unspecified
                        Text("已选择 2 本书", color = textColor)
                        Text("批量操作会替换常规导航", color = textColor)
                    }
                    KixyuContextualActionBar(
                        actions = listOf(
                            KixyuContextualAction("category", "分类", KixyuSymbols.Category) {},
                            KixyuContextualAction("share", "分享", KixyuSymbols.Share) {},
                            KixyuContextualAction(
                                "delete",
                                "删除",
                                KixyuSymbols.DeleteOutline,
                                destructive = true,
                            ) {},
                        ),
                        backdrop = backdrop,
                        modifier = Modifier.align(Alignment.BottomCenter).height(112.dp),
                    )
                }
            }
        }

        compose.onNodeWithText("分类").assertIsDisplayed()
        compose.onNodeWithText("删除").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/context_bar_$suffix.png")
    }
}
