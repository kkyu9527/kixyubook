// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuGlassEffectEnabled
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuGlassBlurRadius
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

/**
 * Backdrop of the current page. Popup components read this local so every floating surface can use
 * the same glass implementation without threading a rendering object through every feature API.
 */
val LocalKixyuGlassBackdrop = staticCompositionLocalOf<KixyuNavigationBackdrop?> { null }

/**
 * One frosted-glass recipe shared by navigation, reader controls and popup surfaces.
 *
 * Keep the visual tuning here instead of allowing every feature to invent its own blur. A fairly
 * wide blur and an opaque-enough neutral veil make controls readable even above plain book pages.
 * Refraction is intentionally omitted here: shared surfaces are frosted glass, not transparent
 * liquid glass. Individual pressed indicators may still add a small interaction-only refraction.
 */
private const val KIXYU_FROSTED_TINT_MIN_ALPHA = .72f

internal fun BackdropEffectScope.kixyuFrostedGlassEffects(blurRadius: Float) {
    colorControls(brightness = .01f, contrast = 1.03f, saturation = 1.1f)
    val blurRadiusPx = blurRadius.dp.toPx()
    blur(blurRadiusPx, blurRadiusPx)
}

internal fun Color.kixyuFrostedGlassTint(): Color =
    copy(alpha = alpha.coerceAtLeast(KIXYU_FROSTED_TINT_MIN_ALPHA))

/**
 * Shared frosted-glass surface for floating controls.
 *
 * It uses the same blur, tint and fallback policy as the app navigation capsule.
 * The caller only owns semantic color: the glass tint is neutral while icons and text may retain
 * the page accent color.
 */
@Composable
fun KixyuGlassSurface(
    backdrop: KixyuNavigationBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    glassTintColor: Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .4f),
    fallbackContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shadowRadius: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.kixyuGlassSurfaceModifier(
            backdrop = backdrop,
            shape = shape,
            glassTintColor = glassTintColor,
            fallbackContainerColor = fallbackContainerColor,
            shadowRadius = shadowRadius,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/** Applies the exact shared glass boundary to components that already own their content node. */
@Composable
internal fun Modifier.kixyuGlassSurfaceModifier(
    backdrop: KixyuNavigationBackdrop,
    shape: Shape,
    glassTintColor: Color,
    fallbackContainerColor: Color,
    shadowRadius: Dp,
): Modifier {
    val glassAvailable = LocalKixyuGlassEffectEnabled.current && remember { isRuntimeShaderSupported() }
    val glassBlurRadius = LocalKixyuGlassBlurRadius.current
    val isDark = fallbackContainerColor.luminance() < .5f
    val highlight = remember {
        Highlight(
            width = 1.dp,
            alpha = .75f,
            style = BloomStroke(
                color = Color.White.copy(alpha = .12f),
                innerBlurRadius = 2.dp,
                primaryLight = LightSource(
                    position = LightPosition(.5f, -.3f, -.05f),
                    color = Color.White,
                    intensity = 1f,
                ),
                secondaryLight = LightSource(
                    position = LightPosition(.5f, .8f, -.5f),
                    color = Color.White,
                    intensity = .4f,
                ),
                dualPeak = true,
            ),
        )
    }
    return dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = shadowRadius,
                    color = Color.Black,
                    alpha = if (isDark) .2f else .1f,
                ),
            )
        .then(
                if (glassAvailable) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop.value,
                        shape = { shape },
                        effects = {
                            kixyuFrostedGlassEffects(glassBlurRadius)
                        },
                        highlight = { highlight },
                        onDrawSurface = { drawRect(glassTintColor.kixyuFrostedGlassTint()) },
                    )
                } else {
                    Modifier.background(fallbackContainerColor, shape)
                },
            )
}
