package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline

/** Keep text protected without painting an opaque veil over the refracting perimeter. */
internal fun Modifier.kixyuGlassContentBacking(shape: Shape, tint: Color): Modifier = drawWithCache {
    val inset = minOf(KixyuGlassStyle.contentBackingInset.toPx(), size.minDimension / 2f)
    val backing = Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
        if (size.width > 0f && size.height > 0f) {
            transform(Matrix().apply {
                // Scaling the outline preserves arbitrary continuous/capsule shapes and
                // caches the resulting path. This adds no content padding or offscreen layer.
                translate(inset, inset)
                scale((size.width - inset * 2f) / size.width, (size.height - inset * 2f) / size.height)
            })
        }
    }
    onDrawBehind { drawPath(backing, tint) }
}
