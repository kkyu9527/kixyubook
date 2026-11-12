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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuGlassEffectEnabled

/** Backdrop of the current page, shared by navigation, reader controls and popup hosts. */
val LocalKixyuGlassBackdrop = staticCompositionLocalOf<KixyuNavigationBackdrop?> { null }

/** Cross-window blur can be disabled by the device, accessibility or power-saving policy. */
@Composable
internal fun kixyuCrossWindowBlurAvailable(): Boolean {
    val windowManager = LocalContext.current.getSystemService(android.view.WindowManager::class.java)
    return windowManager.isCrossWindowBlurEnabled
}

/**
 * Shared liquid-glass surface for floating controls.
 *
 * The page is recorded once through [KixyuNavigationBackdrop]; Glasmio renders the material and
 * its native edge treatment from that recording. Callers own shape, content and the opaque
 * fallback only, so neither features nor tests need to know the rendering library.
 */
@Composable
fun KixyuGlassSurface(
    backdrop: KixyuNavigationBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    fallbackContainerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    role: KixyuGlassRole = KixyuGlassRole.CONTROL,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!LocalKixyuGlassEffectEnabled.current) {
        Box(modifier.background(fallbackContainerColor, shape)) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    } else {
        KixyuGlasmioSurface(
            backdrop = backdrop,
            modifier = modifier,
            shape = shape,
            contentColor = contentColor,
            role = role,
            content = content,
        )
    }
}

/**
 * Unified in-page floating surface. Reader overlays sample the navigation backdrop; destinations
 * without one fall back to the safe popup material.
 */
@Composable
fun KixyuBackdropAwareInteractiveSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    fallbackContainerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    role: KixyuGlassRole = KixyuGlassRole.CONTENT,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalKixyuGlassBackdrop.current
    val interactiveModifier = modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent()
        }
    }
    if (backdrop != null) {
        KixyuGlassSurface(
            backdrop = backdrop,
            modifier = interactiveModifier,
            shape = shape,
            fallbackContainerColor = fallbackContainerColor,
            role = role,
            content = content,
        )
    } else {
        KixyuPopupSurface(
            modifier = interactiveModifier,
            shape = shape,
            containerColor = fallbackContainerColor,
            role = role,
            shadowElevation = androidx.compose.ui.unit.Dp(0f),
        ) {
            Box(content = content)
        }
    }
}
