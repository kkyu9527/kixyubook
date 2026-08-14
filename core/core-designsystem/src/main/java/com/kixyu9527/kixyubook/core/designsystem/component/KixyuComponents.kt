package com.kixyu9527.kixyubook.core.designsystem.component

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import android.graphics.Color.parseColor
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults as MiuixBasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.InputField as MiuixInputField
import top.yukonga.miuix.kmp.basic.SearchBarDefaults as MiuixSearchBarDefaults
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
    val contextMenuItemHeight = 44.dp
    val contextMenuWidth = 144.dp
    val colorSwatch = 24.dp
    val accountAvatar = 56.dp
    val progressHeight = 4.dp
    val compactButtonIconGap = 6.dp
    val searchCorner = 16.dp
    val libraryCoverWidth = 62.dp
    val libraryCoverHeight = 88.dp
    val libraryDetailCoverWidth = 128.dp
    val libraryDetailCoverHeight = 180.dp
    const val libraryCategorySelectorWidthFraction = 0.4f
    val libraryCategoryMenuMaxHeight = 320.dp
    val continueCoverWidth = 84.dp
    val continueCoverHeight = 118.dp
    val recentCoverWidth = 46.dp
    val recentCoverHeight = 64.dp
    val bottomNavigationItemWidth = 80.dp
    val bottomNavigationBarHeight = 64.dp
    val navigationContainerCornerRadius = 16.dp
    val bottomNavigationContentHeight = 76.dp
    val navigationRailWidth = 72.dp
    val navigationRailItemHeight = 64.dp
    val navigationRailLabeledItemHeight = 76.dp
    val navigationRailContentWidth = 96.dp
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
    val readerBookTitleMinHeight = 48.dp
    val readerBookTitleMaxWidth = 360.dp
    const val readerBookTitleWidthFraction = .76f
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
    contentColor: Color? = null,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val isSelected = selected == true
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val selectedContentColor = MaterialTheme.colorScheme.onPrimary
    val selectedSupportingColor = selectedContentColor.copy(alpha = .78f)
    val rowContentColor = contentColor ?: MaterialTheme.colorScheme.onSurface
    val rowSupportingColor = contentColor?.copy(alpha = .78f)
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    val rowModifier = (if (selected == null) {
        modifier
    } else {
        modifier.semantics { this.selected = isSelected }
    }).fillMaxWidth()
        // Keep both Material ripple and MIUIX press feedback inside the same rounded row shape.
        // A rounded background alone does not clip an interaction indication added afterwards.
        .clip(MaterialTheme.shapes.medium)
        .background(containerColor, MaterialTheme.shapes.medium)
        .heightIn(min = KixyuSize.rowMinHeight)
    val leadingContent: (@Composable () -> Unit)? = leading ?: icon?.let { image ->
        {
            Icon(
                image,
                null,
                Modifier.size(KixyuSize.icon),
                tint = if (isSelected) {
                    selectedContentColor
                } else {
                    contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        MiuixBasicComponent(
            modifier = rowModifier,
            title = title,
            titleColor = if (isSelected) {
                MiuixBasicComponentDefaults.titleColor(selectedContentColor)
            } else if (contentColor != null) {
                MiuixBasicComponentDefaults.titleColor(rowContentColor)
            } else {
                MiuixBasicComponentDefaults.titleColor()
            },
            summary = supportingText,
            summaryColor = if (isSelected) {
                MiuixBasicComponentDefaults.summaryColor(selectedSupportingColor)
            } else if (contentColor != null) {
                MiuixBasicComponentDefaults.summaryColor(rowSupportingColor)
            } else {
                MiuixBasicComponentDefaults.summaryColor()
            },
            startAction = leadingContent,
            endActions = {
                CompositionLocalProvider(
                    LocalContentColor provides if (isSelected) {
                        selectedContentColor
                    } else {
                        contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    trailing()
                }
            },
            insideMargin = PaddingValues(
                horizontal = KixyuSpacing.rowHorizontal,
                vertical = KixyuSpacing.rowVertical,
            ),
            onClick = onClick,
        )
        return
    }
    val interactionModifier = if (onClick == null) rowModifier else rowModifier.clickable(onClick = onClick)
    Row(
        interactionModifier.padding(
            horizontal = KixyuSpacing.rowHorizontal,
            vertical = KixyuSpacing.rowVertical,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.let {
            it()
            Spacer(Modifier.width(KixyuSpacing.medium))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) {
                    selectedContentColor
                } else {
                    rowContentColor
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        selectedSupportingColor
                    } else {
                        rowSupportingColor
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(KixyuSpacing.small))
        CompositionLocalProvider(
            LocalContentColor provides if (isSelected) {
                selectedContentColor
            } else {
                contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
            },
        ) {
            trailing()
        }
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
    val isMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val containerColor = when {
        selected && isMiuix -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.secondaryContainer
        highlighted -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    if (isMiuix) {
        val selectedContentColor = MaterialTheme.colorScheme.onPrimary
        val selectedSupportingColor = selectedContentColor.copy(alpha = .78f)
        MiuixBasicComponent(
            modifier = modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(containerColor, MaterialTheme.shapes.large)
                .heightIn(min = KixyuSize.rowMinHeight),
            title = title,
            titleColor = if (selected) {
                MiuixBasicComponentDefaults.titleColor(selectedContentColor)
            } else {
                MiuixBasicComponentDefaults.titleColor()
            },
            summary = supportingText,
            summaryColor = if (selected) {
                MiuixBasicComponentDefaults.summaryColor(selectedSupportingColor)
            } else {
                MiuixBasicComponentDefaults.summaryColor()
            },
            startAction = leading?.let { content ->
                {
                    CompositionLocalProvider(
                        LocalContentColor provides if (selected) {
                            selectedContentColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) { content() }
                }
            },
            endActions = {
                CompositionLocalProvider(
                    LocalContentColor provides if (selected) {
                        selectedContentColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) { trailing() }
            },
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
            modifier = modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick),
        )
    }
}

/** Search input that follows the selected Material 3 or MIUIX component system. */
@Composable
fun KixyuSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        val spacedLeadingIcon = leadingIcon?.let { icon ->
            @Composable {
                Box(
                    modifier = Modifier.padding(
                        start = MiuixSearchBarDefaults.LeadingIconStartPadding,
                        end = MiuixSearchBarDefaults.LeadingIconEndPadding,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
            }
        }
        MiuixInputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { onSearch() },
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = modifier.fillMaxWidth().heightIn(min = KixyuSize.rowMinHeight),
            label = placeholder,
            leadingIcon = spacedLeadingIcon,
            trailingIcon = trailingIcon,
        )
    } else {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, maxLines = 1) },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
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
                Icon(KixyuSymbols.Remove, "减小$title", Modifier.size(KixyuSize.iconSmall))
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
                Icon(KixyuSymbols.Add, "增大$title", Modifier.size(KixyuSize.iconSmall))
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
                Icon(KixyuSymbols.ArrowDropDown, null, Modifier.size(KixyuSize.icon))
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
    val appUiStyle = LocalAppUiStyle.current
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
                shape = RoundedCornerShape(KixyuSize.navigationContainerCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = if (appUiStyle == AppUiStyle.MIUIX) 0.dp else KixyuSpacing.extraSmall,
            ) {
                if (appUiStyle == AppUiStyle.MIUIX) {
                    MiuixNavigationBar(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
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

internal val HEX_COLOR_PATTERN = Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")
internal const val MAX_HEX_LENGTH = 9
