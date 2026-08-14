// Copyright 2026, AndroidLiquidGlass and compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Adapted from AndroidLiquidGlass, compose-miuix-ui and SukiSU-Ultra.

package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

/** Draws the page backdrop and a previously captured control surface as a single source. */
@Stable
internal class KixyuCombinedBackdrop(
    private val page: Backdrop,
    private val controlSurface: Backdrop,
) : Backdrop {
    override val isCoordinatesDependent: Boolean =
        page.isCoordinatesDependent || controlSurface.isCoordinatesDependent

    override val offsetResidualX: Float
        get() = page.offsetResidualX

    override val offsetResidualY: Float
        get() = page.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        with(page) {
            drawBackdrop(density, coordinates, layerBlock, downscaleFactor)
        }
        with(controlSurface) {
            drawBackdrop(density, coordinates, layerBlock, downscaleFactor)
        }
    }
}

@Composable
internal fun rememberKixyuCombinedBackdrop(
    page: Backdrop,
    controlSurface: Backdrop,
): Backdrop = remember(page, controlSurface) {
    KixyuCombinedBackdrop(page, controlSurface)
}
