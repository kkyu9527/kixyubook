package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
class KixyuGlassScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun material() = capture(AppUiStyle.MATERIAL, ReaderTheme.DAY, "material")

    @Test
    fun miuix() = capture(AppUiStyle.MIUIX, ReaderTheme.DAY, "miuix")

    @Test
    fun materialDark() = capture(AppUiStyle.MATERIAL, ReaderTheme.NIGHT, "material_dark")

    @Test
    fun miuixDark() = capture(AppUiStyle.MIUIX, ReaderTheme.NIGHT, "miuix_dark")

    private fun capture(style: AppUiStyle, theme: ReaderTheme, suffix: String) {
        compose.setContent {
            KixyuBookTheme(themeMode = theme, uiStyle = style, glassEffectEnabled = true) {
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        val dark = theme == ReaderTheme.NIGHT
                        drawRect(
                            Brush.linearGradient(
                                if (dark) {
                                    listOf(Color(0xFF0B1730), Color(0xFF392044), Color(0xFF123B36))
                                } else {
                                    listOf(Color(0xFFB8D8FF), Color(0xFFFFC7A8), Color(0xFFC7B8FF))
                                },
                            ),
                        )
                        drawCircle(
                            Color.White.copy(alpha = if (dark) .20f else .55f),
                            radius = size.minDimension * .35f,
                            center = Offset(size.width * .2f, size.height * .25f),
                        )
                        drawCircle(
                            Color(0xFF3355AA).copy(alpha = if (dark) .45f else .5f),
                            radius = size.minDimension * .28f,
                            center = Offset(size.width * .8f, size.height * .7f),
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        KixyuPopupSurface(
                            modifier = Modifier.size(280.dp, 160.dp),
                            backdropEffect = KixyuPopupBackdropEffect.SURFACE_ONLY,
                        ) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Liquid Glass", style = MaterialTheme.typography.titleLarge)
                                Text("Frosted surface · specular rim", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/glass_$suffix.png")
    }
}
