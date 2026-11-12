package com.kixyu9527.kixyubook.core.designsystem.component

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.designsystem.R
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuGlassEffectEnabled
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

/** Matches the spring used by MIUIX 0.9.2 bottom sheets for edge-attached surfaces. */
fun kixyuPopupSpring(): AnimationSpec<Float> =
    folmeSpring(damping = 0.9f, response = 0.38f)

/** Every platform dialog participates in the app's edge-to-edge contract. */
val KixyuEdgeToEdgeDialogProperties = DialogProperties(
    decorFitsSystemWindows = false,
)

internal val KixyuAdaptiveDialogProperties = DialogProperties(
    decorFitsSystemWindows = false,
    usePlatformDefaultWidth = false,
    dismissOnBackPress = false,
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
            // The shared content surface owns the glass tint, outline and shadow.
            backgroundColor = Color.Transparent,
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
            containerColor = Color.Transparent,
            dragHandle = null,
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
    val backdrop = LocalKixyuGlassBackdrop.current
    val predictiveBackState = rememberKixyuPredictiveBackState<Unit>()
    if (backdrop != null) {
        KixyuBackdropAdaptiveModal(
            show = show,
            onDismissRequest = onDismissRequest,
            backdrop = backdrop,
            backProgress = { predictiveBackState.progress },
            content = content,
        )
        // Compose after the visible surface so this callback outranks the underlying NavDisplay.
        KixyuPredictiveBackHandler(
            target = Unit.takeIf { show },
            state = predictiveBackState,
            onBack = { onDismissRequest() },
        )
    } else if (kixyuWindowWidthClass() == KixyuWindowWidthClass.COMPACT) {
        // Material and MIUIX bottom sheets own a native predictive-back implementation.
        KixyuBottomSheet(show = show, onDismissRequest = onDismissRequest, content = content)
    } else if (show) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = KixyuAdaptiveDialogProperties,
        ) {
            KixyuDialogWindowEffect(predictiveBackState.progress)
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(KixyuSpacing.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                val surfaceWidth = minOf(maxWidth, KixyuSize.adaptiveDialogMaxWidth)
                val surfaceHeight = minOf(maxHeight, KixyuSize.adaptiveDialogMaxHeight)
                KixyuPopupSurface(
                    modifier = Modifier.width(surfaceWidth)
                        .heightIn(max = surfaceHeight)
                        .kixyuPredictivePopupTransform { predictiveBackState.progress },
                    shadowElevation = KixyuSpacing.small,
                    backdropEffect = KixyuPopupBackdropEffect.BLUR_BEHIND,
                    content = content,
                )
            }
            KixyuPredictiveBackHandler(
                target = Unit,
                state = predictiveBackState,
                onBack = { onDismissRequest() },
            )
        }
    }
}

/**
 * Keeps a modal in the page render tree when that page exposes a backdrop. This is the same
 * rendering path used by the reader directory: the surface samples and blurs the real page layer
 * instead of relying on best-effort blur behind a separate Android window.
 */
@Composable
private fun KixyuBackdropAdaptiveModal(
    show: Boolean,
    onDismissRequest: () -> Unit,
    backdrop: KixyuNavigationBackdrop,
    backProgress: () -> Float,
    content: @Composable () -> Unit,
) {
    val compact = kixyuWindowWidthClass() == KixyuWindowWidthClass.COMPACT
    AnimatedVisibility(
        visible = show,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)),
        exit = fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = 1f - backProgress() }
                    .background(Color.Black.copy(alpha = .28f))
                    .clickable(onClick = onDismissRequest),
            )
            val maxSurfaceHeight = if (compact) maxHeight * .82f else {
                minOf(maxHeight, KixyuSize.adaptiveDialogMaxHeight)
            }
            val maxSurfaceWidth = minOf(maxWidth, KixyuSize.adaptiveDialogMaxWidth)
            Box(
                modifier = if (compact) {
                    Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(
                            start = KixyuSpacing.medium,
                            end = KixyuSpacing.medium,
                            bottom = KixyuSize.floatingSurfaceBottomGap,
                        )
                } else {
                    Modifier.align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(KixyuSpacing.extraLarge)
                },
            ) {
                KixyuGlassSurface(
                    backdrop = backdrop,
                    role = KixyuGlassRole.CONTENT,
                    modifier = Modifier
                        .then(
                            if (compact) {
                                Modifier.fillMaxWidth().widthIn(max = KixyuSize.sheetContentMaxWidth)
                            } else {
                                Modifier.width(maxSurfaceWidth)
                            },
                        )
                        .heightIn(max = maxSurfaceHeight)
                        .kixyuPredictivePopupTransform(backProgress)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent()
                            }
                        },
                    fallbackContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun KixyuSheetContent(content: @Composable () -> Unit) {
    KixyuDialogWindowEffect()
    Box(
        modifier = Modifier.fillMaxWidth().padding(
            PaddingValues(
                start = KixyuSpacing.medium,
                end = KixyuSpacing.medium,
                bottom = KixyuSize.floatingSurfaceBottomGap,
            ),
        ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        KixyuPopupSurface(
            modifier = Modifier.fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = KixyuSize.sheetContentMaxWidth),
            shadowElevation = KixyuSpacing.small,
            backdropEffect = KixyuPopupBackdropEffect.BLUR_BEHIND,
        ) {
            Box(contentAlignment = Alignment.TopCenter) { content() }
        }
    }
}

@Composable
fun KixyuPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<KixyuPopupMenuItem>,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    alignEnd: Boolean = false,
    offset: DpOffset = DpOffset.Zero,
    shape: Shape = KixyuPopupShape,
) {
    if (LocalKixyuGlassEffectEnabled.current) {
        KixyuGlassDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = offset,
            shape = shape,
            modifier = modifier
                .then(
                    width?.let { Modifier.width(it) }
                        ?: Modifier.widthIn(
                            min = KixyuSize.popupMenuMinWidth,
                            max = KixyuSize.popupMenuMaxWidth,
                        ),
                )
        ) {
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = item.enabled, onClick = item.onClick)
                        .padding(horizontal = KixyuSpacing.large),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        item.icon,
                        null,
                        Modifier.size(KixyuSize.icon),
                        tint = if (item.enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = .38f),
                    )
                    Spacer(Modifier.width(KixyuSpacing.medium))
                    Text(
                        item.label,
                        modifier = Modifier.weight(1f),
                        color = if (item.enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = .38f),
                        maxLines = 1,
                    )
                    if (item.selected) {
                        Icon(
                            KixyuSymbols.Check,
                            null,
                            Modifier.size(KixyuSize.icon),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    } else if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
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
                                KixyuSymbols.Check,
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
            modifier = modifier.then(
                width?.let { Modifier.width(it) } ?: Modifier,
            ),
            offset = offset,
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, maxLines = 1) },
                    leadingIcon = { Icon(item.icon, null) },
                    trailingIcon = if (item.selected) {
                        { Icon(KixyuSymbols.Check, null) }
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

/** Every glass dropdown, including settings preferences, enters through this one container. */
@Composable
internal fun KixyuGlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    shape: Shape = KixyuPopupShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdropEffect = KixyuPopupBackdropEffect.BLUR_BEHIND
    val fallbackContainer = if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val glassEnabled = LocalKixyuGlassEffectEnabled.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        modifier = modifier,
        shape = shape,
        containerColor = if (glassEnabled) Color.Transparent else fallbackContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        if (backdropEffect.blursWindow) KixyuPopupWindowEffect()
        if (glassEnabled) {
            KixyuGlasmioSurface(
                backdrop = LocalKixyuGlassBackdrop.current,
                role = KixyuGlassRole.CONTENT,
                modifier = Modifier,
                shape = shape,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column { content() }
            }
        } else {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            ) {
                content()
            }
        }
    }
}

/** Controls whether a popup frosts only its own surface or also blurs the window behind it. */
enum class KixyuPopupBackdropEffect {
    SURFACE_ONLY,
    BLUR_BEHIND,
}

internal val KixyuPopupBackdropEffect.blursWindow: Boolean
    get() = this == KixyuPopupBackdropEffect.BLUR_BEHIND

@Composable
fun KixyuPopupSurface(
    modifier: Modifier = Modifier,
    shape: Shape = KixyuPopupShape,
    shadowElevation: Dp = KixyuSpacing.extraSmall,
    containerColor: Color? = null,
    contentColor: Color? = null,
    backdropEffect: KixyuPopupBackdropEffect = KixyuPopupBackdropEffect.SURFACE_ONLY,
    role: KixyuGlassRole = KixyuGlassRole.CONTENT,
    content: @Composable () -> Unit,
) {
    val resolvedContainer = containerColor ?: if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val resolvedContent = contentColor ?: MaterialTheme.colorScheme.onSurface
    val windowBlurred = backdropEffect.blursWindow
    if (LocalKixyuGlassEffectEnabled.current) {
        if (windowBlurred) KixyuPopupWindowEffect()
        KixyuGlasmioSurface(
            backdrop = LocalKixyuGlassBackdrop.current,
            modifier = modifier,
            shape = shape,
            contentColor = resolvedContent,
            role = role,
            content = { content() },
        )
    } else if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixSurface(
            modifier = modifier,
            shape = shape,
            color = resolvedContainer,
            shadowElevation = shadowElevation,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalContentColor provides resolvedContent,
                content = content,
            )
        }
    } else {
        androidx.compose.material3.Surface(
            modifier = modifier,
            shape = shape,
            color = resolvedContainer,
            contentColor = resolvedContent,
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
    surfaceModifier: Modifier = Modifier,
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
                modifier = Modifier.widthIn(max = KixyuSize.transientPopupMaxWidth).then(surfaceModifier),
                role = KixyuGlassRole.CONTROL,
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
    // Completion notices reuse the same popup without a spinner so both states look unified.
    progress: Boolean = true,
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
            if (progress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
 * Optional app brand mark shown at the leading edge of every transient bottom snackbar, matching the
 * platform toast that carries the app icon. Provided once at the app root so all [KixyuSnackbarHost]
 * call sites share the same logo treatment.
 */
val LocalKixyuBrandIcon = staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }

@Composable
private fun SnackbarBrandIcon() {
    val brand = LocalKixyuBrandIcon.current ?: return
    brand(Modifier.size(KixyuSize.icon))
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
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(
                bottom = LocalKixyuNavigationContentPadding.current + KixyuSpacing.medium,
            ),
    ) { data ->
        KixyuPopupSurface(
            modifier = Modifier
                .widthIn(max = KixyuSize.transientPopupMaxWidth)
                .heightIn(min = KixyuSize.bottomNavigationBarHeight),
            role = KixyuGlassRole.CONTROL,
            shape = RoundedCornerShape(KixyuSize.navigationContainerCornerRadius),
            shadowElevation = KixyuSpacing.extraSmall,
        ) {
            val hasAction = data.visuals.withDismissAction || data.visuals.actionLabel != null
            if (LocalAppUiStyle.current == AppUiStyle.MIUIX && hasAction) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = KixyuSize.bottomNavigationBarHeight)
                        .padding(
                            horizontal = KixyuSpacing.large,
                            vertical = KixyuSpacing.medium,
                        ),
                    verticalArrangement = Arrangement.spacedBy(
                        KixyuSpacing.medium,
                        Alignment.CenterVertically,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SnackbarBrandIcon()
                    Text(
                        text = data.visuals.message,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    KixyuMiuixWideActionButtons(
                        primaryLabel = data.visuals.actionLabel,
                        onPrimary = data::performAction,
                        secondaryLabel = stringResource(R.string.kixyu_close).takeIf { data.visuals.withDismissAction },
                        onSecondary = data::dismiss,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            } else if (hasAction) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = KixyuSize.bottomNavigationBarHeight)
                        .padding(horizontal = KixyuSpacing.large),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SnackbarBrandIcon()
                    Text(
                        text = data.visuals.message,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (data.visuals.withDismissAction) {
                        KixyuTextButton(text = stringResource(R.string.kixyu_close), onClick = data::dismiss)
                    }
                    data.visuals.actionLabel?.let { actionLabel ->
                        KixyuTextButton(text = actionLabel, onClick = data::performAction)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .heightIn(min = KixyuSize.bottomNavigationBarHeight)
                        .padding(horizontal = KixyuSpacing.large),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SnackbarBrandIcon()
                    Text(
                        text = data.visuals.message,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun KixyuPopupWindowEffect() {
    if (!kixyuCrossWindowBlurAvailable()) return
    val view = LocalView.current
    // Compose Dialog already has a stable Window API and is handled by KixyuDialogWindowEffect.
    if (view.parent is DialogWindowProvider) return
    val glassEnabled = LocalKixyuGlassEffectEnabled.current
    val blurRadius = with(LocalDensity.current) {
        KixyuGlassStyle.windowBlurRadius.roundToPx()
    }
    DisposableEffect(view, glassEnabled, blurRadius) {
        if (!glassEnabled) return@DisposableEffect onDispose {}
        val popupRoot = view.rootView
        val layoutParams = popupRoot.layoutParams as? WindowManager.LayoutParams
            ?: return@DisposableEffect onDispose {}
        val windowManager = popupRoot.context.getSystemService(WindowManager::class.java)
        val originalFlags = layoutParams.flags
        val originalBlurRadius = layoutParams.blurBehindRadius
        layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        layoutParams.blurBehindRadius = blurRadius
        runCatching { windowManager.updateViewLayout(popupRoot, layoutParams) }
        onDispose {
            if (popupRoot.isAttachedToWindow) {
                layoutParams.flags = originalFlags
                layoutParams.blurBehindRadius = originalBlurRadius
                runCatching { windowManager.updateViewLayout(popupRoot, layoutParams) }
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION") // Dialog windows still require the legacy soft-input resize flag.
internal fun KixyuDialogWindowEffect(backProgress: Float = 0f) {
    val view = LocalView.current
    val glassEnabled = LocalKixyuGlassEffectEnabled.current
    val blurRadius = with(LocalDensity.current) {
        (KixyuGlassStyle.windowBlurRadius * (1f - backProgress.coerceIn(0f, 1f))).roundToPx()
    }
    DisposableEffect(view, glassEnabled) {
        val window = (view.parent as? DialogWindowProvider)?.window
            ?: return@DisposableEffect onDispose {}
        val originalMode = window.attributes.softInputMode
        val originallyBlurred = window.attributes.flags
            .and(WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
        val originalBlurRadius = window.attributes.blurBehindRadius
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (glassEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = blurRadius
            }
        }
        onDispose {
            window.setSoftInputMode(originalMode)
            window.attributes = window.attributes.apply {
                blurBehindRadius = originalBlurRadius
            }
            if (!originallyBlurred) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }
    // Progress changes must not tear down/reinstall the window effect (including soft-input
    // flags) every frame. Only send a window update when the rounded pixel radius changes.
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && glassEnabled && window.attributes.blurBehindRadius != blurRadius) {
            window.attributes = window.attributes.apply { blurBehindRadius = blurRadius }
        }
    }
}
