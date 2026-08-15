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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuGlassEffectEnabled
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

/**
 * Shared liquid-glass surface for floating controls.
 *
 * It uses the same blur, vibrancy, refraction and fallback policy as the app navigation capsule.
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
    val glassAvailable = LocalKixyuGlassEffectEnabled.current && remember { isRuntimeShaderSupported() }
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
    Box(
        modifier = modifier
            .dropShadow(
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
                            colorControls(brightness = 0f, contrast = 1f, saturation = 1.5f)
                            blur(4.dp.toPx(), 4.dp.toPx())
                            kixyuLens(
                                refractionHeight = 24.dp.toPx(),
                                refractionAmount = 24.dp.toPx(),
                            )
                        },
                        highlight = { highlight },
                        onDrawSurface = { drawRect(glassTintColor) },
                    )
                } else {
                    Modifier.background(fallbackContainerColor, shape)
                },
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
