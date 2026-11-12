package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KixyuWavyGeometryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialKeepsLowProgressCurved() = verify(AppUiStyle.MATERIAL, ReaderTheme.DAY)
    @Test fun miuixKeepsLowProgressCurved() = verify(AppUiStyle.MIUIX, ReaderTheme.DAY)
    @Test fun darkMaterialKeepsLowProgressCurved() = verify(AppUiStyle.MATERIAL, ReaderTheme.NIGHT)
    @Test fun darkMiuixKeepsLowProgressCurved() = verify(AppUiStyle.MIUIX, ReaderTheme.NIGHT)
    @Test fun materialRtlKeepsLowProgressCurved() = verify(AppUiStyle.MATERIAL, ReaderTheme.DAY, LayoutDirection.Rtl)
    @Test fun miuixRtlKeepsLowProgressCurved() = verify(AppUiStyle.MIUIX, ReaderTheme.DAY, LayoutDirection.Rtl)

    private fun verify(style: AppUiStyle, theme: ReaderTheme, direction: LayoutDirection = LayoutDirection.Ltr) {
        val fractions = listOf(0f, .01f, .05f, .099f, .101f, .99f, 1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KixyuBookTheme(uiStyle = style, themeMode = theme) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Column(Modifier.width(320.dp).background(Color.White)) {
                        fractions.forEach { fraction ->
                            KixyuProgressBar(
                                fraction,
                                Modifier.testTag("wave_$fraction"),
                                color = Color.Blue,
                                wavy = true,
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        fractions.forEach { fraction ->
            val node = compose.onNodeWithTag("wave_$fraction")
            node.assertIsDisplayed()
            compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(fraction, 0f..1f))).assertIsDisplayed()
            val pixels = node.captureToImage().toPixelMap()
            val columns = (0 until pixels.width).map { offset ->
                val x = if (direction == LayoutDirection.Rtl) pixels.width - 1 - offset else offset
                (0 until pixels.height).filter { y -> pixels[x, y].red < .25f && pixels[x, y].blue > .8f }
            }
            if (fraction == 0f) {
                assertTrue("Zero progress must not paint an active segment", columns.all { it.isEmpty() })
            } else if (fraction == .01f) {
                // A very short segment can look like a dot; never lengthen it to fake a wave.
                assertTrue("Tiny progress must retain its true length", columns.count { it.isNotEmpty() } in 1..8)
            } else {
                val centers = columns.drop(3).take(10).filter { it.isNotEmpty() }.map { it.average() }
                assertEquals(10, centers.size)
                assertTrue("Active progress must remain curved at $fraction", centers.max() - centers.min() > 1.0)
            }
        }
    }
}
