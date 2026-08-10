package com.kixyu9527.kixyubook.core.designsystem.component

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

object KixyuMotion {
    const val PageNavigationMillis = 320
    const val ReaderPopupEnterMillis = 200
    const val ReaderPopupExitMillis = 150
    const val ReaderSearchEnterMillis = 280
    const val ReaderSearchExitMillis = 220
}

/** Matches the spring used by MIUIX 0.9.2 bottom sheets for edge-attached surfaces. */
fun kixyuPopupSpring(): AnimationSpec<Float> =
    folmeSpring(damping = 0.9f, response = 0.38f)

/** Every platform dialog participates in the app's edge-to-edge contract. */
val KixyuEdgeToEdgeDialogProperties = DialogProperties(
    decorFitsSystemWindows = false,
)

private val KixyuAdaptiveDialogProperties = DialogProperties(
    decorFitsSystemWindows = false,
    usePlatformDefaultWidth = false,
)

data class KixyuPopupMenuItem(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val selected: Boolean = false,
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
            // MIUIX's light default sheet background and Card container are
            // both white. Its official surface token provides the intended
            // gray page / bright card hierarchy (and the matching dark pair).
            backgroundColor = MiuixTheme.colorScheme.surface,
            // DialogLayout is already resized by the IME on Android. MIUIX's
            // default adds imePadding after that resize, leaving a transparent
            // keyboard-height gap below the visible sheet on real devices.
            defaultWindowInsetsPadding = false,
            renderInRootScaffold = true,
            content = { KixyuSheetContent(content) },
        )
    } else if (show) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = state,
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            // Keep IME ownership at the sheet boundary, matching MIUIX's
            // BottomSheetContentLayout and avoiding per-sheet double insets.
            CompositionLocalProvider(LocalKixyuSheetSection provides true) {
                Box(Modifier.fillMaxWidth().imePadding()) { KixyuSheetContent(content) }
            }
        }
    }
}

/**
 * Uses a bottom sheet only on phones. Medium and expanded windows receive a centered, bounded
 * modal surface so long content never becomes a phone sheet stretched across a tablet.
 */
@Composable
fun KixyuAdaptiveModal(
    show: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (kixyuWindowWidthClass() == KixyuWindowWidthClass.COMPACT) {
        KixyuBottomSheet(show = show, onDismissRequest = onDismissRequest, content = content)
    } else if (show) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = KixyuAdaptiveDialogProperties,
        ) {
            KixyuDialogImeResizeEffect()
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(KixyuSpacing.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                val surfaceWidth = minOf(maxWidth, KixyuSize.adaptiveDialogMaxWidth)
                val surfaceHeight = minOf(maxHeight, KixyuSize.adaptiveDialogMaxHeight)
                KixyuPopupSurface(
                    modifier = Modifier.width(surfaceWidth).heightIn(max = surfaceHeight),
                    shadowElevation = KixyuSpacing.small,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun KixyuSheetContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = KixyuSize.sheetContentMaxWidth),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
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
                        if (item.selected) {
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Outlined.Check,
                                null,
                                Modifier.size(KixyuSize.icon),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
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
                    trailingIcon = if (item.selected) {
                        { Icon(Icons.Outlined.Check, null) }
                    } else {
                        null
                    },
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
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    disabledContainerColor: Color = Color.Unspecified,
    disabledContentColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val materialColors = MaterialTheme.colorScheme
    val useMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val resolvedContainer = if (containerColor == Color.Unspecified) {
        if (useMiuix) materialColors.surfaceContainerHigh else materialColors.secondaryContainer
    } else containerColor
    val resolvedContent = if (contentColor == Color.Unspecified) {
        if (useMiuix) LocalContentColor.current else materialColors.onSecondaryContainer
    } else contentColor
    val resolvedDisabledContainer = if (disabledContainerColor == Color.Unspecified) {
        if (useMiuix && containerColor == Color.Unspecified) resolvedContainer else resolvedContainer.copy(alpha = .38f)
    } else disabledContainerColor
    val resolvedDisabledContent = if (disabledContentColor == Color.Unspecified) {
        if (useMiuix && contentColor == Color.Unspecified) resolvedContent else resolvedContent.copy(alpha = .38f)
    } else disabledContentColor
    if (useMiuix) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            backgroundColor = if (enabled) resolvedContainer else resolvedDisabledContainer,
            minWidth = minSize,
            minHeight = minSize,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) resolvedContent else resolvedDisabledContent,
                content = content,
            )
        }
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = resolvedContainer,
                contentColor = resolvedContent,
                disabledContainerColor = resolvedDisabledContainer,
                disabledContentColor = resolvedDisabledContent,
            ),
            content = content,
        )
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
fun KixyuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColorsPrimary(),
        ) { MiuixText(text, maxLines = 1) }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) { Text(text, maxLines = 1) }
    }
}

@Composable
fun KixyuSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColors(),
        ) { MiuixText(text, maxLines = 1) }
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) { Text(text, maxLines = 1) }
    }
}

@Composable
fun KixyuTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixTextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) { Text(text, maxLines = 1) }
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

/**
 * Interactive popup surface that owns its complete pointer hit area. This prevents gestures from
 * falling through to content behind the popup while keeping nested buttons and fields interactive.
 */
@Composable
fun KixyuInteractivePopupSurface(
    modifier: Modifier = Modifier,
    shadowElevation: Dp = KixyuSpacing.extraSmall,
    content: @Composable () -> Unit,
) {
    KixyuPopupSurface(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent()
            }
        },
        shadowElevation = shadowElevation,
        content = content,
    )
}

@Composable
fun KixyuSafeTopPopup(
    visible: Boolean,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(
                horizontal = KixyuSpacing.medium,
                vertical = KixyuSpacing.small,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)) +
                scaleIn(tween(KixyuMotion.ReaderPopupEnterMillis), initialScale = .96f),
            exit = fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)) +
                scaleOut(tween(KixyuMotion.ReaderPopupExitMillis), targetScale = .98f),
        ) {
            KixyuPopupSurface(
                modifier = Modifier.widthIn(max = 560.dp),
                shadowElevation = shadowElevation,
                content = content,
            )
        }
    }
}

/**
 * App-level non-blocking status popup. Its placement owns safe-drawing Insets, including status
 * bars, display cutouts and landscape camera holes, so callers must not add system padding.
 */
@Composable
fun KixyuTransientStatusPopup(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier,
) {
    KixyuSafeTopPopup(visible = visible, modifier = modifier) {
        Row(
            modifier = Modifier.padding(
                horizontal = KixyuSpacing.medium,
                vertical = KixyuSpacing.small,
            ),
            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Shared transient message surface. SnackbarHost retains Material's queue and motion behavior,
 * while the visible container follows the selected Material/MIUIX component system.
 */
@Composable
fun KixyuSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
        ),
    ) { data ->
        KixyuPopupSurface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            shadowElevation = KixyuSpacing.extraSmall,
        ) {
            val hasAction = data.visuals.withDismissAction || data.visuals.actionLabel != null
            if (LocalAppUiStyle.current == AppUiStyle.MIUIX && hasAction) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = KixyuSpacing.large,
                        vertical = KixyuSpacing.medium,
                    ),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                ) {
                    Text(
                        text = data.visuals.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    KixyuMiuixWideActionButtons(
                        primaryLabel = data.visuals.actionLabel,
                        onPrimary = data::performAction,
                        secondaryLabel = "关闭".takeIf { data.visuals.withDismissAction },
                        onSecondary = data::dismiss,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = KixyuSpacing.large,
                        vertical = KixyuSpacing.medium,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = data.visuals.message,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (data.visuals.withDismissAction) {
                        TextButton(onClick = data::dismiss) { Text("关闭") }
                    }
                    data.visuals.actionLabel?.let { actionLabel ->
                        TextButton(onClick = data::performAction) { Text(actionLabel) }
                    }
                }
            }
        }
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
    alternativeLabel: String? = null,
    onAlternative: (() -> Unit)? = null,
    alternativeEnabled: Boolean = true,
    dismissLabel: String? = "取消",
    content: @Composable () -> Unit,
) {
    val isMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val resolvedAlternativeLabel = alternativeLabel.takeIf { onAlternative != null }
    if (kixyuWindowWidthClass() != KixyuWindowWidthClass.COMPACT) {
        if (!show) return
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = KixyuAdaptiveDialogProperties,
        ) {
            KixyuDialogImeResizeEffect()
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(KixyuSpacing.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                val surfaceWidth = minOf(maxWidth, 600.dp)
                val surfaceHeight = minOf(maxHeight, KixyuSize.adaptiveDialogMaxHeight)
                KixyuPopupSurface(
                    modifier = Modifier.width(surfaceWidth).heightIn(max = surfaceHeight),
                    shadowElevation = KixyuSpacing.small,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.large),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                        )
                        Box(
                            Modifier.weight(1f, fill = false)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                                content = content,
                            )
                        }
                        if (isMiuix) {
                            KixyuMiuixWideActionButtons(
                                primaryLabel = confirmLabel,
                                onPrimary = onConfirm,
                                primaryEnabled = confirmEnabled,
                                secondaryLabel = resolvedAlternativeLabel ?: dismissLabel,
                                onSecondary = onAlternative ?: onDismissRequest,
                                secondaryEnabled = if (resolvedAlternativeLabel != null) {
                                    alternativeEnabled
                                } else {
                                    true
                                },
                                tertiaryLabel = dismissLabel.takeIf { resolvedAlternativeLabel != null },
                                onTertiary = onDismissRequest,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    KixyuSpacing.small,
                                    Alignment.End,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (alternativeLabel != null && onAlternative != null) {
                                    dismissLabel?.let {
                                        KixyuTextButton(text = it, onClick = onDismissRequest)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    KixyuSecondaryButton(
                                        text = alternativeLabel,
                                        onClick = onAlternative,
                                        enabled = alternativeEnabled,
                                        modifier = Modifier.widthIn(min = 140.dp),
                                    )
                                } else {
                                    dismissLabel?.let {
                                        KixyuSecondaryButton(
                                            text = it,
                                            onClick = onDismissRequest,
                                            modifier = Modifier.widthIn(min = 112.dp),
                                        )
                                    }
                                }
                                KixyuButton(
                                    text = confirmLabel,
                                    onClick = onConfirm,
                                    enabled = confirmEnabled,
                                    modifier = Modifier.widthIn(min = 112.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }
    if (isMiuix) {
        // Form fields need a real dialog window so Android can resize that window for the IME.
        // Rendering in the root Scaffold makes the system pan the whole activity to the focused
        // field, producing a large empty band between the dialog and keyboard.
        WindowDialog(
            show = show,
            title = title,
            onDismissRequest = onDismissRequest,
        ) {
            KixyuDialogImeResizeEffect()
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = KixyuSize.adaptiveDialogMaxHeight),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                Box(
                    Modifier.weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) { content() }
                KixyuMiuixWideActionButtons(
                    primaryLabel = confirmLabel,
                    onPrimary = onConfirm,
                    primaryEnabled = confirmEnabled,
                    secondaryLabel = resolvedAlternativeLabel ?: dismissLabel,
                    onSecondary = onAlternative ?: onDismissRequest,
                    secondaryEnabled = if (resolvedAlternativeLabel != null) alternativeEnabled else true,
                    tertiaryLabel = dismissLabel.takeIf { resolvedAlternativeLabel != null },
                    onTertiary = onDismissRequest,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    } else if (show) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            properties = KixyuEdgeToEdgeDialogProperties,
            title = { Text(title, maxLines = 1) },
            text = {
                KixyuDialogImeResizeEffect()
                Box(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) { content() }
            },
            confirmButton = {
                if (alternativeLabel != null && onAlternative != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                        ) {
                            KixyuSecondaryButton(
                                text = alternativeLabel,
                                onClick = onAlternative,
                                enabled = alternativeEnabled,
                                modifier = Modifier.weight(1f),
                            )
                            KixyuButton(
                                text = confirmLabel,
                                onClick = onConfirm,
                                enabled = confirmEnabled,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        dismissLabel?.let {
                            KixyuTextButton(
                                text = it,
                                onClick = onDismissRequest,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    KixyuButton(text = confirmLabel, onClick = onConfirm, enabled = confirmEnabled)
                }
            },
            dismissButton = {
                if (alternativeLabel == null || onAlternative == null) {
                    dismissLabel?.let { TextButton(onClick = onDismissRequest) { Text(it) } }
                }
            },
        )
    }
}

/**
 * HyperOS-style popup actions: wide, equal buttons inside the popup, with a bounded action group
 * so tablet and unfolded layouts do not stretch controls across the whole window.
 */
@Composable
private fun KixyuMiuixWideActionButtons(
    primaryLabel: String?,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    secondaryEnabled: Boolean = true,
    tertiaryLabel: String? = null,
    onTertiary: () -> Unit = {},
) {
    Column(
        modifier = modifier.widthIn(max = 520.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
    ) {
        if (primaryLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            ) {
                secondaryLabel?.let {
                    KixyuSecondaryButton(
                        text = it,
                        onClick = onSecondary,
                        enabled = secondaryEnabled,
                        modifier = Modifier.weight(1f),
                    )
                }
                KixyuButton(
                    text = primaryLabel,
                    onClick = onPrimary,
                    enabled = primaryEnabled,
                    modifier = if (secondaryLabel != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                )
            }
        } else if (secondaryLabel != null) {
            KixyuSecondaryButton(
                text = secondaryLabel,
                onClick = onSecondary,
                enabled = secondaryEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        tertiaryLabel?.let {
            KixyuSecondaryButton(
                text = it,
                onClick = onTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
@Suppress("DEPRECATION") // Dialog windows still require the legacy soft-input resize flag.
private fun KixyuDialogImeResizeEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val originalMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {
            if (originalMode != null) window.setSoftInputMode(originalMode)
        }
    }
}
