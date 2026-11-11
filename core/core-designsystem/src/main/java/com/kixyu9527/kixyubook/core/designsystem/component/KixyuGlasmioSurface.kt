package com.kixyu9527.kixyubook.core.designsystem.component

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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop

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
    content: @Composable BoxScope.() -> Unit,
) {
    var consumerCoordinates by remember(backdrop) { mutableStateOf<LayoutCoordinates?>(null) }
    val sampleBackdrop = canSampleKixyuGlassBackdrop(backdrop?.coordinates, consumerCoordinates)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < .5f
    GlassSurface(
        modifier = modifier.onGloballyPositioned { consumerCoordinates = it },
        backdrop = if (sampleBackdrop) requireNotNull(backdrop).value else emptyBackdrop(),
        shape = shape,
        sampleBackdrop = sampleBackdrop,
        // Missing/unsafe sources use the same neutral veil, not an opaque theme container.
        navigationSurfaceColor = KixyuGlassStyle.fallbackTint(isDark).takeUnless { sampleBackdrop },
        navigationSurfaceAlphaMultiplier = if (sampleBackdrop) 1.25f else 1f,
        material = KixyuGlassStyle.material,
        frostRadius = KixyuGlassStyle.frostRadius,
        preserveBackdropColors = KixyuGlassStyle.preserveBackdropColors,
        exportedBackdrop = exportedBackdrop,
        contentAlignment = Alignment.Center,
        content = {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        },
    )
}
