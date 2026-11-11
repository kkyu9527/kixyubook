package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KixyuGlassTest {
    @Test
    fun everySurfaceUsesTheClearNavigationRecipe() {
        assertEquals(NavigationGlassMaterial.Clear, KixyuGlassStyle.material)
        assertEquals(.75.dp, KixyuGlassStyle.frostRadius)
        assertTrue(KixyuGlassStyle.preserveBackdropColors)
    }

    @Test
    fun unsampledPopupsUseTheSameTranslucentTintInEitherTheme() {
        val light = KixyuGlassStyle.fallbackTint(false)
        val dark = KixyuGlassStyle.fallbackTint(true)
        assertEquals(.30f, light.alpha, .005f)
        assertEquals(.30f, dark.alpha, .005f)
        assertEquals(1f, light.red, .005f)
        assertTrue(dark.red < .15f)
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
