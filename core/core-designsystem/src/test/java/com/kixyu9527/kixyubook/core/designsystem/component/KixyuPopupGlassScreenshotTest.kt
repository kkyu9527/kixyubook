package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp")
class KixyuPopupGlassScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun material() = capture(AppUiStyle.MATERIAL, "material")

    @Test fun miuix() = capture(AppUiStyle.MIUIX, "miuix")

    private fun capture(style: AppUiStyle, suffix: String) {
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style, glassEffectEnabled = true) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    KixyuPopupSurface(
                        modifier = Modifier.width(300.dp),
                        backdropEffect = KixyuPopupBackdropEffect.SURFACE_ONLY,
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Liquid Glass", style = MaterialTheme.typography.titleMedium)
                            Text("Popup material", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Liquid Glass").assertExists()
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/popup_$suffix.png")
    }
}
