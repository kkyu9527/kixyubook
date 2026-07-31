package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class KixyuWindowWidthClass { COMPACT, MEDIUM, EXPANDED }

/** Window-relative width class that also updates for split screen and freeform resizing. */
@Composable
fun kixyuWindowWidthClass(): KixyuWindowWidthClass {
    val width = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    return when {
        width < 600.dp -> KixyuWindowWidthClass.COMPACT
        width < 840.dp -> KixyuWindowWidthClass.MEDIUM
        else -> KixyuWindowWidthClass.EXPANDED
    }
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

@Composable
fun KixyuNavigationRail(
    items: List<KixyuNavigationItem>,
    selectedKey: String?,
    onSelected: (KixyuNavigationItem) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.width(KixyuSize.navigationRailWidth).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        NavigationRail(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            windowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Start + WindowInsetsSides.Vertical,
            ),
        ) {
            Spacer(Modifier.weight(1f))
            items.forEach { item ->
                NavigationRailItem(
                    selected = selectedKey == item.route,
                    onClick = { onSelected(item) },
                    icon = { Icon(item.icon, item.label) },
                    label = { Text(item.label, maxLines = 1) },
                    enabled = enabled,
                    alwaysShowLabel = true,
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
