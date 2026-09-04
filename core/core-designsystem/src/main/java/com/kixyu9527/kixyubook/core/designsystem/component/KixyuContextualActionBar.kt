package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class KixyuContextualAction(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

data class KixyuContextualBarState(
    val actions: List<KixyuContextualAction>,
    val backProgress: () -> Float = { 0f },
)

/**
 * Lets a destination replace top-level navigation with actions for its current selection.
 * Ownership prevents a retained or leaving destination from clearing a newer destination's bar.
 */
@Stable
class KixyuContextualBarController {
    private var owner: Any? = null

    var state by mutableStateOf<KixyuContextualBarState?>(null)
        private set

    fun show(owner: Any, state: KixyuContextualBarState) {
        this.owner = owner
        this.state = state
    }

    fun clear(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        state = null
    }
}

val LocalKixyuContextualBarController = staticCompositionLocalOf<KixyuContextualBarController?> {
    null
}

/** Context actions share the exact geometry, backdrop and bottom inset contract of navigation. */
@Composable
fun KixyuContextualActionBar(
    actions: List<KixyuContextualAction>,
    backdrop: KixyuNavigationBackdrop,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .animateContentSize()
                .width(
                    KixyuSize.bottomNavigationItemWidth * actions.size +
                        KixyuSize.bottomNavigationInnerPadding * 2,
                )
                .heightIn(
                    min = KixyuSize.bottomNavigationContentHeight,
                    max = KixyuSize.bottomNavigationContentHeight,
                )
                .padding(bottom = KixyuSize.bottomNavigationBottomGap),
            contentAlignment = Alignment.TopCenter,
        ) {
            KixyuGlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                fallbackContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = KixyuSize.bottomNavigationInnerPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        val contentColor = when {
                            !action.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                            action.destructive -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        ContextualActionItem(action, contentColor)
                    }
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun ContextualActionItem(
    action: KixyuContextualAction,
    contentColor: Color,
) {
    val showLabel = kixyuNavigationShowsLabels(LocalDensity.current.fontScale)
    Column(
        modifier = Modifier
            .width(KixyuSize.bottomNavigationItemWidth)
            .fillMaxHeight()
            .clickable(
                enabled = action.enabled,
                role = Role.Button,
                onClick = action.onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label.takeUnless { showLabel },
            modifier = Modifier.size(24.dp),
            tint = contentColor,
        )
        if (showLabel) {
            Text(
                text = action.label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
