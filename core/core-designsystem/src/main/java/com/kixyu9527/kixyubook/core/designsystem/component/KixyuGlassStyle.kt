package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import kotlin.math.sqrt
import com.kixyu9527.kixyubook.core.common.model.DEFAULT_GLASS_FROST_LEVEL

/** Semantic intent only; optical values remain private to the design system. */
enum class KixyuGlassRole { CONTROL, CONTENT }

/** One frosted material family. Roles adjust thickness, never paint a separate opaque inset. */
internal object KixyuGlassStyle {
    val islandShape = Capsule()

    fun sanitizedFrostLevel(level: Float): Float =
        if (level.isFinite()) level.coerceIn(0f, 100f) else DEFAULT_GLASS_FROST_LEVEL

    fun frostRadius(role: KixyuGlassRole, level: Float = DEFAULT_GLASS_FROST_LEVEL) =
        (if (role == KixyuGlassRole.CONTROL) 28.dp else 40.dp) * (sanitizedFrostLevel(level) / 100f)

    // Glasmio Clear's original inner-ray geometry. Keep this shared by every surface and
    // the settled selection lens; local frost protects readability across the entire surface.
    val refractionHeight = 18.dp
    val refractionAmount = 32.5.dp
    const val highlightAlpha = .45f
    val edgeWidth = .75.dp

    fun edgeBrush(isDark: Boolean): Brush = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) .30f else .60f),
            Color.White.copy(alpha = .06f),
            Color.Black.copy(alpha = if (isDark) .12f else .14f),
            Color.White.copy(alpha = if (isDark) .18f else .35f),
        ),
    )

    /** Preserve small-control detail without allocating full-screen full-resolution textures. */
    fun samplingScale(role: KixyuGlassRole, pixelArea: Float): Float {
        if (!pixelArea.isFinite() || pixelArea <= 0f) return 1f
        val ceiling = if (role == KixyuGlassRole.CONTROL) 1f else .5f
        val budget = if (role == KixyuGlassRole.CONTROL) 384_000f else 512_000f
        return minOf(ceiling, sqrt(budget / pixelArea))
    }

    fun surfaceTint(role: KixyuGlassRole, isDark: Boolean, sampled: Boolean,
        level: Float = DEFAULT_GLASS_FROST_LEVEL,
    ): Color {
        val strength = sanitizedFrostLevel(level) / 100f
        val baseline = when {
            !sampled -> if (isDark) .86f else .78f
            role == KixyuGlassRole.CONTENT -> if (isDark) .74f else .60f
            else -> if (isDark) .68f else .52f
        }
        // Zero is genuinely clear; the default suppresses competing text while transmitting
        // background colour. Only a user's explicit 100% choice becomes fully opaque.
        val alpha = if (strength <= .55f) baseline * (strength / .55f) else {
            val extra = (strength - .55f) / .45f
            baseline + (1f - baseline) * extra * extra
        }
        return (if (isDark) Color(0xFF1C1C1E) else Color.White).copy(alpha = alpha)
    }

    // Native windows retain a subtle shared backdrop treatment. In particular the reader
    // selection toolbar never blurs the window.
    fun windowBlurRadius(level: Float) = 12.dp * (sanitizedFrostLevel(level) / 100f)

    fun fallbackTint(isDark: Boolean, level: Float = DEFAULT_GLASS_FROST_LEVEL): Color =
        surfaceTint(KixyuGlassRole.CONTROL, isDark, sampled = false, level = level)

    fun selectionTint(contentColor: Color, isDark: Boolean): Color =
        contentColor.copy(alpha = if (isDark) .12f else .08f)
}
