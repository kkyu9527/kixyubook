package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.lens
import io.github.neoruaa.glasmio.glass.render.highlight.Highlight
import io.github.neoruaa.glasmio.glass.render.highlight.HighlightStyle

/** A consumer inside its own recorded source would create a cyclic RenderNode graph. */
internal fun canSampleKixyuGlassBackdrop(
    source: LayoutCoordinates?,
    consumer: LayoutCoordinates?,
): Boolean {
    if (source?.isAttached != true || consumer?.isAttached != true) return false
    var ancestor: LayoutCoordinates? = consumer
    while (ancestor != null) {
        if (ancestor === source) return false
        ancestor = ancestor.parentCoordinates
    }
    return true
}

/**
 * Recording and sampling use the same backend. In particular the source must retain its
 * neighbouring pixels and notify consumers when it changes; a cross-library bridge loses
 * those invalidations and applies the press transform twice.
 */
@Composable
internal fun KixyuGlasmioSurface(
    backdrop: KixyuNavigationBackdrop?,
    modifier: Modifier = Modifier,
    shape: Shape,
    contentColor: androidx.compose.ui.graphics.Color,
    exportedBackdrop: LayerBackdrop? = null,
    role: KixyuGlassRole = KixyuGlassRole.CONTROL,
    content: @Composable BoxScope.() -> Unit,
) {
    var consumerCoordinates by remember(backdrop) { mutableStateOf<LayoutCoordinates?>(null) }
    val sampleBackdrop = canSampleKixyuGlassBackdrop(backdrop?.coordinates, consumerCoordinates)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val tint = KixyuGlassStyle.surfaceTint(role, isDark, sampleBackdrop)
    val edge = remember(isDark) { KixyuGlassStyle.edgeBrush(isDark) }
    val highlight = remember {
        Highlight.Default.copy(alpha = KixyuGlassStyle.highlightAlpha, style = HighlightStyle.Default(clear = true))
    }
    // Preserve the original Clear ray while keeping capture resolution and legibility
    // independently managed. A low blur radius must not also weaken the glass curvature.
    Box(
        modifier = modifier
            .onGloballyPositioned { consumerCoordinates = it }
            .clip(shape)
            .drawBackdrop(
                backdrop = if (sampleBackdrop) requireNotNull(backdrop).value else emptyBackdrop(),
                shape = { shape },
                exportedBackdrop = exportedBackdrop,
                samplingScale = { size, density ->
                    val support = with(density) {
                        KixyuGlassStyle.frostRadius(role).toPx() * 3f
                    }
                    KixyuGlassStyle.samplingScale(role, (size.width + support * 2f) * (size.height + support * 2f))
                },
                effects = {
                    if (sampleBackdrop) {
                        val frost = KixyuGlassStyle.frostRadius(role).toPx()
                        val height = KixyuGlassStyle.refractionHeight.toPx()
                        val amount = KixyuGlassStyle.refractionAmount.toPx()
                        // The Clear inner ray samples inward. Only Gaussian support needs
                        // extra pixels; padding by the ray amount wastes control resolution.
                        padding = maxOf(padding, frost * 3f)
                        if (frost > 0f) blur(frost)
                        lens(height, amount)
                    }
                },
                highlight = { highlight },
                shadow = null,
                innerShadow = null,
                onDrawSurface = {
                    if (role == KixyuGlassRole.CONTROL || !sampleBackdrop) drawRect(tint)
                },
            )
            .then(
                if (role == KixyuGlassRole.CONTENT && sampleBackdrop) {
                    Modifier.kixyuGlassContentBacking(shape, tint)
                } else Modifier,
            )
            .border(KixyuGlassStyle.edgeWidth, edge, shape),
        contentAlignment = Alignment.Center,
        content = {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        },
    )
}
