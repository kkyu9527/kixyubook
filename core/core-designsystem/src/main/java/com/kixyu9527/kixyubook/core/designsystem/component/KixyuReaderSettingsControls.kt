package com.kixyu9527.kixyubook.core.designsystem.component

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.kixyu9527.kixyubook.core.common.model.AppColorTheme
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.CustomReaderTheme
import com.kixyu9527.kixyubook.core.common.model.MAX_GLASS_FROST_LEVEL
import com.kixyu9527.kixyubook.core.common.model.MIN_GLASS_FROST_LEVEL
import com.kixyu9527.kixyubook.core.common.model.PageMode
import com.kixyu9527.kixyubook.core.common.model.PageTurnAnimation
import com.kixyu9527.kixyubook.core.common.model.ReaderBrightnessMode
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.designsystem.R

/** Field-level intent: applied to the latest pending settings, never to a stale snapshot. */
typealias ReaderSettingsUpdate = ((ReaderSettings) -> ReaderSettings) -> Unit

/** Global light/dark mode selector shared by app appearance and the reader shortcut. */
@Composable
fun KixyuThemeModeControl(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
    title: String? = null,
) {
    KixyuDropdownRow(
        title = title ?: stringResource(R.string.kixyu_display_mode),
        selected = settings.theme,
        options = listOf(ReaderTheme.SYSTEM, ReaderTheme.DAY, ReaderTheme.NIGHT),
        optionLabel = { it.displayName() },
        onSelected = { onSettingsChange { current -> current.copy(theme = it) } },
    )
}

@Composable
fun KixyuAppColorControl(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    KixyuDropdownRow(
        title = stringResource(R.string.kixyu_theme_color),
        selected = settings.appColorTheme,
        options = AppColorTheme.entries.filter {
            settings.appUiStyle == AppUiStyle.MATERIAL || it != AppColorTheme.WHITE
        },
        optionLabel = { it.displayName() },
        onSelected = { onSettingsChange { current -> current.copy(appColorTheme = it) } },
    )
}

@Composable
fun KixyuAppUiStyleControl(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    KixyuDropdownRow(
        title = stringResource(R.string.kixyu_ui_style),
        selected = settings.appUiStyle,
        options = AppUiStyle.entries,
        optionLabel = { it.displayName() },
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
    )
}

/** Glass controls shared by app appearance, global reading settings and the in-reader sheet. */
@Composable
fun KixyuGlassEffectControls(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    var previewLevel by remember { mutableFloatStateOf(settings.glassFrostLevel) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(settings.glassFrostLevel) {
        if (!dragging) previewLevel = settings.glassFrostLevel
    }
    KixyuSettingsRow(
        title = stringResource(R.string.kixyu_glass_effect),
        supportingText = stringResource(R.string.kixyu_glass_unsupported) + " · " +
            stringResource(R.string.kixyu_glass_app_wide),
        onClick = {
            onSettingsChange { current -> current.copy(glassEffectEnabled = !settings.glassEffectEnabled) }
        },
    ) {
        KixyuSwitch(
            checked = settings.glassEffectEnabled,
            onCheckedChange = { enabled ->
                onSettingsChange { current -> current.copy(glassEffectEnabled = enabled) }
            },
        )
    }
    KixyuDivider()
    KixyuSliderRow(
        title = stringResource(R.string.kixyu_frost_level),
        value = previewLevel,
        valueLabel = "${previewLevel.roundToInt()}%",
        onValueChange = { value ->
            dragging = true
            previewLevel = value.coerceIn(MIN_GLASS_FROST_LEVEL, MAX_GLASS_FROST_LEVEL)
        },
        onValueChangeFinished = {
            dragging = false
            val snappedLevel = (previewLevel / 5f).roundToInt() * 5f
            previewLevel = snappedLevel.coerceIn(MIN_GLASS_FROST_LEVEL, MAX_GLASS_FROST_LEVEL)
            onSettingsChange { current -> current.copy(glassFrostLevel = previewLevel) }
        },
        valueRange = MIN_GLASS_FROST_LEVEL..MAX_GLASS_FROST_LEVEL,
        steps = 19,
        enabled = settings.glassEffectEnabled,
    )
}

/** Both surfaces render one font entry that opens the shared font management page. */
@Composable
fun KixyuFontControls(
    fonts: List<UserFont>,
    selectedFontUuid: String?,
    onManageFonts: () -> Unit,
) {
    val systemDefaultLabel = stringResource(R.string.kixyu_system_default)
    val selectedUserFont = fonts.firstOrNull { it.uuid == selectedFontUuid }
    KixyuSettingsRow(
        title = stringResource(R.string.kixyu_reading_font),
        supportingText = selectedUserFont?.name ?: systemDefaultLabel,
        icon = KixyuSymbols.FontDownload,
        onClick = onManageFonts,
    ) {
        Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon))
    }
}

@Composable
fun KixyuReaderModeControls(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    KixyuDropdownRow(
        title = stringResource(R.string.kixyu_reading_mode),
        selected = settings.pageMode,
        options = PageMode.entries,
        optionLabel = { it.displayName() },
        onSelected = { mode -> onSettingsChange { current -> current.copy(pageMode = mode) } },
    )
    KixyuDivider()
    if (settings.pageMode == PageMode.SCROLL) {
        // The previous effect is kept in the settings; it applies again when paged reading returns.
        KixyuSettingsRow(
            title = stringResource(R.string.kixyu_page_turn_animation),
            supportingText = stringResource(R.string.kixyu_page_turn_paged_only),
        )
    } else {
        KixyuDropdownRow(
            title = stringResource(R.string.kixyu_page_turn_animation),
            selected = settings.pageTurnAnimation,
            options = PageTurnAnimation.entries,
            optionLabel = { it.displayName() },
            onSelected = { animation ->
                onSettingsChange { current -> current.copy(pageTurnAnimation = animation) }
            },
        )
    }
}

@Composable
fun PageTurnAnimation.displayName(): String = stringResource(
    when (this) {
        PageTurnAnimation.HORIZONTAL_SLIDE -> R.string.kixyu_mode_slide
        PageTurnAnimation.COVER -> R.string.kixyu_mode_cover
    },
)

@Composable
fun KixyuReaderTypographyControls(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    ReaderStepper(
        title = stringResource(R.string.kixyu_font_size),
        value = settings.fontSize,
        step = .5f,
        range = 15f..30f,
        suffix = "sp",
    ) { onSettingsChange { current -> current.copy(fontSize = it) } }
    KixyuDivider()
    ReaderStepper(stringResource(R.string.kixyu_line_spacing), settings.lineHeight, .1f, 1.2f..2.2f) {
        onSettingsChange { current -> current.copy(lineHeight = it) }
    }
    KixyuDivider()
    ReaderStepper(stringResource(R.string.kixyu_letter_spacing), settings.letterSpacing, .1f, 0f..0.2f, "em") {
        onSettingsChange { current -> current.copy(letterSpacing = it) }
    }
    KixyuDivider()
    ReaderStepper(stringResource(R.string.kixyu_page_margin), settings.margin, .1f, 12f..52f, "dp") {
        onSettingsChange { current -> current.copy(margin = it) }
    }
}

@Composable
fun KixyuReaderBehaviorControls(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    ReaderSwitch(
        title = stringResource(R.string.kixyu_volume_page_turn),
        supportingText = stringResource(R.string.kixyu_volume_page_turn_hint),
        checked = settings.volumeKeyPageTurn,
    ) { onSettingsChange { current -> current.copy(volumeKeyPageTurn = it) } }
}

/** Screen-on stays in the theme/screen group; brightness itself is level-1 only. */
@Composable
fun KixyuReaderKeepScreenOnControl(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
) {
    ReaderSwitch(
        title = stringResource(R.string.kixyu_keep_screen_on),
        supportingText = stringResource(R.string.kixyu_keep_screen_on_hint),
        checked = settings.keepScreenOn,
    ) { onSettingsChange { current -> current.copy(keepScreenOn = it) } }
}

/** Brightness + follow-system only; the quick access row and level-2 group share this. */
@Composable
fun KixyuReaderBrightnessSlider(
    settings: ReaderSettings,
    onSettingsChange: ReaderSettingsUpdate,
    onBrightnessPreview: (Float?) -> Unit = {},
) {
    var previewBrightness by remember { mutableFloatStateOf(settings.brightness) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(settings.brightness) {
        if (!dragging) previewBrightness = settings.brightness
    }
    val automatic = settings.brightnessMode == ReaderBrightnessMode.SYSTEM
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = KixyuSpacing.rowHorizontal,
            vertical = KixyuSpacing.rowVertical,
        ),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.kixyu_reading_brightness),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (automatic) stringResource(R.string.kixyu_follow_system) else "${(previewBrightness * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = if (automatic) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            KixyuTonalIconButton(
                onClick = {
                    val mode = if (automatic) ReaderBrightnessMode.MANUAL else ReaderBrightnessMode.SYSTEM
                    dragging = false
                    onBrightnessPreview(previewBrightness.takeIf { mode == ReaderBrightnessMode.MANUAL })
                    onSettingsChange { current -> current.copy(brightnessMode = mode) }
                },
                modifier = Modifier.size(KixyuSize.stepperButton).semantics { selected = automatic },
                minSize = KixyuSize.stepperButton,
                containerColor = if (automatic) MaterialTheme.colorScheme.primary else Color.Unspecified,
                contentColor = if (automatic) MaterialTheme.colorScheme.onPrimary else Color.Unspecified,
            ) {
                Icon(
                    KixyuSymbols.Tune,
                    stringResource(
                        if (automatic) R.string.kixyu_disable_auto_brightness
                        else R.string.kixyu_enable_auto_brightness,
                    ),
                    Modifier.size(KixyuSize.iconSmall),
                )
            }
            KixyuSlider(
                value = previewBrightness,
                onValueChange = { value ->
                    dragging = true
                    previewBrightness = value
                    onBrightnessPreview(value)
                },
                onValueChangeFinished = {
                    dragging = false
                    onSettingsChange { current ->
                        current.copy(
                            brightnessMode = ReaderBrightnessMode.MANUAL,
                            brightness = previewBrightness,
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !automatic,
                valueRange = .05f..1f,
                steps = 18,
            )
            Text(
                text = if (automatic) stringResource(R.string.kixyu_automatic) else "${(previewBrightness * 100).roundToInt()}%",
                modifier = Modifier.width(44.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ReaderSwitch(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    KixyuSettingsRow(
        title = title,
        supportingText = supportingText,
        onClick = { onCheckedChange(!checked) },
    ) {
        KixyuSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun ReaderStepper(
    title: String,
    value: Float,
    step: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    onChanged: (Float) -> Unit,
) {
    val valueLabel = String.format(
        LocalLocale.current.platformLocale,
        "%.1f%s",
        value,
        if (suffix.isEmpty()) "" else " $suffix",
    )
    KixyuStepperRow(
        title = title,
        valueLabel = valueLabel,
        onDecrease = { onChanged(value.steppedBy(-1, step, range)) },
        onIncrease = { onChanged(value.steppedBy(1, step, range)) },
        decreaseEnabled = value > range.start,
        increaseEnabled = value < range.endInclusive,
    )
}

private fun Float.steppedBy(
    direction: Int,
    step: Float,
    range: ClosedFloatingPointRange<Float>,
): Float {
    val tick = (this / step).roundToInt() + direction
    return ((tick * step * 1_000f).roundToInt() / 1_000f).coerceIn(range)
}

@Composable
internal fun CustomThemeEditor(
    theme: CustomReaderTheme,
    onChanged: (CustomReaderTheme) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(
            start = KixyuSpacing.rowHorizontal,
            end = KixyuSpacing.rowHorizontal,
            bottom = KixyuSpacing.medium,
        ),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
    ) {
        KixyuColorEditorRow(stringResource(R.string.kixyu_color_background), theme.backgroundHex) { onChanged(theme.copy(backgroundHex = it)) }
        KixyuColorEditorRow(stringResource(R.string.kixyu_color_body), theme.bodyHex) { onChanged(theme.copy(bodyHex = it)) }
        KixyuColorEditorRow(stringResource(R.string.kixyu_color_title), theme.titleHex) { onChanged(theme.copy(titleHex = it)) }
        KixyuColorEditorRow(stringResource(R.string.kixyu_color_accent), theme.accentHex) { onChanged(theme.copy(accentHex = it)) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun KixyuColorEditorRow(label: String, value: String, onValidValue: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    var paletteOpen by remember { mutableStateOf(false) }
    val valid = draft.matches(HEX_COLOR_PATTERN)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
    ) {
        Box {
            // Palette first: tapping the swatch offers ready-made colours; the hex field below is
            // the precise fallback for values outside the palette.
            KixyuColorSwatch(
                if (valid) draft else value,
                modifier = Modifier.clickable { paletteOpen = true },
            )
            DropdownMenu(expanded = paletteOpen, onDismissRequest = { paletteOpen = false }) {
                FlowRow(
                    modifier = Modifier.width(232.dp).padding(KixyuSpacing.small),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                ) {
                    COLOR_PALETTE.forEach { swatch ->
                        KixyuColorSwatch(
                            swatch,
                            modifier = Modifier.clickable {
                                draft = swatch
                                onValidValue(swatch)
                                paletteOpen = false
                            },
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { candidate ->
                if (candidate.length <= MAX_HEX_LENGTH) {
                    draft = candidate
                    if (candidate.matches(HEX_COLOR_PATTERN)) onValidValue(candidate)
                }
            },
            label = { Text(label) },
            singleLine = true,
            isError = !valid,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ReaderTheme.displayName(): String = stringResource(when (this) {
    ReaderTheme.SYSTEM -> R.string.kixyu_follow_system
    ReaderTheme.DAY -> R.string.kixyu_theme_day
    ReaderTheme.NIGHT -> R.string.kixyu_theme_night
})

@Composable
fun PageMode.displayName(): String = stringResource(when (this) {
    PageMode.SCROLL -> R.string.kixyu_mode_scroll
    PageMode.PAGED -> R.string.kixyu_mode_paged
})

@Composable
fun AppColorTheme.displayName(): String = stringResource(when (this) {
    AppColorTheme.DEFAULT -> R.string.kixyu_palette_default
    AppColorTheme.WHITE -> R.string.kixyu_palette_white
    AppColorTheme.DYNAMIC -> R.string.kixyu_palette_dynamic
    AppColorTheme.SAGE -> R.string.kixyu_palette_sage
    AppColorTheme.OCEAN -> R.string.kixyu_palette_ocean
    AppColorTheme.VIOLET -> R.string.kixyu_palette_violet
    AppColorTheme.AMBER -> R.string.kixyu_palette_amber
})

fun AppUiStyle.displayName(): String = when (this) {
    AppUiStyle.MATERIAL -> "Material"
    AppUiStyle.MIUIX -> "MIUIX"
}


/** Curated light/dark reading colours; exact values stay available through the hex field. */
private val COLOR_PALETTE = listOf(
    "#F7F4EC", "#FFFFFF", "#EFE7D8", "#E3DAC9", "#D9D9D0",
    "#B8CCBD", "#A3B8A8", "#8FA98F", "#52655A", "#3E4C43",
    "#292722", "#171713", "#11120F", "#3A3A34", "#4A4A42",
    "#8B5E83", "#4A6B8A", "#1F618D", "#B03A2E", "#7A6A53",
)
