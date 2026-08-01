package com.kixyu9527.kixyubook.core.designsystem.component

import android.graphics.Color.parseColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.kixyu9527.kixyubook.core.common.model.AppColorTheme
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.CustomReaderTheme
import com.kixyu9527.kixyubook.core.common.model.PageMode
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

/** Material sheets use a low container, so grouped content needs the next tonal elevation. */
internal val LocalKixyuSheetSection = staticCompositionLocalOf { false }

object KixyuSpacing {
    val hairline = 1.dp
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 20.dp
    val screenHorizontal = 16.dp
    val screenVertical = 12.dp
    val sectionGap = 12.dp
    val rowHorizontal = 14.dp
    val rowVertical = 10.dp
}

object KixyuSize {
    val iconSmall = 18.dp
    val icon = 20.dp
    val rowMinHeight = 52.dp
    val colorSwatch = 24.dp
    val progressHeight = 4.dp
    val compactButtonIconGap = 6.dp
    val searchCorner = 16.dp
    val libraryCoverWidth = 62.dp
    val libraryCoverHeight = 88.dp
    val continueCoverWidth = 84.dp
    val continueCoverHeight = 118.dp
    val recentCoverWidth = 46.dp
    val recentCoverHeight = 64.dp
    val bottomNavigationItemWidth = 80.dp
    val bottomNavigationBarHeight = 64.dp
    val bottomNavigationCornerRadius = 32.dp
    val bottomNavigationContentHeight = 76.dp
    val navigationRailWidth = 88.dp
    val pageContentMaxWidth = 840.dp
    val expandedPageContentMaxWidth = 1200.dp
    val readerTextMaxWidth = 760.dp
    val readerSpreadGutter = 20.dp
    val sheetContentMaxWidth = 720.dp
    val adaptiveDialogMaxWidth = 680.dp
    val adaptiveDialogMaxHeight = 640.dp
    val readerControlInset = 12.dp
    val readerTopControlInset = 0.dp
    val readerControlButton = 48.dp
    val readerChapterActionGap = 4.dp
    val readerBookTitleHeight = 40.dp
    val readerBookTitleMaxWidth = 200.dp
    val stepperButton = 36.dp
    val stepperValueWidth = 64.dp
    val readerMenuBottomOffset = 68.dp
    val readerSheetMaxContent = 620.dp
    val updateNotesMaxHeight = 420.dp
    val readerSearchPanelMaxWidth = 600.dp
    val readerSearchPanelMaxHeight = 560.dp
    val directoryFastScrollerWidth = 40.dp
    val directoryFastScrollerThumbWidth = 32.dp
    val directoryFastScrollerThumbHeight = 48.dp
    val directoryFastScrollerTrackWidth = 2.dp
}

@Composable
fun KixyuSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = KixyuSpacing.extraSmall),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
            MiuixCard(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                // MIUIX owns its own content-color local. Material Text/Icon used by feature
                // content must receive the bridged Material color explicitly in dark mode.
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    content()
                }
            }
        } else {
            Surface(
                color = if (LocalKixyuSheetSection.current) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                shape = MaterialTheme.shapes.large,
                content = { Column(content = content) },
            )
        }
    }
}

@Composable
fun KixyuSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixBasicComponent(
            modifier = modifier.fillMaxWidth().heightIn(min = KixyuSize.rowMinHeight),
            title = title,
            summary = supportingText,
            startAction = icon?.let { image ->
                {
                    Icon(
                        image,
                        null,
                        Modifier.size(KixyuSize.icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            endActions = trailing,
            insideMargin = PaddingValues(
                horizontal = KixyuSpacing.rowHorizontal,
                vertical = KixyuSpacing.rowVertical,
            ),
            onClick = onClick,
        )
        return
    }
    val interactionModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Row(
        interactionModifier.fillMaxWidth().heightIn(min = KixyuSize.rowMinHeight)
            .padding(horizontal = KixyuSpacing.rowHorizontal, vertical = KixyuSpacing.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, null, Modifier.size(KixyuSize.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(KixyuSpacing.medium))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(KixyuSpacing.small))
        trailing()
    }
}

/** Style-aware compact row used by long, scrollable collections such as the directory. */
@Composable
fun KixyuListRow(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    selected: Boolean = false,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        highlighted -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixBasicComponent(
            modifier = modifier.fillMaxWidth()
                .background(containerColor, MaterialTheme.shapes.large)
                .heightIn(min = KixyuSize.rowMinHeight),
            title = title,
            summary = supportingText,
            startAction = leading,
            endActions = { trailing() },
            insideMargin = PaddingValues(
                horizontal = KixyuSpacing.rowHorizontal,
                vertical = KixyuSpacing.rowVertical,
            ),
            onClick = onClick,
        )
    } else {
        ListItem(
            headlineContent = {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = supportingText?.let { summary ->
                {
                    Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            leadingContent = leading,
            trailingContent = { trailing() },
            colors = ListItemDefaults.colors(containerColor = containerColor),
            modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        )
    }
}

@Composable
fun KixyuDivider() {
    val modifier = Modifier.padding(start = KixyuSpacing.rowHorizontal + KixyuSize.icon + KixyuSpacing.medium)
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixHorizontalDivider(modifier = modifier)
    } else {
        HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun KixyuStepperRow(
    title: String,
    valueLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseEnabled: Boolean = true,
    increaseEnabled: Boolean = true,
) {
    KixyuSettingsRow(title = title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KixyuTonalIconButton(
                onClick = onDecrease,
                enabled = decreaseEnabled,
                modifier = Modifier.size(KixyuSize.stepperButton),
                minSize = KixyuSize.stepperButton,
            ) {
                Icon(Icons.Outlined.Remove, "减小$title", Modifier.size(KixyuSize.iconSmall))
            }
            Text(
                valueLabel,
                modifier = Modifier.width(KixyuSize.stepperValueWidth),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            KixyuTonalIconButton(
                onClick = onIncrease,
                enabled = increaseEnabled,
                modifier = Modifier.size(KixyuSize.stepperButton),
                minSize = KixyuSize.stepperButton,
            ) {
                Icon(Icons.Outlined.Add, "增大$title", Modifier.size(KixyuSize.iconSmall))
            }
        }
    }
}

@Composable
fun <T> KixyuDropdownRow(
    title: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        val labels = options.map(optionLabel)
        val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
        WindowDropdownPreference(
            items = labels,
            selectedIndex = selectedIndex,
            title = title,
            modifier = modifier.heightIn(min = KixyuSize.rowMinHeight),
            startAction = icon?.let { image ->
                {
                    Icon(
                        image,
                        null,
                        Modifier.size(KixyuSize.icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            onSelectedIndexChange = { index -> options.getOrNull(index)?.let(onSelected) },
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    KixyuSettingsRow(
        title = title,
        modifier = modifier,
        icon = icon,
        onClick = { expanded = true },
    ) {
        // Keep the popup anchored to the trailing value instead of the row's
        // left edge, so its origin matches the selected value on screen.
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    optionLabel(selected),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(KixyuSize.icon))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), maxLines = 1) },
                        onClick = { expanded = false; onSelected(option) },
                        leadingIcon = { RadioButton(selected = selected == option, onClick = null) },
                    )
                }
            }
        }
    }
}

@Composable
fun KixyuColorSwatch(hex: String, modifier: Modifier = Modifier) {
    val color = remember(hex) { runCatching { Color(parseColor(hex)) }.getOrDefault(Color.Transparent) }
    Surface(
        modifier = modifier.size(KixyuSize.colorSwatch).clip(MaterialTheme.shapes.small),
        color = color,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(KixyuSpacing.hairline, MaterialTheme.colorScheme.outlineVariant),
        content = {},
    )
}

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
    KixyuDropdownRow(
        title = modeTitle,
        selected = settings.theme,
        options = listOf(ReaderTheme.SYSTEM, ReaderTheme.DAY, ReaderTheme.NIGHT),
        optionLabel = ReaderTheme::displayName,
        onSelected = { onSettingsChange(settings.copy(theme = it)) },
    )
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
        icon = Icons.Outlined.FontDownload,
        onSelected = { option ->
            if (option.addFont) onAddFont() else onSelectFont(option.uuid)
        },
    )
    selectedUserFont?.let { font ->
        KixyuDivider()
        KixyuSettingsRow(
            title = "删除当前字体",
            supportingText = font.name,
            icon = Icons.Outlined.DeleteOutline,
            onClick = { onDeleteFont(font) },
        ) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon))
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
        supportingText = "阅读时显示时间和系统状态",
        checked = settings.showStatusBar,
    ) { onSettingsChange(settings.copy(showStatusBar = it)) }
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

@Composable
fun KixyuSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixSwitch(checked, onCheckedChange, modifier, enabled = enabled)
    } else {
        Switch(checked, onCheckedChange, modifier, enabled = enabled)
    }
}

data class KixyuNavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun KixyuNavigationBar(
    items: List<KixyuNavigationItem>,
    selectedKey: String?,
    onSelected: (KixyuNavigationItem) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(KixyuSize.bottomNavigationItemWidth * items.size)
                .heightIn(
                    min = KixyuSize.bottomNavigationContentHeight,
                    max = KixyuSize.bottomNavigationContentHeight,
                )
                .padding(vertical = (KixyuSize.bottomNavigationContentHeight - KixyuSize.bottomNavigationBarHeight) / 2),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(KixyuSize.bottomNavigationItemWidth * items.size)
                    .heightIn(
                        min = KixyuSize.bottomNavigationBarHeight,
                        max = KixyuSize.bottomNavigationBarHeight,
                    ),
                shape = RoundedCornerShape(KixyuSize.bottomNavigationCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = KixyuSpacing.extraSmall,
            ) {
                if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
                    MiuixNavigationBar(
                        modifier = Modifier.fillMaxSize(),
                        showDivider = false,
                        defaultWindowInsetsPadding = false,
                    ) {
                        items.forEach { item ->
                            MiuixNavigationBarItem(
                                selected = selectedKey == item.route,
                                onClick = { onSelected(item) },
                                icon = item.icon,
                                label = item.label,
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                            )
                        }
                    }
                } else {
                    NavigationBar(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        items.forEach { item ->
                            NavigationBarItem(
                                selected = selectedKey == item.route,
                                onClick = { onSelected(item) },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

private val HEX_COLOR_PATTERN = Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")
private const val MAX_HEX_LENGTH = 9
