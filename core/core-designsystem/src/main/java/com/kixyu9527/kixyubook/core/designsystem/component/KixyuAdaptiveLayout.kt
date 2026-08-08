package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.basic.LocalNavigationRailDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationRailDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem

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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val appUiStyle = LocalAppUiStyle.current
    val showLabels = kixyuWindowSizeClass().showNavigationLabels
    val itemHeight = if (showLabels) {
        KixyuSize.navigationRailLabeledItemHeight
    } else {
        KixyuSize.navigationRailItemHeight
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Start + WindowInsetsSides.Vertical,
                ),
            )
            .padding(start = KixyuSpacing.medium),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.width(KixyuSize.navigationRailWidth),
            shape = RoundedCornerShape(KixyuSize.navigationContainerCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = if (appUiStyle == AppUiStyle.MIUIX) 0.dp else KixyuSpacing.extraSmall,
        ) {
            if (appUiStyle == AppUiStyle.MIUIX) {
                CompositionLocalProvider(
                    LocalNavigationRailDisplayMode provides if (showLabels) {
                        NavigationRailDisplayMode.IconAndText
                    } else {
                        NavigationRailDisplayMode.IconOnly
                    },
                ) {
                    Column {
                        items.forEach { item ->
                            MiuixNavigationRailItem(
                                selected = selectedKey == item.route,
                                onClick = { onSelected(item) },
                                icon = item.icon,
                                label = item.label,
                                enabled = enabled,
                                modifier = Modifier
                                    .width(KixyuSize.navigationRailWidth)
                                    .height(itemHeight),
                            )
                        }
                    }
                }
            } else {
                Column {
                    items.forEach { item ->
                        NavigationRailItem(
                            selected = selectedKey == item.route,
                            onClick = { onSelected(item) },
                            icon = { Icon(item.icon, item.label) },
                            label = if (showLabels) {
                                { Text(item.label, maxLines = 1) }
                            } else {
                                null
                            },
                            enabled = enabled,
                            alwaysShowLabel = showLabels,
                            modifier = Modifier
                                .width(KixyuSize.navigationRailWidth)
                                .height(itemHeight),
                        )
                    }
                }
            }
        }
    }
}
