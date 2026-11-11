package com.kixyu9527.kixyubook.core.designsystem.component
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import android.os.Build
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
    dismissLabel: String? = stringResource(R.string.kixyu_cancel),
    dismissRequiresConfirmation: Boolean = false,
    /** Dialogs with dense lists can opt into a brighter surface and full-contrast content. */
    containerColor: Color? = null,
    contentColor: Color? = null,
    backdropEffect: KixyuPopupBackdropEffect = KixyuPopupBackdropEffect.BLUR_BEHIND,
    content: @Composable () -> Unit,
) {
    if (!show) return
    val isMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val isCompact = kixyuWindowWidthClass() == KixyuWindowWidthClass.COMPACT
    val resolvedAlternativeLabel = alternativeLabel.takeIf { onAlternative != null }
    val predictiveBackState = rememberKixyuPredictiveBackState<Unit>()
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = KixyuAdaptiveDialogProperties,
    ) {
        KixyuDialogWindowEffect(predictiveBackState.progress)
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(if (isCompact) KixyuSpacing.large else KixyuSpacing.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            val surfaceWidth = minOf(maxWidth, KixyuSize.actionDialogMaxWidth)
            val surfaceHeight = minOf(maxHeight, KixyuSize.adaptiveDialogMaxHeight)
            KixyuPopupSurface(
                modifier = Modifier.width(surfaceWidth)
                    .heightIn(max = surfaceHeight)
                    .kixyuPredictivePopupTransform { predictiveBackState.progress },
                shadowElevation = KixyuSpacing.small,
                containerColor = containerColor,
                contentColor = contentColor,
                backdropEffect = backdropEffect,
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
                    } else if (isCompact && resolvedAlternativeLabel != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                            ) {
                                KixyuSecondaryButton(
                                    text = resolvedAlternativeLabel,
                                    onClick = onAlternative ?: {},
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                KixyuSpacing.small,
                                Alignment.End,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (resolvedAlternativeLabel != null) {
                                dismissLabel?.let {
                                    KixyuTextButton(text = it, onClick = onDismissRequest)
                                }
                                Spacer(Modifier.weight(1f))
                                KixyuSecondaryButton(
                                    text = resolvedAlternativeLabel,
                                    onClick = onAlternative ?: {},
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
        KixyuPredictiveBackHandler(
            target = Unit,
            state = predictiveBackState,
            onBack = { onDismissRequest() },
            commitAllowed = { !dismissRequiresConfirmation },
        )
    }
}

/**
 * HyperOS-style popup actions: wide, equal buttons inside the popup, with a bounded action group
 * so tablet and unfolded layouts do not stretch controls across the whole window.
 */
