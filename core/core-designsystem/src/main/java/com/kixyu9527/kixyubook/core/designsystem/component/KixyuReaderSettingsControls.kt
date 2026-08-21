package com.kixyu9527.kixyubook.core.designsystem.component

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

@Composable
fun KixyuReaderThemeControls(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    modeTitle: String = "显示模式",
) {
    var editingTheme by remember { mutableStateOf<ReaderTheme?>(null) }
    LaunchedEffect(settings.customThemeEnabled) {
        if (!settings.customThemeEnabled) editingTheme = null
    }
    KixyuThemeModeControl(settings, onSettingsChange, modeTitle)
    KixyuDivider()
    KixyuSettingsRow(
        title = "自定义配色",
        onClick = {
            val enabled = !settings.customThemeEnabled
            if (!enabled) editingTheme = null
            onSettingsChange(settings.copy(customThemeEnabled = enabled))
        },
        trailing = {
            KixyuSwitch(
                checked = settings.customThemeEnabled,
                onCheckedChange = { enabled ->
                    if (!enabled) editingTheme = null
                    onSettingsChange(settings.copy(customThemeEnabled = enabled))
                },
            )
        },
    )
    if (settings.customThemeEnabled) {
        listOf(ReaderTheme.DAY, ReaderTheme.NIGHT).forEach { theme ->
            KixyuDivider()
            val expanded = editingTheme == theme
            KixyuSettingsRow(
                title = "${theme.displayName()}配色",
                onClick = { editingTheme = theme.takeUnless { expanded } },
                trailing = {
                    Text(
                        text = if (expanded) "收起" else "修改",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(KixyuMotion.ReaderPopupEnterMillis)) +
                    fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)),
                exit = shrinkVertically(tween(KixyuMotion.ReaderPopupExitMillis)) +
                    fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)),
            ) {
                val editingColors = if (theme == ReaderTheme.NIGHT) settings.customNightTheme else settings.customDayTheme
                CustomThemeEditor(editingColors) { custom ->
                    onSettingsChange(
                        if (theme == ReaderTheme.NIGHT) {
                            settings.copy(customNightTheme = custom)
                        } else {
                            settings.copy(customDayTheme = custom)
                        },
                    )
                }
            }
        }
    }
    KixyuDivider()
    KixyuGlassEffectControls(settings, onSettingsChange)
}

/** Global light/dark mode selector shared by app appearance and the reader shortcut. */
@Composable
fun KixyuThemeModeControl(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    title: String = "显示模式",
) {
    KixyuDropdownRow(
        title = title,
        selected = settings.theme,
        options = listOf(ReaderTheme.SYSTEM, ReaderTheme.DAY, ReaderTheme.NIGHT),
        optionLabel = ReaderTheme::displayName,
        onSelected = { onSettingsChange(settings.copy(theme = it)) },
    )
}

@Composable
fun KixyuAppColorControl(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    KixyuDropdownRow(
        title = "主题色",
        selected = settings.appColorTheme,
        options = AppColorTheme.entries.filter {
            settings.appUiStyle == AppUiStyle.MATERIAL || it != AppColorTheme.WHITE
        },
        optionLabel = AppColorTheme::displayName,
        onSelected = { onSettingsChange(settings.copy(appColorTheme = it)) },
    )
}

@Composable
fun KixyuAppUiStyleControl(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    KixyuDropdownRow(
        title = "界面风格",
        selected = settings.appUiStyle,
        options = AppUiStyle.entries,
        optionLabel = AppUiStyle::displayName,
        onSelected = { style ->
            onSettingsChange(
                settings.copy(
                    appUiStyle = style,
                    appColorTheme = settings.appColorTheme.takeUnless {
                        style == AppUiStyle.MIUIX && it == AppColorTheme.WHITE
                    } ?: AppColorTheme.DEFAULT,
                ),
            )
        },
    )
}

/** Glass controls shared by app appearance, global reading settings and the in-reader sheet. */
@Composable
fun KixyuGlassEffectControls(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    var previewLevel by remember { mutableFloatStateOf(settings.glassFrostLevel) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(settings.glassFrostLevel) {
        if (!dragging) previewLevel = settings.glassFrostLevel
    }
    KixyuSettingsRow(
        title = "玻璃效果",
        supportingText = "低版本 Android 不支持",
        onClick = {
            onSettingsChange(settings.copy(glassEffectEnabled = !settings.glassEffectEnabled))
        },
    ) {
        KixyuSwitch(
            checked = settings.glassEffectEnabled,
            onCheckedChange = { enabled ->
                onSettingsChange(settings.copy(glassEffectEnabled = enabled))
            },
        )
    }
    KixyuDivider()
    KixyuSliderRow(
        title = "磨砂程度",
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
            onSettingsChange(settings.copy(glassFrostLevel = previewLevel))
        },
        valueRange = MIN_GLASS_FROST_LEVEL..MAX_GLASS_FROST_LEVEL,
        steps = 19,
        enabled = settings.glassEffectEnabled,
    )
}

private data class KixyuFontOption(
    val uuid: String?,
    val label: String,
    val addFont: Boolean = false,
)

/** Shared font picker used by both global settings and the in-reader sheet. */
@Composable
fun KixyuFontControls(
    fonts: List<UserFont>,
    selectedFontUuid: String?,
    onSelectFont: (String?) -> Unit,
    onAddFont: () -> Unit,
    onDeleteFont: (UserFont) -> Unit,
    onManageFonts: (() -> Unit)? = null,
) {
    val options = remember(fonts) {
        buildList {
            add(KixyuFontOption(uuid = null, label = "新增字体", addFont = true))
            add(KixyuFontOption(uuid = null, label = "系统默认"))
            fonts.forEach { add(KixyuFontOption(uuid = it.uuid, label = it.name)) }
        }
    }
    val selected = options.firstOrNull { !it.addFont && it.uuid == selectedFontUuid } ?: options[1]
    val selectedUserFont = fonts.firstOrNull { it.uuid == selectedFontUuid }
    if (onManageFonts != null) {
        KixyuSettingsRow(
            title = "阅读字体",
            supportingText = selectedUserFont?.name ?: "系统默认",
            icon = KixyuSymbols.FontDownload,
            onClick = onManageFonts,
        ) {
            Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon))
        }
        return
    }
    KixyuDropdownRow(
        title = "阅读字体",
        selected = selected,
        options = options,
        optionLabel = KixyuFontOption::label,
        icon = KixyuSymbols.FontDownload,
        onSelected = { option ->
            if (option.addFont) onAddFont() else onSelectFont(option.uuid)
        },
    )
    selectedUserFont?.let { font ->
        KixyuDivider()
        KixyuSettingsRow(
            title = "删除当前字体",
            supportingText = font.name,
            icon = KixyuSymbols.DeleteOutline,
            onClick = { onDeleteFont(font) },
        ) {
            Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon))
        }
    }
}

@Composable
fun KixyuPageModeControl(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    val selected = when {
        settings.pageMode == PageMode.SCROLL -> ReaderReadingMode.VERTICAL_SCROLL
        settings.pageTurnAnimation == PageTurnAnimation.COVER -> ReaderReadingMode.COVER
        else -> ReaderReadingMode.HORIZONTAL_SLIDE
    }
    KixyuDropdownRow(
        title = "阅读方式",
        selected = selected,
        options = ReaderReadingMode.entries,
        optionLabel = ReaderReadingMode::displayName,
        onSelected = { mode ->
            onSettingsChange(
                when (mode) {
                    ReaderReadingMode.VERTICAL_SCROLL -> settings.copy(pageMode = PageMode.SCROLL)
                    ReaderReadingMode.HORIZONTAL_SLIDE -> settings.copy(
                        pageMode = PageMode.PAGED,
                        pageTurnAnimation = PageTurnAnimation.HORIZONTAL_SLIDE,
                    )
                    ReaderReadingMode.COVER -> settings.copy(
                        pageMode = PageMode.PAGED,
                        pageTurnAnimation = PageTurnAnimation.COVER,
                    )
                },
            )
        },
    )
}

@Composable
fun KixyuReaderLayoutControls(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    KixyuPageModeControl(settings, onSettingsChange)
    KixyuDivider()
    ReaderStepper(
        title = "字号",
        value = settings.fontSize,
        step = .5f,
        range = 15f..30f,
        suffix = "sp",
    ) { onSettingsChange(settings.copy(fontSize = it)) }
    KixyuDivider()
    ReaderStepper("行间距", settings.lineHeight, .1f, 1.2f..2.2f) {
        onSettingsChange(settings.copy(lineHeight = it))
    }
    KixyuDivider()
    ReaderStepper("字间距", settings.letterSpacing, .1f, 0f..0.2f, "em") {
        onSettingsChange(settings.copy(letterSpacing = it))
    }
    KixyuDivider()
    ReaderStepper("页边距", settings.margin, .1f, 12f..52f, "dp") {
        onSettingsChange(settings.copy(margin = it))
    }
}

@Composable
fun KixyuReaderBehaviorControls(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    ReaderSwitch(
        title = "音量键翻页",
        supportingText = "音量加键上一页，音量减键下一页",
        checked = settings.volumeKeyPageTurn,
    ) { onSettingsChange(settings.copy(volumeKeyPageTurn = it)) }
}

@Composable
fun KixyuReaderInformationControls(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    ReaderSwitch(
        title = "显示状态栏",
        supportingText = "关闭后仅随阅读控制层临时显示",
        checked = settings.showStatusBar,
    ) { onSettingsChange(settings.copy(showStatusBar = it)) }
    KixyuDivider()
    ReaderSwitch(
        title = "隐藏小白条",
        supportingText = "开启后仅随阅读控制层临时显示",
        checked = settings.hideNavigationBar,
    ) { onSettingsChange(settings.copy(hideNavigationBar = it)) }
    KixyuDivider()
    ReaderSwitch(
        title = "显示章节名",
        supportingText = "非章节首页顶部显示当前章节名",
        checked = settings.showChapterTitle,
    ) { onSettingsChange(settings.copy(showChapterTitle = it)) }
    KixyuDivider()
    ReaderSwitch(
        title = "显示页码",
        supportingText = "翻页模式底部显示当前页/总页数",
        checked = settings.showPageNumber,
    ) { onSettingsChange(settings.copy(showPageNumber = it)) }
    KixyuDivider()
    ReaderSwitch(
        title = "显示时间",
        supportingText = "翻页模式底部显示当前时间",
        checked = settings.showReadingTime,
    ) { onSettingsChange(settings.copy(showReadingTime = it)) }
    KixyuDivider()
    ReaderSwitch(
        title = "显示电量",
        supportingText = "翻页模式底部显示设备剩余电量",
        checked = settings.showBatteryLevel,
    ) { onSettingsChange(settings.copy(showBatteryLevel = it)) }
}

@Composable
fun KixyuReaderBrightnessControls(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
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
                text = "阅读亮度",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (automatic) "跟随系统" else "${(previewBrightness * 100).roundToInt()}%",
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
                    onSettingsChange(settings.copy(brightnessMode = mode))
                },
                modifier = Modifier.size(KixyuSize.stepperButton).semantics { selected = automatic },
                minSize = KixyuSize.stepperButton,
                containerColor = if (automatic) MaterialTheme.colorScheme.primary else Color.Unspecified,
                contentColor = if (automatic) MaterialTheme.colorScheme.onPrimary else Color.Unspecified,
            ) {
                Icon(KixyuSymbols.Tune, "${if (automatic) "关闭" else "开启"}跟随系统亮度", Modifier.size(KixyuSize.iconSmall))
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
                    onSettingsChange(
                        settings.copy(
                            brightnessMode = ReaderBrightnessMode.MANUAL,
                            brightness = previewBrightness,
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = !automatic,
                valueRange = .05f..1f,
                steps = 18,
            )
            Text(
                text = if (automatic) "自动" else "${(previewBrightness * 100).roundToInt()}%",
                modifier = Modifier.width(44.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
    KixyuDivider()
    ReaderSwitch(
        title = "保持屏幕常亮",
        supportingText = "阅读期间不自动熄屏",
        checked = settings.keepScreenOn,
    ) { onSettingsChange(settings.copy(keepScreenOn = it)) }
}

@Composable
private fun ReaderSwitch(
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
private fun ReaderStepper(
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
private fun CustomThemeEditor(
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
        KixyuColorEditorRow("背景", theme.backgroundHex) { onChanged(theme.copy(backgroundHex = it)) }
        KixyuColorEditorRow("正文", theme.bodyHex) { onChanged(theme.copy(bodyHex = it)) }
        KixyuColorEditorRow("标题", theme.titleHex) { onChanged(theme.copy(titleHex = it)) }
        KixyuColorEditorRow("强调色", theme.accentHex) { onChanged(theme.copy(accentHex = it)) }
    }
}

@Composable
private fun KixyuColorEditorRow(label: String, value: String, onValidValue: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    val valid = draft.matches(HEX_COLOR_PATTERN)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
    ) {
        KixyuColorSwatch(if (valid) draft else value)
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

fun ReaderTheme.displayName(): String = when (this) {
    ReaderTheme.SYSTEM -> "跟随系统"
    ReaderTheme.DAY -> "日间"
    ReaderTheme.NIGHT -> "夜间"
}

fun PageMode.displayName(): String = when (this) {
    PageMode.SCROLL -> "上下滑动"
    PageMode.PAGED -> "左右翻页"
}

private enum class ReaderReadingMode { VERTICAL_SCROLL, HORIZONTAL_SLIDE, COVER }

private fun ReaderReadingMode.displayName(): String = when (this) {
    ReaderReadingMode.VERTICAL_SCROLL -> "上下滑动"
    ReaderReadingMode.HORIZONTAL_SLIDE -> "左右滑动"
    ReaderReadingMode.COVER -> "覆盖翻页"
}

fun ReaderBrightnessMode.displayName(): String = when (this) {
    ReaderBrightnessMode.SYSTEM -> "跟随系统"
    ReaderBrightnessMode.MANUAL -> "自定义"
}

fun AppColorTheme.displayName(): String = when (this) {
    AppColorTheme.DEFAULT -> "默认"
    AppColorTheme.WHITE -> "纯净白"
    AppColorTheme.DYNAMIC -> "莫奈动态取色"
    AppColorTheme.SAGE -> "静谧青"
    AppColorTheme.OCEAN -> "雾海蓝"
    AppColorTheme.VIOLET -> "暮光紫"
    AppColorTheme.AMBER -> "暖琥珀"
}

fun AppUiStyle.displayName(): String = when (this) {
    AppUiStyle.MATERIAL -> "Material"
    AppUiStyle.MIUIX -> "MIUIX"
}
