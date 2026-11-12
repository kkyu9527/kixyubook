package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val accessibleMinSize = maxOf(minSize, 48.dp)
    val interactiveModifier = modifier
        .minimumInteractiveComponentSize()
        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    val materialColors = MaterialTheme.colorScheme
    val useMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val resolvedContainer = if (containerColor == Color.Unspecified) {
        if (useMiuix) materialColors.surfaceContainerHigh else materialColors.secondaryContainer
    } else containerColor
    val resolvedContent = if (contentColor == Color.Unspecified) {
        materialColors.primary
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
            modifier = interactiveModifier,
            enabled = enabled,
            backgroundColor = if (enabled) resolvedContainer else resolvedDisabledContainer,
            minWidth = accessibleMinSize,
            minHeight = accessibleMinSize,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) resolvedContent else resolvedDisabledContent,
                content = content,
            )
        }
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = interactiveModifier,
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
    val interactiveModifier = modifier
        .minimumInteractiveComponentSize()
        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    val contentColor = MaterialTheme.colorScheme.primary
    val disabledContentColor = contentColor.copy(alpha = .38f)
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixIconButton(
            onClick = onClick,
            modifier = interactiveModifier,
            enabled = enabled,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (enabled) contentColor else disabledContentColor,
                content = content,
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = interactiveModifier,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = contentColor,
                disabledContentColor = disabledContentColor,
            ),
            content = content,
        )
    }
}

@Composable
fun KixyuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    horizontalPadding: Dp? = null,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColorsPrimary(),
            insideMargin = horizontalPadding?.let {
                PaddingValues(horizontal = it, vertical = MiuixButtonDefaults.InsideMargin.calculateTopPadding())
            } ?: MiuixButtonDefaults.InsideMargin,
        ) { MiuixText(text, maxLines = 1) }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            contentPadding = horizontalPadding?.let {
                PaddingValues(horizontal = it, vertical = ButtonDefaults.ContentPadding.calculateTopPadding())
            } ?: ButtonDefaults.ContentPadding,
        ) { Text(text, maxLines = 1) }
    }
}

@Composable
fun KixyuSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    horizontalPadding: Dp? = null,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColors(),
            insideMargin = horizontalPadding?.let {
                PaddingValues(horizontal = it, vertical = MiuixButtonDefaults.InsideMargin.calculateTopPadding())
            } ?: MiuixButtonDefaults.InsideMargin,
        ) { MiuixText(text, maxLines = 1) }
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            contentPadding = horizontalPadding?.let {
                PaddingValues(horizontal = it, vertical = ButtonDefaults.ContentPadding.calculateTopPadding())
            } ?: ButtonDefaults.ContentPadding,
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
        val accent = MiuixTheme.colorScheme.primary
        MiuixTextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = MiuixButtonDefaults.textButtonColors(
                color = Color.Transparent,
                disabledColor = Color.Transparent,
                textColor = accent,
                disabledTextColor = accent.copy(alpha = .38f),
            ),
        )
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = .38f),
            ),
        ) { Text(text, maxLines = 1) }
    }
}

@Composable
internal fun KixyuMiuixWideActionButtons(
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

/**
 * Adds the same platform blur used by dialogs and sheets to Compose [androidx.compose.ui.window.Popup]
 * windows. Dropdown menus and custom context menus otherwise have the shared tint and border but
 * no blurred image behind them, which makes large menus such as the library category selector look
 * like a different glass material.
 */
