// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Adapted from compose-miuix-ui/miuix and SukiSU-Ultra/SukiSU-Ultra.

package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

/** Keeps the rendering library out of app and feature module APIs. */
@Stable
class KixyuNavigationBackdrop internal constructor(
    internal val value: LayerBackdrop,
) {
    internal var coordinates by mutableStateOf<LayoutCoordinates?>(null)
}

@Composable
fun rememberKixyuNavigationBackdrop(backgroundColor: Color): KixyuNavigationBackdrop =
    key(backgroundColor) {
        val onDraw: ContentDrawScope.() -> Unit = remember(backgroundColor) {
            {
                drawRect(backgroundColor)
                drawContent()
            }
        }
        val backdrop = rememberLayerBackdrop(onDraw = onDraw)
        remember(backdrop) { KixyuNavigationBackdrop(backdrop) }
    }

fun Modifier.kixyuNavigationBackdrop(backdrop: KixyuNavigationBackdrop): Modifier =
    layerBackdrop(backdrop.value).onGloballyPositioned { backdrop.coordinates = it }

/**
 * Floating navigation geometry shared by MIUIX and Material modes.
 *
 * The bar uses the shared shadow-free bottom island material. It must never paint a second sheet
 * over the system gesture area. Both the lens and the labels share the platter's centre.
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
    val showLabels = kixyuNavigationShowsLabels(LocalDensity.current.fontScale)
    if (items.isEmpty()) return

    val selectedIndex = items.indexOfFirst { it.route == selectedKey }.coerceAtLeast(0)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 1f)
    val unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
    val selectedItemColor = accentColor
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
    val animatedIndicatorPosition = animateFloatAsState(
        targetValue = if (indicatorDragging) dragPosition else restingVisualIndex.toFloat(),
        // No trailing spring while dragging, and no catch-up jump when the finger releases.
        animationSpec = if (indicatorDragging) snap() else spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "navigationIndicatorPosition",
    )
    val indicatorPosition = { animatedIndicatorPosition.value }
    val visuallySelectedIndex = if (indicatorDragging) {
        navigationDragTargetIndex(dragPosition, items.size)
    } else {
        currentIndex
    }
    Box(
        modifier = modifier
            .width(
                KixyuSize.bottomNavigationItemWidth * items.size +
                    KixyuSize.bottomNavigationInnerPadding * 2,
            )
            .height(KixyuSize.bottomNavigationBarHeight),
        contentAlignment = Alignment.TopStart,
    ) {
        KixyuNavigationGlass(
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            pressProgress = { pressProgress },
            selectionModifier = Modifier
                .offset {
                    IntOffset(
                        x = (KixyuSize.bottomNavigationInnerPadding +
                            KixyuSize.bottomNavigationItemWidth * indicatorPosition()).roundToPx(),
                        y = ((KixyuSize.bottomNavigationBarHeight -
                            KixyuSize.bottomNavigationIndicatorHeight) / 2).roundToPx(),
                    )
                }
                .width(KixyuSize.bottomNavigationItemWidth)
                .height(KixyuSize.bottomNavigationIndicatorHeight)
                .graphicsLayer {
                    // Keep the lifted lens within the platter's breathing room.
                    scaleX = 1f + .04f * pressProgress
                    scaleY = 1f + .06f * pressProgress
                }
                .testTag("navigation_selection"),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .testTag("navigation_tabs")
                .selectableGroup()
                .padding(horizontal = KixyuSize.bottomNavigationInnerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = currentIndex == index
                val itemColor by animateColorAsState(
                    targetValue = if (visuallySelectedIndex == index) {
                        selectedItemColor
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
                        contentDescription = item.label.takeUnless { showLabels },
                        modifier = Modifier.size(24.dp),
                        tint = itemColor,
                    )
                    if (showLabels) {
                        Text(
                            text = item.label,
                            color = itemColor,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

internal const val KIXYU_NAVIGATION_LABEL_MAX_FONT_SCALE = 1.3f

internal fun kixyuNavigationShowsLabels(fontScale: Float): Boolean =
    fontScale <= KIXYU_NAVIGATION_LABEL_MAX_FONT_SCALE

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
