// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Adapted from compose-miuix-ui/miuix and SukiSU-Ultra/SukiSU-Ultra.

package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalLayoutDirection
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

/** Keeps the MIUIX backdrop type out of app and feature module APIs. */
@Stable
class KixyuNavigationBackdrop internal constructor(
    internal val value: LayerBackdrop,
)

@Composable
fun rememberKixyuNavigationBackdrop(backgroundColor: Color): KixyuNavigationBackdrop =
    key(backgroundColor) {
        val backdrop = rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }
        remember(backdrop) { KixyuNavigationBackdrop(backdrop) }
    }

fun Modifier.kixyuNavigationBackdrop(backdrop: KixyuNavigationBackdrop): Modifier =
    layerBackdrop(backdrop.value)

/**
 * KSU-compatible floating navigation geometry shared by MIUIX and Material modes.
 *
 * On Android 13+ the bar samples and refracts the page beneath it. On unsupported devices the
 * exact same 64 dp capsule and 56 dp selection indicator are retained with an opaque surface.
 */
@Composable
internal fun KixyuFloatingNavigationBar(
    items: List<KixyuNavigationItem>,
    selectedKey: String?,
    onSelected: (KixyuNavigationItem) -> Unit,
    backdrop: KixyuNavigationBackdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (items.isEmpty()) return

    val selectedIndex = items.indexOfFirst { it.route == selectedKey }.coerceAtLeast(0)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 1f)
    val unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
    val isDark = surfaceContainer.luminance() < 0.5f
    val glassAvailable = remember { isRuntimeShaderSupported() }
    val shape = CircleShape
    val itemKeys = remember(items) { items.map(KixyuNavigationItem::route) }
    var currentIndex by remember(itemKeys) { mutableIntStateOf(selectedIndex) }
    var dragPosition by remember(itemKeys) { mutableFloatStateOf(selectedIndex.toFloat()) }
    var indicatorDragging by remember(itemKeys) { mutableStateOf(false) }
    LaunchedEffect(selectedIndex) {
        currentIndex = selectedIndex
        if (!indicatorDragging) {
            dragPosition = selectedIndex.toFloat()
        }
    }
    val interactionSources = remember(itemKeys) {
        List(items.size) { MutableInteractionSource() }
    }
    val pressedStates = interactionSources.map { it.collectIsPressedAsState() }
    val pressedIndex = pressedStates.indexOfFirst { it.value }
    val selectedPressed = pressedIndex >= 0 || indicatorDragging
    val pressProgress by animateFloatAsState(
        targetValue = if (selectedPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
        label = "navigationPress",
    )
    val restingVisualIndex = if (pressedIndex >= 0) pressedIndex else currentIndex
    val indicatorPosition by animateFloatAsState(
        targetValue = if (indicatorDragging) dragPosition else restingVisualIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "navigationIndicatorPosition",
    )
    val logicalIndicatorPosition = if (isLtr) {
        indicatorPosition
    } else {
        items.lastIndex - indicatorPosition
    }
    val indicatorOffset = KixyuSize.bottomNavigationInnerPadding +
        KixyuSize.bottomNavigationItemWidth * logicalIndicatorPosition
    val visuallySelectedIndex = if (indicatorDragging) {
        navigationDragTargetIndex(indicatorPosition, items.size)
    } else {
        currentIndex
    }
    val containerColor = if (glassAvailable) {
        surfaceContainer.copy(alpha = 0.4f)
    } else {
        surfaceContainer
    }
    val baseHighlight = rememberGravityRotatedHighlight(
        base = iosIndicatorSpecular,
        extraDegrees = -45f,
    )
    val indicatorHighlight = rememberGravityRotatedHighlight(
        base = iosIndicatorSpecular,
        extraDegrees = 90f,
    )
    val navigationSurfaceBackdrop = rememberLayerBackdrop()
    val selectedIndicatorBackdrop = rememberKixyuCombinedBackdrop(
        page = backdrop.value,
        controlSurface = navigationSurfaceBackdrop,
    )

    Box(
        modifier = modifier
            .width(
                KixyuSize.bottomNavigationItemWidth * items.size +
                    KixyuSize.bottomNavigationInnerPadding * 2,
            )
            .height(KixyuSize.bottomNavigationBarHeight)
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 10.dp,
                    color = Color.Black,
                    alpha = if (isDark) 0.2f else 0.1f,
                ),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (glassAvailable) {
                        Modifier
                            .layerBackdrop(navigationSurfaceBackdrop)
                            .drawBackdrop(
                                backdrop = backdrop.value,
                                shape = { shape },
                                effects = {
                                    vibrancy()
                                    blur(4.dp.toPx(), 4.dp.toPx())
                                    kixyuLens(
                                        refractionHeight = 24.dp.toPx(),
                                        refractionAmount = 24.dp.toPx(),
                                    )
                                },
                                highlight = { baseHighlight.copy(alpha = 0.75f) },
                                onDrawSurface = { drawRect(containerColor) },
                            )
                    } else {
                        Modifier.background(containerColor, shape)
                    },
                ),
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = indicatorOffset.toPx()
                    val scale = lerp(1f, 78f / 56f, pressProgress)
                    scaleX = scale
                    scaleY = scale
                }
                .width(KixyuSize.bottomNavigationItemWidth)
                .height(KixyuSize.bottomNavigationIndicatorHeight)
                .then(
                    if (glassAvailable) {
                        Modifier.drawBackdrop(
                            backdrop = selectedIndicatorBackdrop,
                            shape = { shape },
                            effects = {
                                kixyuLens(
                                    refractionHeight = 10.dp.toPx() * pressProgress,
                                    refractionAmount = 14.dp.toPx() * pressProgress,
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            highlight = { indicatorHighlight.copy(alpha = pressProgress) },
                            onDrawSurface = {
                                drawRect(
                                    color = if (isDark) {
                                        Color.White.copy(alpha = 0.1f)
                                    } else {
                                        Color.Black.copy(alpha = 0.1f)
                                    },
                                    alpha = 1f - pressProgress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * pressProgress))
                            },
                        )
                    } else {
                        Modifier.background(accentColor.copy(alpha = 0.15f), shape)
                    },
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KixyuSize.bottomNavigationInnerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = currentIndex == index
                val itemColor by animateColorAsState(
                    targetValue = if (visuallySelectedIndex == index) {
                        accentColor
                    } else {
                        unselectedColor
                    },
                    label = "navigationItemColor",
                )
                Column(
                    modifier = Modifier
                        .width(KixyuSize.bottomNavigationItemWidth)
                        .fillMaxHeight()
                        .then(
                            if (selected && enabled) {
                                Modifier.pointerInput(item.route, items.size, isLtr, currentIndex) {
                                    val itemWidthPx = KixyuSize.bottomNavigationItemWidth.toPx()
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            indicatorDragging = true
                                            dragPosition = currentIndex.toFloat()
                                        },
                                        onDragEnd = {
                                            val targetIndex = navigationDragTargetIndex(
                                                position = dragPosition,
                                                itemCount = items.size,
                                            )
                                            val pageChanged = targetIndex != currentIndex
                                            currentIndex = targetIndex
                                            dragPosition = targetIndex.toFloat()
                                            indicatorDragging = false
                                            if (pageChanged) {
                                                onSelected(items[targetIndex])
                                            }
                                        },
                                        onDragCancel = {
                                            dragPosition = currentIndex.toFloat()
                                            indicatorDragging = false
                                        },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        dragPosition = updateNavigationDragPosition(
                                            currentPosition = dragPosition,
                                            dragAmountPx = dragAmount,
                                            itemWidthPx = itemWidthPx,
                                            itemCount = items.size,
                                            isLtr = isLtr,
                                        )
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )
                        .selectable(
                            selected = selected,
                            interactionSource = interactionSources[index],
                            indication = null,
                            enabled = enabled,
                            role = Role.Tab,
                            onClick = {
                                currentIndex = index
                                onSelected(item)
                            },
                        ),
                    verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = itemColor,
                    )
                    Text(
                        text = item.label,
                        color = itemColor,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

internal fun updateNavigationDragPosition(
    currentPosition: Float,
    dragAmountPx: Float,
    itemWidthPx: Float,
    itemCount: Int,
    isLtr: Boolean,
): Float {
    if (itemCount <= 0 || itemWidthPx <= 0f) return currentPosition
    val direction = if (isLtr) 1f else -1f
    return (currentPosition + dragAmountPx / itemWidthPx * direction)
        .coerceIn(0f, (itemCount - 1).toFloat())
}

internal fun navigationDragTargetIndex(position: Float, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    return position.roundToInt().coerceIn(0, itemCount - 1)
}

private fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}

private val iosIndicatorSpecular = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

@Composable
private fun rememberGravityRotatedHighlight(
    base: Highlight,
    extraDegrees: Float,
): Highlight {
    val baseStyle = base.style as BloomStroke
    val tilt by rememberDeviceTilt()
    val rotatedPrimary = remember(tilt, baseStyle.primaryLight, extraDegrees) {
        val gravityX = tilt.gravityX
        val gravityY = tilt.gravityY
        val gravityMagnitudeSquared = gravityX * gravityX + gravityY * gravityY
        val (lightX, lightY) = if (gravityMagnitudeSquared > GRAVITY_DIR_THRESHOLD_SQ) {
            val inverseMagnitude = 1f / sqrt(gravityMagnitudeSquared)
            gravityX * inverseMagnitude to gravityY * inverseMagnitude
        } else {
            0f to -1f
        }
        val radians = extraDegrees * PI / 180.0
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        baseStyle.primaryLight.copy(
            position = LightPosition(
                x = LIGHT_REF_X + cosine * lightX - sine * lightY,
                y = LIGHT_REF_Y + sine * lightX + cosine * lightY,
                z = baseStyle.primaryLight.position.z,
            ),
        )
    }
    return remember(base, rotatedPrimary) {
        base.copy(style = baseStyle.copy(primaryLight = rotatedPrimary))
    }
}
