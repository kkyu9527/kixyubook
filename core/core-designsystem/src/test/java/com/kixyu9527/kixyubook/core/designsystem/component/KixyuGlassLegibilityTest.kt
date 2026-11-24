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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/** Busy text must be filtered by the entire material, including toolbar interiors. */
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
        var level by mutableFloatStateOf(55f)
        var broadBackground by mutableStateOf(false)
        compose.setContent {
            KixyuBookTheme(themeMode = theme, uiStyle = style, glassEffectEnabled = true, glassFrostLevel = level) {
                val source = rememberKixyuNavigationBackdrop(MaterialTheme.colorScheme.background)
                CompositionLocalProvider(LocalKixyuGlassBackdrop provides source) {
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize().kixyuNavigationBackdrop(source)) {
                            if (broadBackground) {
                                drawRect(Color.Black)
                                drawRect(Color.White, Offset(size.width / 2, 0f), Size(size.width / 2, size.height))
                            } else for (x in 0 until size.width.toInt() step 2) {
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
                            KixyuNavigationGlass(
                                source,
                                selectionModifier = Modifier.size(118.dp, 64.dp),
                                pressProgress = { 0f },
                                modifier = Modifier.size(236.dp, 64.dp).testTag("navigation"),
                            )
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
        assertTrue("Toolbar text must not compete with high-frequency background detail", clearValues.max() - clearValues.min() < .12f)
        assertTrue("Underlying text/stripes must not compete with panel text", panelValues.max() - panelValues.min() < .08f)
        val navigation = compose.onNodeWithTag("navigation").captureToImage().toPixelMap()
        val lensValues = (40 until 80).map { navigation[it, 32].red }
        assertTrue("The selected navigation lens must not reintroduce sharp page text",
            lensValues.max() - lensValues.min() < .12f)
        compose.runOnIdle { level = 0f }
        compose.waitForIdle()
        val transparent = control.captureToImage().toPixelMap()
        val transparentValues = (48 until 188).map { transparent[it, 32].red }
        assertTrue("0% must show the source rather than a fixed blur", transparentValues.max() - transparentValues.min() > .8f)
        val left = control.fetchSemanticsNode().boundsInRoot.left.toInt()
        val refracted = (4 until 16).count { x ->
            val unwarped = if ((left + x) % 4 < 2) 0f else 1f
            kotlin.math.abs(transparent[x, 32].red - unwarped) > .15f
        }
        assertTrue("The clear end of the range must retain the original edge refraction", refracted >= 3)
        compose.runOnIdle { level = 55f; broadBackground = true }
        compose.waitForIdle()
        for (tag in listOf("clear_control", "readable_panel")) {
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            val y = if (tag == "clear_control") 32 else 196
            assertTrue("$tag must still transmit broad background colour, not an opaque inset",
                kotlin.math.abs(pixels[55, y].red - pixels[180, y].red) > .15f)
        }
        compose.runOnIdle { level = 100f }
        compose.waitForIdle()
        val opaque = control.captureToImage().toPixelMap()
        assertTrue("100% is the explicit opaque end of the user's range",
            kotlin.math.abs(opaque[55, 32].red - opaque[180, 32].red) < .02f)
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { assertTrue("Material rendering must not intercept content input", closed) }
    }
}
