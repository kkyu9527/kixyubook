package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kixyu9527.kixyubook.core.common.model.AppColorTheme
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
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
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp")
class KixyuAppearanceScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun material() = capture(AppUiStyle.MATERIAL, ReaderTheme.DAY, "material")

    @Test
    fun miuix() = capture(AppUiStyle.MIUIX, ReaderTheme.DAY, "miuix")

    @Test
    fun materialDark() = capture(AppUiStyle.MATERIAL, ReaderTheme.NIGHT, "material_dark")

    private fun capture(style: AppUiStyle, theme: ReaderTheme, suffix: String) {
        val settings = ReaderSettings(
            appUiStyle = style,
            appColorTheme = if (style == AppUiStyle.MIUIX) AppColorTheme.DEFAULT else AppColorTheme.SAGE,
        )
        compose.setContent {
            KixyuBookTheme(themeMode = theme, uiStyle = style) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                ) {
                    KixyuSection(title = "Theme & colour") {
                        KixyuThemeModeControl(settings, {})
                        KixyuDivider()
                        KixyuAppUiStyleControl(settings, {})
                        KixyuDivider()
                        KixyuAppColorControl(settings, {})
                    }
                }
            }
        }

        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/appearance_$suffix.png")
    }
}
