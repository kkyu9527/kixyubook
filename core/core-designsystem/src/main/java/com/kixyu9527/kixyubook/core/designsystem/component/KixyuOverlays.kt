package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

object KixyuMotion {
    const val PageNavigationMillis = 280
    const val ReaderPopupEnterMillis = 200
    const val ReaderPopupExitMillis = 150
}

/** Every platform dialog participates in the app's edge-to-edge contract. */
val KixyuEdgeToEdgeDialogProperties = DialogProperties(
    decorFitsSystemWindows = false,
)

data class KixyuPopupMenuItem(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun KixyuOverlayHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixScaffold(
            modifier = modifier,
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { content() }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.TopStart) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KixyuBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        OverlayBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            renderInRootScaffold = true,
            content = content,
        )
    } else if (show) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = state,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            content()
        }
    }
}

@Composable
fun KixyuPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<KixyuPopupMenuItem>,
    alignEnd: Boolean = false,
    offset: DpOffset = DpOffset.Zero,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        OverlayListPopup(
            show = expanded,
            alignment = if (alignEnd) PopupPositionProvider.Align.End else PopupPositionProvider.Align.Start,
            enableWindowDim = false,
            onDismissRequest = onDismissRequest,
            renderInRootScaffold = true,
        ) {
            ListPopupColumn {
                items.forEach { item ->
                    // MIUIX 0.9.x BasicComponent cannot be measured with the
                    // intrinsic-width constraints used by ListPopupColumn and
                    // crashes on real devices. Keep the popup/list container
                    // native MIUIX and use a compact bounded row for its item.
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(enabled = item.enabled, onClick = item.onClick)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            item.icon,
                            null,
                            Modifier.size(KixyuSize.icon),
                            tint = if (item.enabled) {
                                MiuixTheme.colorScheme.onSurfaceContainer
                            } else {
                                MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            },
                        )
                        Spacer(Modifier.width(KixyuSpacing.medium))
                        Text(
                            item.label,
                            color = if (item.enabled) {
                                MiuixTheme.colorScheme.onSurfaceContainer
                            } else {
                                MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = offset,
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, maxLines = 1) },
                    leadingIcon = { Icon(item.icon, null) },
                    enabled = item.enabled,
                    onClick = item.onClick,
                )
            }
        }
    }
}

@Composable
fun KixyuTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minSize: Dp = KixyuSize.readerControlButton,
    content: @Composable () -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
            minWidth = minSize,
            minHeight = minSize,
            content = content,
        )
    } else {
        FilledTonalIconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
fun KixyuIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    } else {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
fun KixyuPopupSurface(
    modifier: Modifier = Modifier,
    shadowElevation: Dp = KixyuSpacing.extraSmall,
    content: @Composable () -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixSurface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = MiuixTheme.colorScheme.surfaceContainer,
            shadowElevation = shadowElevation,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                content = content,
            )
        }
    } else {
        androidx.compose.material3.Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = KixyuSpacing.extraSmall,
            shadowElevation = shadowElevation,
            content = content,
        )
    }
}

@Composable
fun KixyuActionDialog(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissLabel: String = "取消",
    content: @Composable () -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        OverlayDialog(
            show = show,
            title = title,
            onDismissRequest = onDismissRequest,
            renderInRootScaffold = true,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small, Alignment.End),
                ) {
                    MiuixTextButton(text = dismissLabel, onClick = onDismissRequest)
                    MiuixButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
                }
            }
        }
    } else if (show) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            properties = KixyuEdgeToEdgeDialogProperties,
            title = { Text(title, maxLines = 1) },
            text = content,
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
            },
            dismissButton = { TextButton(onClick = onDismissRequest) { Text(dismissLabel) } },
        )
    }
}
