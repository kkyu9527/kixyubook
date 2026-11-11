package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * The one liquid-glass island used by the navigation bar, the long-press action bar and the
 * reader dock. Geometry comes from KixyuGlassStyle; the shared renderer owns the material,
 * so these controls cannot drift apart through per-call optical overrides.
 */
@Composable
fun KixyuGlassIsland(
    backdrop: KixyuNavigationBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = KixyuGlassStyle.islandShape,
    fallbackContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    KixyuGlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        fallbackContainerColor = fallbackContainerColor,
        contentColor = contentColor,
        content = content,
    )
}
