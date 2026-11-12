package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp")
class KixyuWavyScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun material() = capture(AppUiStyle.MATERIAL, ReaderTheme.DAY, "material")

    @Test
    fun miuix() = capture(AppUiStyle.MIUIX, ReaderTheme.DAY, "miuix")

    @Test
    fun materialRtl() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        KixyuWavyProgressBar(progress = .05f)
                        KixyuWavyProgressBar(progress = .42f)
                        KixyuWavyProgressBar(progress = null)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/wavy_material_rtl.png")
    }

    @Test
    fun materialDark() = capture(AppUiStyle.MATERIAL, ReaderTheme.NIGHT, "material_dark")

    @Test
    fun miuixDark() = capture(AppUiStyle.MIUIX, ReaderTheme.NIGHT, "miuix_dark")

    private fun capture(style: AppUiStyle, theme: ReaderTheme, suffix: String) {
        compose.setContent {
            KixyuBookTheme(themeMode = theme, uiStyle = style) {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    KixyuWavyProgressBar(progress = 0f)
                    KixyuWavyProgressBar(progress = .05f)
                    KixyuWavyProgressBar(progress = .42f)
                    KixyuWavyProgressBar(progress = .83f)
                    KixyuWavyProgressBar(progress = 1f)
                    KixyuWavyProgressBar(progress = null)
                    KixyuProgressBar(progress = .42f, wavy = true)
                }
            }
        }

        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/wavy_$suffix.png")
    }
}
