package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import kotlin.math.sqrt

/** Semantic intent only; optical values remain private to the design system. */
enum class KixyuGlassRole { CONTROL, CONTENT }

/** One material family with clear chrome and a protected text-reading surface. */
internal object KixyuGlassStyle {
    val islandShape = Capsule()

    fun frostRadius(role: KixyuGlassRole) = when (role) {
        KixyuGlassRole.CONTROL -> 0.dp
        KixyuGlassRole.CONTENT -> .75.dp
    }

    // Glasmio Clear's original inner-ray geometry. Keep this shared by every surface and
    // the settled selection lens; readability is protected by a separate content backing.
    val refractionHeight = 18.dp
    val refractionAmount = 32.5.dp
    val contentBackingInset = 4.dp
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

    fun surfaceTint(role: KixyuGlassRole, isDark: Boolean, sampled: Boolean): Color {
        val alpha = when (role) {
            KixyuGlassRole.CONTROL -> if (sampled) .06f else .12f
            KixyuGlassRole.CONTENT -> .96f
        }
        return (if (isDark) Color(0xFF1C1C1E) else Color.White).copy(alpha = alpha)
    }

    // Native windows retain only the subtle shared backdrop treatment; readable panels own
    // their local backing. In particular the reader selection toolbar never blurs the window.
    val windowBlurRadius = .75.dp

    fun fallbackTint(isDark: Boolean): Color = surfaceTint(KixyuGlassRole.CONTROL, isDark, sampled = false)

    fun selectionTint(contentColor: Color, isDark: Boolean): Color =
        contentColor.copy(alpha = if (isDark) .12f else .08f)
}
