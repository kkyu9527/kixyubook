// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuGlassEffectEnabled
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuGlassFrostLevel
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import kotlin.math.pow

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
/** The only visual-token source for every real-backdrop and cross-window glass surface. */
private object KixyuGlassStyle {
    const val MaxBlurDp = 40f
    const val HighlightAlpha = .75f
    const val SimulatedFrostGamma = .4f
    const val LightShadowAlpha = .1f
    const val DarkShadowAlpha = .2f
    const val PopupHighlightTopAlpha = .12f
    const val PopupHighlightBottomAlpha = .04f
    const val DarkIndicatorVeilAlpha = .14f
    const val LightIndicatorVeilAlpha = .22f
    const val PressedIndicatorShadeAlpha = .03f
    val ShadowRadius = 10.dp

    val Specular = Highlight(
        width = 1.dp,
        alpha = 1f,
        style = BloomStroke(
            color = Color.White.copy(alpha = PopupHighlightTopAlpha),
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

internal fun Float.kixyuFrostFraction(): Float = coerceIn(0f, 100f) / 100f
internal fun Float.kixyuFrostBlurDp(): Float = KixyuGlassStyle.MaxBlurDp * kixyuFrostFraction()

/**
 * Popup/Dialog windows cannot safely sample the page layer used by [drawBackdrop]. Compensate for
 * that missing opaque blurred image with a perceptual veil curve. The setting endpoints remain
 * exact: zero is fully transparent and one hundred is fully opaque.
 */
internal fun Float.kixyuSimulatedFrostFraction(): Float =
    kixyuFrostFraction().pow(KixyuGlassStyle.SimulatedFrostGamma)

/**
 * A popup window with platform blur already owns the blurred image that real-backdrop glass gets
 * from [drawBackdrop]. Keep its veil linear like the navigation/directory surfaces; only windows
 * that cannot blur need the denser simulated curve.
 */
internal fun Float.kixyuPopupFrostFraction(windowBlurred: Boolean): Float =
    if (windowBlurred) {
        kixyuFrostFraction()
    } else {
        kixyuSimulatedFrostFraction()
    }

/** Cross-window blur can be disabled by the device, accessibility or power-saving policy. */
@Composable
internal fun kixyuCrossWindowBlurAvailable(): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false
    val windowManager = LocalContext.current.getSystemService(android.view.WindowManager::class.java)
    return windowManager.isCrossWindowBlurEnabled
}

internal fun BackdropEffectScope.kixyuFrostedGlassEffects(frostLevel: Float) {
    val frostFraction = frostLevel.kixyuFrostFraction()
    if (frostFraction == 0f) return
    colorControls(brightness = .01f, contrast = 1.03f, saturation = 1.1f)
    val blurRadiusPx = frostLevel.kixyuFrostBlurDp().dp.toPx()
    blur(blurRadiusPx, blurRadiusPx)
}

internal fun Color.kixyuFrostedGlassTint(frostLevel: Float): Color =
    copy(alpha = frostLevel.kixyuFrostFraction())

internal fun kixyuFrostedGlassHighlight(
    frostLevel: Float,
    alphaMultiplier: Float = KixyuGlassStyle.HighlightAlpha,
): Highlight = KixyuGlassStyle.Specular.copy(
    alpha = frostLevel.kixyuFrostFraction() * alphaMultiplier,
)

/** Shared interaction veil for the navigation selection lens; no visual tokens live at call sites. */
internal fun DrawScope.drawKixyuGlassIndicatorVeil(
    isDark: Boolean,
    frostFraction: Float,
    pressProgress: Float,
) {
    drawRect(
        color = Color.White.copy(
            alpha = (if (isDark) {
                KixyuGlassStyle.DarkIndicatorVeilAlpha
            } else {
                KixyuGlassStyle.LightIndicatorVeilAlpha
            }) * frostFraction,
        ),
        alpha = 1f - pressProgress * .35f,
    )
    drawRect(
        Color.Black.copy(
            alpha = KixyuGlassStyle.PressedIndicatorShadeAlpha *
                pressProgress * frostFraction,
        ),
    )
}

/**
 * Glass boundary for content rendered in a popup or dialog window.
 *
 * A [LayerBackdrop] may only be sampled by a sibling of the layer that owns it. Popup content can
 * live inside that captured layer (Snackbar) or in another Android window (Dialog/Popup), so using
 * [drawBackdrop] there creates a self-sampling render loop or an invalid cross-window read. This
 * surface keeps the shared frosted tint, highlight and shadow without sampling an unsafe backdrop.
 * Dialog windows add platform blur behind this surface where Android supports it.
 */
@Composable
internal fun Modifier.kixyuPopupGlassSurfaceModifier(
    shape: Shape,
    fallbackContainerColor: Color,
    windowBlurred: Boolean = false,
): Modifier {
    val glassEnabled = LocalKixyuGlassEffectEnabled.current
    val frostLevel = LocalKixyuGlassFrostLevel.current
    val frostFraction = frostLevel.kixyuPopupFrostFraction(
        windowBlurred = windowBlurred && kixyuCrossWindowBlurAvailable(),
    )
    val isDark = fallbackContainerColor.luminance() < .5f
    val tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = frostFraction)
    val highlightBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(
                alpha = KixyuGlassStyle.PopupHighlightTopAlpha *
                    KixyuGlassStyle.HighlightAlpha * frostFraction,
            ),
            Color.White.copy(
                alpha = KixyuGlassStyle.PopupHighlightBottomAlpha *
                    KixyuGlassStyle.HighlightAlpha * frostFraction,
            ),
        ),
    )
    return dropShadow(
        shape = shape,
        shadow = Shadow(
            radius = KixyuGlassStyle.ShadowRadius,
            color = Color.Black,
            alpha = if (isDark) {
                KixyuGlassStyle.DarkShadowAlpha
            } else {
                KixyuGlassStyle.LightShadowAlpha
            },
        ),
    ).clip(shape).then(
        if (glassEnabled) {
            Modifier
                .background(tint, shape)
                .border(1.dp, highlightBrush, shape)
        } else {
            Modifier.background(fallbackContainerColor, shape)
        },
    )
}

/**
 * Shared frosted-glass surface for floating controls.
 *
 * It uses the same blur, tint and fallback policy as the app navigation capsule.
 * Callers own shape, content and the opaque fallback only. When glass is enabled, tint, blur,
 * highlight and shadow always use the navigation-container recipe above.
 */
@Composable
fun KixyuGlassSurface(
    backdrop: KixyuNavigationBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    fallbackContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.kixyuGlassSurfaceModifier(
            backdrop = backdrop,
            shape = shape,
            fallbackContainerColor = fallbackContainerColor,
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
    fallbackContainerColor: Color,
    surfaceBackdrop: LayerBackdrop? = null,
): Modifier {
    val glassAvailable = LocalKixyuGlassEffectEnabled.current && remember { isRuntimeShaderSupported() }
    val glassEnabled = LocalKixyuGlassEffectEnabled.current
    val frostLevel = LocalKixyuGlassFrostLevel.current
    val isDark = fallbackContainerColor.luminance() < .5f
    val glassTintColor = MaterialTheme.colorScheme.surfaceContainer
    val highlight = kixyuFrostedGlassHighlight(frostLevel)
    return dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = KixyuGlassStyle.ShadowRadius,
                    color = Color.Black,
                    alpha = if (isDark) {
                        KixyuGlassStyle.DarkShadowAlpha
                    } else {
                        KixyuGlassStyle.LightShadowAlpha
                    },
                ),
            )
        .then(
                if (glassAvailable) {
                    (surfaceBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier).drawBackdrop(
                        backdrop = backdrop.value,
                        shape = { shape },
                        effects = {
                            kixyuFrostedGlassEffects(frostLevel)
                        },
                        highlight = { highlight },
                        onDrawSurface = { drawRect(glassTintColor.kixyuFrostedGlassTint(frostLevel)) },
                    )
                } else if (glassEnabled) {
                    Modifier.background(glassTintColor.kixyuFrostedGlassTint(frostLevel), shape)
                } else {
                    Modifier.background(fallbackContainerColor, shape)
                },
            )
}
