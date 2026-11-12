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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** High-frequency background exposes both over-blur and interference through a text panel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KixyuGlassLegibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialKeepsControlsClearAndTextPanelsReadable() = verify(AppUiStyle.MATERIAL, ReaderTheme.DAY)
    @Test fun miuixKeepsControlsClearAndTextPanelsReadable() = verify(AppUiStyle.MIUIX, ReaderTheme.DAY)
    @Test fun darkMaterialKeepsControlsClearAndTextPanelsReadable() = verify(AppUiStyle.MATERIAL, ReaderTheme.NIGHT)
    @Test fun darkMiuixKeepsControlsClearAndTextPanelsReadable() = verify(AppUiStyle.MIUIX, ReaderTheme.NIGHT)

    private fun verify(style: AppUiStyle, theme: ReaderTheme) {
        var closed = false
        compose.setContent {
            KixyuBookTheme(themeMode = theme, uiStyle = style, glassEffectEnabled = true) {
                val source = rememberKixyuNavigationBackdrop(MaterialTheme.colorScheme.background)
                CompositionLocalProvider(LocalKixyuGlassBackdrop provides source) {
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize().kixyuNavigationBackdrop(source)) {
                            for (x in 0 until size.width.toInt() step 2) {
                                drawRect(
                                    if (x % 4 == 0) Color.Black else Color.White,
                                    Offset(x.toFloat(), 0f), Size(2f, size.height),
                                )
                            }
                        }
                        Column(
                            Modifier.align(Alignment.Center),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            KixyuGlassIsland(source, Modifier.size(236.dp, 64.dp).testTag("clear_control")) {}
                            // Default popup intent must protect text, without per-dialog optics.
                            KixyuPopupSurface(Modifier.size(236.dp, 224.dp).testTag("readable_panel")) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("目录 · 简介 · 更新日志")
                                    Text("Chapter fourteen. This panel should remain readable over a busy page.")
                                    TextButton(onClick = { closed = true }) { Text("关闭") }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("目录 · 简介 · 更新日志").assertIsDisplayed()
        val control = compose.onNodeWithTag("clear_control")
        val clear = control.captureToImage().toPixelMap()
        val panel = compose.onNodeWithTag("readable_panel").captureToImage().toPixelMap()
        val clearValues = (48 until 188).map { clear[it, 32].red }
        val panelValues = (48 until 188).map { panel[it, 196].red }
        assertTrue("Settled controls must retain high-frequency source detail", clearValues.max() - clearValues.min() > .80f)
        assertTrue("Underlying text/stripes must not compete with panel text", panelValues.max() - panelValues.min() < .08f)
        val left = control.fetchSemanticsNode().boundsInRoot.left.toInt()
        val tint = KixyuGlassStyle.surfaceTint(KixyuGlassRole.CONTROL, theme == ReaderTheme.NIGHT, sampled = true)
        val refractedPixels = (4 until 16).count { x ->
            val source = if ((left + x) % 4 < 2) Color.Black else Color.White
            val unwarped = tint.compositeOver(source)
            kotlin.math.abs(clear[x, 32].red - unwarped.red) > .15f
        }
        assertTrue("The settled glass rim must bend the background, not only tint it", refractedPixels >= 3)
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { assertTrue("Material rendering must not intercept content input", closed) }
    }
}
