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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.kixyu9527.kixyubook.core.common.model.AppColorTheme
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.CustomReaderTheme
import com.kixyu9527.kixyubook.core.common.model.PageMode
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
        options = AppColorTheme.entries,
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
        onSelected = { onSettingsChange(settings.copy(appUiStyle = it)) },
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
    KixyuDropdownRow(
        title = "阅读方式",
        selected = settings.pageMode,
        options = PageMode.entries,
        optionLabel = PageMode::displayName,
        onSelected = { onSettingsChange(settings.copy(pageMode = it)) },
    )
}

@Composable
fun KixyuReaderLayoutControls(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    ReaderStepper(
        title = "字号",
        value = settings.fontSize,
        step = .5f,
        range = 15f..30f,
        suffix = "sp",
    ) { onSettingsChange(settings.copy(fontSize = it)) }
    KixyuDivider()
    KixyuPageModeControl(settings, onSettingsChange)
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
        title = "显示页码",
        supportingText = "翻页模式底部显示当前页/总页数",
        checked = settings.showPageNumber,
    ) { onSettingsChange(settings.copy(showPageNumber = it)) }
    KixyuDivider()
    ReaderSwitch(
        title = "显示章节名",
        supportingText = "非章节首页顶部显示当前章节名",
        checked = settings.showChapterTitle,
    ) { onSettingsChange(settings.copy(showChapterTitle = it)) }
    KixyuDivider()
    ReaderSwitch(
        title = "音量键翻页",
        supportingText = "音量加键上一页，音量减键下一页",
        checked = settings.volumeKeyPageTurn,
    ) { onSettingsChange(settings.copy(volumeKeyPageTurn = it)) }
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

fun AppColorTheme.displayName(): String = when (this) {
    AppColorTheme.DEFAULT -> "默认"
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
