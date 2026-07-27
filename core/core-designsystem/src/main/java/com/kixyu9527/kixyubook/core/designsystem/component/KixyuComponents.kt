package com.kixyu9527.kixyubook.core.designsystem.component

import android.graphics.Color.parseColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.AppColorTheme
import com.kixyu9527.kixyubook.core.common.model.CustomReaderTheme
import com.kixyu9527.kixyubook.core.common.model.PageMode
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme

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
    val floatingActionClearance = 72.dp
    val bottomNavigationContentHeight = 80.dp
    val readerControlInset = 12.dp
    val readerControlButton = 48.dp
    val readerPageIndicatorWidth = 52.dp
    val readerMenuBottomOffset = 68.dp
    val readerSheetMaxContent = 620.dp
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
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            content = { Column(content = content) },
        )
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

@Composable
fun KixyuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = KixyuSpacing.rowHorizontal + KixyuSize.icon + KixyuSpacing.medium),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
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
    KixyuDropdownRow(
        title = modeTitle,
        selected = settings.theme,
        options = listOf(ReaderTheme.SYSTEM, ReaderTheme.DAY, ReaderTheme.NIGHT),
        optionLabel = ReaderTheme::displayName,
        onSelected = { onSettingsChange(settings.copy(theme = it)) },
    )
    KixyuDivider()
    KixyuSettingsRow(
        title = "自定义阅读配色",
        supportingText = if (settings.theme == ReaderTheme.CUSTOM) "正在实时预览" else "背景、正文、标题与强调色",
        onClick = { onSettingsChange(settings.copy(theme = ReaderTheme.CUSTOM)) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall)) {
                with(settings.customTheme) {
                    KixyuColorSwatch(backgroundHex)
                    KixyuColorSwatch(bodyHex)
                    KixyuColorSwatch(titleHex)
                    KixyuColorSwatch(accentHex)
                }
            }
        },
    )
    if (settings.theme == ReaderTheme.CUSTOM) {
        CustomThemeEditor(settings.customTheme) { custom ->
            onSettingsChange(settings.copy(customTheme = custom))
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
    ReaderTheme.CUSTOM -> "自定义"
}

fun PageMode.displayName(): String = when (this) {
    PageMode.SCROLL -> "上下滑动"
    PageMode.PAGED -> "左右翻页"
}

fun AppColorTheme.displayName(): String = when (this) {
    AppColorTheme.DYNAMIC -> "系统动态色"
    AppColorTheme.SAGE -> "静谧青"
    AppColorTheme.OCEAN -> "雾海蓝"
    AppColorTheme.VIOLET -> "暮光紫"
    AppColorTheme.AMBER -> "暖琥珀"
}

private val HEX_COLOR_PATTERN = Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")
private const val MAX_HEX_LENGTH = 9
