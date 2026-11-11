package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.AppColorTheme
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.R
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.theme.lightColors
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Compact single-choice display-mode row; the current value is visible without opening a menu. */
@Composable
fun KixyuThemeModeControl(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
    title: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KixyuSpacing.rowHorizontal, vertical = KixyuSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
    ) {
        Text(
            text = title ?: stringResource(R.string.kixyu_display_mode),
            style = MaterialTheme.typography.titleMedium,
        )
        KixyuSegmentedControl(
            options = listOf(ReaderTheme.SYSTEM, ReaderTheme.DAY, ReaderTheme.NIGHT),
            selected = settings.theme,
            onSelected = { theme -> onSettingsChange { it.copy(theme = theme) } },
            label = { it.displayName() },
        )
    }
}

/** Material/MIUIX switch with explanatory copy instead of a bare dropdown label. */
@Composable
fun KixyuAppUiStyleControl(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KixyuSpacing.rowHorizontal, vertical = KixyuSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
    ) {
        Text(
            text = stringResource(R.string.kixyu_ui_style),
            style = MaterialTheme.typography.titleMedium,
        )
        KixyuSegmentedControl(
            options = AppUiStyle.entries,
            selected = settings.appUiStyle,
            onSelected = { style ->
                onSettingsChange { current ->
                    current.copy(
                        appUiStyle = style,
                        appColorTheme = settings.appColorTheme.takeUnless {
                            style == AppUiStyle.MIUIX && it == AppColorTheme.WHITE
                        } ?: AppColorTheme.DEFAULT,
                    )
                }
            },
            label = { it.displayName() },
        )
    }
}

/** Visual palette picker: seeing the actual surface + accent is faster than reading 7 labels. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KixyuAppColorControl(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    val options = remember(settings.appUiStyle) {
        AppColorTheme.entries.filter {
            settings.appUiStyle == AppUiStyle.MATERIAL || it != AppColorTheme.WHITE
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KixyuSpacing.rowHorizontal, vertical = KixyuSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
    ) {
        Text(
            text = stringResource(R.string.kixyu_theme_color),
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = KixyuSpacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            options.forEach { theme ->
                PaletteOption(
                    theme = theme,
                    selected = settings.appColorTheme == theme,
                    onSelected = { onSettingsChange { it.copy(appColorTheme = theme) } },
                )
            }
        }
    }
}

private data class PalettePreview(
    val background: Color,
    val primary: Color,
    val onPrimary: Color,
)

@Composable
private fun palettePreview(theme: AppColorTheme): PalettePreview {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        if (theme == AppColorTheme.DEFAULT || theme == AppColorTheme.DYNAMIC) {
            val miuix = MiuixTheme.colorScheme
            return PalettePreview(miuix.background, miuix.primary, miuix.onPrimary)
        }
        val dark = MaterialTheme.colorScheme.background.luminance() < .5f
        val controller = remember(theme, dark) {
            ThemeController(
                colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                keyColor = lightColors(theme).primary,
            )
        }
        val miuix = controller.currentColors()
        return PalettePreview(miuix.background, miuix.primary, miuix.onPrimary)
    }
    val scheme = when (theme) {
        AppColorTheme.DEFAULT -> lightColorScheme()
        AppColorTheme.WHITE -> lightColorScheme(
            primary = Color(0xFF3367D6),
            onPrimary = Color.White,
            background = Color.White,
            surface = Color.White,
        )
        AppColorTheme.DYNAMIC -> MaterialTheme.colorScheme
        else -> lightColors(theme)
    }
    return PalettePreview(scheme.background, scheme.primary, scheme.onPrimary)
}

@Composable
private fun PaletteOption(
    theme: AppColorTheme,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val preview = palettePreview(theme)
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelected,
            )
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(preview.background, preview.primary))),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = KixyuSymbols.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = preview.onPrimary,
                )
            }
        }
        Text(
            text = theme.displayName(),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
