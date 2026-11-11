package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.ui.graphics.Color
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial

/** The single material recipe. Features own geometry and content, never optical overrides. */
internal object KixyuGlassStyle {
    val islandShape = Capsule()
    val material = NavigationGlassMaterial.Clear
    val frostRadius get() = material.frostRadius
    const val preserveBackdropColors = true
    const val fallbackTintAlpha = .30f

    fun fallbackTint(isDark: Boolean): Color =
        (if (isDark) Color(0xFF1C1C1E) else Color.White).copy(alpha = fallbackTintAlpha)

    fun selectionTint(contentColor: Color, isDark: Boolean): Color =
        contentColor.copy(alpha = if (isDark) .12f else .08f)
}
