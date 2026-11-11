// SPDX-License-Identifier: Apache-2.0
// Layered track/lens approach adapted from Kyant0/AndroidLiquidGlass (Apache-2.0)
// and NEORUAA/Glasmio v0.1.2 (Apache-2.0), GlassBottomBar.kt.
package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuGlassEffectEnabled
import io.github.neoruaa.glasmio.glass.NativeControlLensStyle
import io.github.neoruaa.glasmio.glass.nativeControlLens
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop

/** One clear platter, one control lens, then sharp foreground tabs drawn by the caller. */
@Composable
internal fun KixyuNavigationGlass(
    backdrop: KixyuNavigationBackdrop,
    selectionModifier: Modifier,
    pressProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = KixyuGlassStyle.islandShape
    val colors = MaterialTheme.colorScheme
    val glassEnabled = LocalKixyuGlassEffectEnabled.current
    val isDark = colors.surface.luminance() < .5f
    val selectionColor = KixyuGlassStyle.selectionTint(colors.onSurface, isDark)
    Box(modifier) {
        if (glassEnabled) {
            val trackBackdrop = rememberLayerBackdrop()
            val lensBackdrop = rememberCombinedBackdrop(backdrop.value, trackBackdrop)
            KixyuGlasmioSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                contentColor = colors.onSurface,
                exportedBackdrop = trackBackdrop,
            ) {}
            Box(
                selectionModifier.drawPlainBackdrop(
                    // Sample the actual track as well as the page, never another full glass
                    // surface or the foreground labels (which must stay sharp).
                    backdrop = lensBackdrop,
                    shape = { shape },
                    effects = {
                        nativeControlLens(
                            style = NativeControlLensStyle.TabBarIndicator,
                            liftProgress = pressProgress(),
                            isDark = isDark,
                            restingColor = selectionColor,
                        )
                    },
                ),
            )
        } else {
            Box(Modifier.fillMaxSize().background(colors.surfaceContainerHigh, shape))
            Box(selectionModifier.background(selectionColor, shape))
        }
    }
}
