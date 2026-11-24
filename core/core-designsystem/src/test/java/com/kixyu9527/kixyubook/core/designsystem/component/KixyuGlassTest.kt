package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KixyuGlassTest {
    @Test
    fun bothRolesFilterCompetingTextWhileContentUsesThickerFrost() {
        assertTrue(KixyuGlassStyle.frostRadius(KixyuGlassRole.CONTROL) >= 15.dp)
        assertTrue(KixyuGlassStyle.frostRadius(KixyuGlassRole.CONTENT) > KixyuGlassStyle.frostRadius(KixyuGlassRole.CONTROL))
        assertEquals(1f, KixyuGlassStyle.samplingScale(KixyuGlassRole.CONTROL, 236f * 64 * 9), .0001f)
    }

    @Test
    fun surfaceAndSettledLensRetainTheOriginalClearRefractionGeometry() {
        assertEquals(18.dp, KixyuGlassStyle.refractionHeight)
        assertEquals(32.5.dp, KixyuGlassStyle.refractionAmount)
    }

    @Test
    fun unsampledPopupsUseTheSameTranslucentTintInEitherTheme() {
        val light = KixyuGlassStyle.fallbackTint(false)
        val dark = KixyuGlassStyle.fallbackTint(true)
        assertEquals(.78f, light.alpha, .005f)
        assertEquals(.86f, dark.alpha, .005f)
        assertEquals(1f, light.red, .005f)
        assertTrue(dark.red < .15f)
    }

    @Test
    fun theEntireTranslucentMaterialProtectsContrastWithoutAnOpaqueInset() {
        for (dark in listOf(false, true)) for (sampled in listOf(false, true)) {
            val tint = KixyuGlassStyle.surfaceTint(KixyuGlassRole.CONTENT, dark, sampled)
            assertTrue(tint.alpha < .9f)
            val foreground = if (dark) Color.White else Color.Black
            for (background in listOf(Color.Black, Color.White)) {
                val panel = tint.compositeOver(background)
                val high = maxOf(foreground.luminance(), panel.luminance())
                val low = minOf(foreground.luminance(), panel.luminance())
                assertTrue("Text contrast must remain readable", (high + .05f) / (low + .05f) >= 4.5f)
            }
        }
    }

    @Test
    fun everyRoleAndSamplingPathFollowsTheFullPercentageRange() {
        for (role in KixyuGlassRole.entries) for (dark in listOf(false, true)) {
            for (sampled in listOf(false, true)) {
                val alphas = listOf(0f, 25f, 55f, 75f, 100f).map { level ->
                    KixyuGlassStyle.surfaceTint(role, dark, sampled, level).alpha
                }
                assertEquals(0f, alphas.first(), .001f)
                assertEquals(1f, alphas.last(), .001f)
                assertTrue(alphas.zipWithNext().all { (a, b) -> a < b })
            }
            assertEquals(0.dp, KixyuGlassStyle.frostRadius(role, -1f))
            assertEquals(KixyuGlassStyle.frostRadius(role, 100f), KixyuGlassStyle.frostRadius(role, 101f))
            assertEquals(KixyuGlassStyle.frostRadius(role), KixyuGlassStyle.frostRadius(role, Float.NaN))
        }
    }

    @Test
    fun largeSurfacesStayWithinTheirCapturePixelBudget() {
        for (area in listOf(500_000f, 8_000_000f, 32_000_000f)) {
            for (role in KixyuGlassRole.entries) {
                val scale = KixyuGlassStyle.samplingScale(role, area)
                val budget = if (role == KixyuGlassRole.CONTROL) 384_000f else 512_000f
                assertTrue(scale > 0f && scale <= 1f)
                assertTrue(area * scale * scale <= budget + 1f)
            }
        }
    }

    @Test
    fun selectionTintRetainsTheThemeContentColour() {
        val accent = Color(0xFF1677FF)
        val selection = KixyuGlassStyle.selectionTint(accent, false)
        assertEquals(accent.red, selection.red, .005f)
        assertEquals(accent.blue, selection.blue, .005f)
        assertEquals(.08f, selection.alpha, .005f)
    }
}
