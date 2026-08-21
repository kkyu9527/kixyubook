package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape

enum class KixyuWindowWidthClass { COMPACT, MEDIUM, EXPANDED }
enum class KixyuWindowHeightClass { COMPACT, MEDIUM, EXPANDED }

data class KixyuWindowSizeClass(
    val widthClass: KixyuWindowWidthClass,
    val heightClass: KixyuWindowHeightClass,
    val width: Dp,
    val height: Dp,
) {
    val isLandscape: Boolean get() = width > height

    /** Medium landscape and expanded windows have enough room for persistent master/detail panes. */
    val supportsTwoPane: Boolean
        get() = widthClass == KixyuWindowWidthClass.EXPANDED ||
            (widthClass == KixyuWindowWidthClass.MEDIUM && isLandscape)

    /** Short landscape windows keep the rail icon-only so labels cannot be clipped. */
    val showNavigationLabels: Boolean
        get() = widthClass != KixyuWindowWidthClass.COMPACT &&
            heightClass != KixyuWindowHeightClass.COMPACT
}

fun classifyKixyuWindowSize(width: Dp, height: Dp): KixyuWindowSizeClass =
    KixyuWindowSizeClass(
        widthClass = when {
            width < 600.dp -> KixyuWindowWidthClass.COMPACT
            width < 840.dp -> KixyuWindowWidthClass.MEDIUM
            else -> KixyuWindowWidthClass.EXPANDED
        },
        heightClass = when {
            height < 480.dp -> KixyuWindowHeightClass.COMPACT
            height < 900.dp -> KixyuWindowHeightClass.MEDIUM
            else -> KixyuWindowHeightClass.EXPANDED
        },
        width = width,
        height = height,
    )

/** Uses the current app window, not the physical display, so split/freeform changes recompose. */
@Composable
fun kixyuWindowSizeClass(): KixyuWindowSizeClass {
    val density = LocalDensity.current
    val container = LocalWindowInfo.current.containerSize
    return classifyKixyuWindowSize(
        width = with(density) { container.width.toDp() },
        height = with(density) { container.height.toDp() },
    )
}

/** Window-relative width class that also updates for split screen and freeform resizing. */
@Composable
fun kixyuWindowWidthClass(): KixyuWindowWidthClass {
    return kixyuWindowSizeClass().widthClass
}

/**
 * Top-level navigation follows the current window orientation instead of the device category.
 * This keeps a resized tablet and a landscape phone on the same compact floating-rail layout.
 */
@Composable
fun kixyuUsesNavigationRail(): Boolean {
    return kixyuWindowSizeClass().isLandscape
}

/**
 * Keeps list-based pages readable on wide windows while preserving a full-size scroll surface.
 * The outer modifier occupies the page; the actual lazy layout is measured at [maxWidth] and
 * centered, so cards and text never stretch from one tablet edge to the other.
 */
fun Modifier.kixyuPageContentWidth(
    maxWidth: Dp = KixyuSize.pageContentMaxWidth,
): Modifier = fillMaxSize()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = maxWidth)

/** Bottom clearance owned by the active top-level navigation presentation. */
val LocalKixyuNavigationContentPadding = staticCompositionLocalOf<Dp> {
    KixyuSize.bottomNavigationContentHeight
}

/** One shared bottom guard for both the app navigation overlay and system gesture/navigation area. */
@Composable
fun KixyuBottomContentSpacer(modifier: Modifier = Modifier) {
    Column(modifier) {
        Spacer(Modifier.height(LocalKixyuNavigationContentPadding.current))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
fun KixyuNavigationRail(
    items: List<KixyuNavigationItem>,
    selectedKey: String?,
    onSelected: (KixyuNavigationItem) -> Unit,
    backdrop: KixyuNavigationBackdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val showLabels = kixyuWindowSizeClass().showNavigationLabels
    val itemHeight = if (showLabels) {
        KixyuSize.navigationRailLabeledItemHeight
    } else {
        KixyuSize.navigationRailItemHeight
    }
    Box(
        modifier = modifier
            // The rail owns only its compact capsule. Keeping the wrapper intrinsic means the
            // transparent area above and below it never becomes a full-height touch blocker while
            // pager content slides underneath.
            .wrapContentWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Start),
            )
            .padding(start = KixyuSpacing.medium),
        contentAlignment = Alignment.CenterStart,
    ) {
        KixyuGlassSurface(
            backdrop = backdrop,
            modifier = Modifier.width(KixyuSize.navigationRailWidth),
            shape = CircleShape,
            fallbackContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(KixyuSize.bottomNavigationInnerPadding)) {
                items.forEach { item ->
                    val selected = selectedKey == item.route
                    val itemColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        label = "navigationRailItemColor",
                    )
                    val indicatorColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = .15f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                        label = "navigationRailIndicatorColor",
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .background(indicatorColor, CircleShape)
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.Tab,
                                onClick = { onSelected(item) },
                            ),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                            1.dp,
                            Alignment.CenterVertically,
                        ),
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
    }
}
