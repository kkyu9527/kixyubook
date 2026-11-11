package com.kixyu9527.kixyubook.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.kixyu9527.kixyubook.core.common.model.AppColorTheme
import org.junit.Assert.assertTrue
import org.junit.Test

class KixyuThemeContrastTest {
    @Test
    fun lightPalettesKeepTextAndControlContrastAboveWcagThresholds() {
        assertPalette("DEFAULT", lightColorScheme())
        listOf(AppColorTheme.SAGE, AppColorTheme.OCEAN, AppColorTheme.VIOLET, AppColorTheme.AMBER).forEach { theme ->
            assertPalette(theme.name, lightColors(theme))
        }
    }

    @Test
    fun darkPalettesKeepTextAndControlContrastAboveWcagThresholds() {
        assertPalette("DEFAULT", darkColorScheme())
        listOf(AppColorTheme.SAGE, AppColorTheme.OCEAN, AppColorTheme.VIOLET, AppColorTheme.AMBER).forEach { theme ->
            assertPalette(theme.name, darkColors(theme))
        }
    }

    private fun assertPalette(name: String, scheme: ColorScheme) {
        assertAtLeast("$name onSurface/background", scheme.onSurface, scheme.background, 4.5)
        assertAtLeast("$name onSurface/surfaceContainerLow", scheme.onSurface, scheme.surfaceContainerLow, 4.5)
        assertAtLeast("$name onSurfaceVariant/background", scheme.onSurfaceVariant, scheme.background, 4.5)
        assertAtLeast("$name onSurfaceVariant/surfaceContainerLow", scheme.onSurfaceVariant, scheme.surfaceContainerLow, 4.5)
        assertAtLeast("$name onPrimary/primary", scheme.onPrimary, scheme.primary, 4.5)
        assertAtLeast("$name onSecondary/secondary", scheme.onSecondary, scheme.secondary, 4.5)
        assertAtLeast("$name onTertiary/tertiary", scheme.onTertiary, scheme.tertiary, 4.5)
        assertAtLeast("$name outline/background", scheme.outline, scheme.background, 3.0)
    }

    private fun assertAtLeast(label: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label contrast was $ratio, expected >= $minimum", ratio >= minimum)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = maxOf(luminance(first), luminance(second))
        val darker = minOf(luminance(first), luminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
