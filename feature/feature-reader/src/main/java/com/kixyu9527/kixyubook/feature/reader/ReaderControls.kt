package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenu
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenuItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTonalIconButton
import com.kixyu9527.kixyubook.core.reader.engine.*


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderControls(
    visible: Boolean,
    menuVisible: Boolean,
    toolsMenuVisible: Boolean,
    progress: Float,
    bookTitle: String,
    accentColor: Color,
    backgroundColor: Color,
    currentPageBookmarked: Boolean,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onExit: () -> Unit,
    onDirectory: () -> Unit,
    onBookInfo: () -> Unit,
    onSettings: () -> Unit,
    onTools: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSearch: () -> Unit,
    onSheet: (ReaderSheet) -> Unit,
) {
    val popupVisible = menuVisible || toolsMenuVisible
    val controlsBackModifier = if (popupVisible) Modifier else Modifier.predictivePopupTransform(progress)
    ReaderControlVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
    ) {
        val stableControlInsets = WindowInsets.statusBarsIgnoringVisibility
            .union(WindowInsets.navigationBarsIgnoringVisibility)
            .union(WindowInsets.displayCutout)
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(stableControlInsets)) {
            if (bookTitle.isNotBlank()) {
                val titleContainerColor = lerp(backgroundColor, accentColor, READER_CONTROL_ACCENT_MIX)
                val titleContentColor = if (
                    accentColor.contrastRatio(titleContainerColor) >= MIN_TEXT_CONTRAST
                ) accentColor else titleContainerColor.highContrastContentColor()
                val titleWidth = minOf(
                    maxWidth * KixyuSize.readerBookTitleWidthFraction,
                    KixyuSize.readerBookTitleMaxWidth,
                )
                KixyuPopupSurface(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(top = KixyuSize.readerTopControlInset)
                        .width(titleWidth)
                        .heightIn(min = KixyuSize.readerBookTitleMinHeight)
                        .then(controlsBackModifier),
                    shadowElevation = KixyuSpacing.extraSmall,
                    containerColor = titleContainerColor,
                    contentColor = titleContentColor,
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraLarge)
                            .clickable(onClick = onBookInfo)
                            .padding(
                                start = KixyuSpacing.large,
                                top = KixyuSpacing.small,
                                end = KixyuSpacing.medium,
                                bottom = KixyuSpacing.small,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = bookTitle,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = KixyuSymbols.Info,
                            contentDescription = "查看书籍信息",
                            modifier = Modifier.size(KixyuSize.iconSmall),
                            tint = titleContentColor.copy(alpha = .72f),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = KixyuSize.readerControlInset)
                    .then(controlsBackModifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KixyuSize.readerChapterActionGap),
            ) {
                ReaderControlIconButton(
                    onClick = onExit,
                    accentColor = accentColor,
                    backgroundColor = backgroundColor,
                    modifier = Modifier.size(KixyuSize.readerControlButton),
                ) { Icon(KixyuSymbols.Close, "退出") }
                ReaderControlIconButton(
                    onClick = onDirectory,
                    accentColor = accentColor,
                    backgroundColor = backgroundColor,
                    modifier = Modifier.size(KixyuSize.readerControlButton),
                ) { Icon(KixyuSymbols.Toc, "目录") }
                ReaderControlIconButton(
                    onClick = onPreviousChapter,
                    accentColor = accentColor,
                    backgroundColor = backgroundColor,
                    enabled = hasPreviousChapter,
                    modifier = Modifier.size(KixyuSize.readerControlButton),
                ) {
                    Icon(KixyuSymbols.SkipPrevious, "上一章")
                }
                ReaderControlIconButton(
                    onClick = onNextChapter,
                    accentColor = accentColor,
                    backgroundColor = backgroundColor,
                    enabled = hasNextChapter,
                    modifier = Modifier.size(KixyuSize.readerControlButton),
                ) {
                    Icon(KixyuSymbols.SkipNext, "下一章")
                }
                Box {
                    ReaderControlIconButton(
                        onClick = onTools,
                        accentColor = accentColor,
                        backgroundColor = backgroundColor,
                        modifier = Modifier.size(KixyuSize.readerControlButton),
                    ) { Icon(KixyuSymbols.MoreHoriz, "阅读工具") }
                    KixyuPopupMenu(
                        expanded = toolsMenuVisible,
                        onDismissRequest = { if (toolsMenuVisible) onTools() },
                        alignEnd = true,
                        items = listOf(
                            KixyuPopupMenuItem(
                                label = if (currentPageBookmarked) "移除当前页书签" else "添加当前页书签",
                                icon = if (currentPageBookmarked) KixyuSymbols.BookmarkFilled else KixyuSymbols.BookmarkAdd,
                                onClick = onToggleBookmark,
                            ),
                            KixyuPopupMenuItem(
                                label = "全文搜索",
                                icon = KixyuSymbols.Search,
                                onClick = onSearch,
                            ),
                        ),
                    )
                }
                Box {
                    ReaderControlIconButton(
                        onClick = onSettings,
                        accentColor = accentColor,
                        backgroundColor = backgroundColor,
                        modifier = Modifier.size(KixyuSize.readerControlButton),
                    ) { Icon(KixyuSymbols.Settings, "设置") }
                    KixyuPopupMenu(
                        expanded = menuVisible,
                        onDismissRequest = { if (menuVisible) onSettings() },
                        alignEnd = true,
                        items = listOf(
                            KixyuPopupMenuItem("阅读配色", KixyuSymbols.Palette) {
                                onSettings(); onSheet(ReaderSheet.THEME)
                            },
                            KixyuPopupMenuItem("排版与翻页", KixyuSymbols.ViewCarousel) {
                                onSettings(); onSheet(ReaderSheet.LAYOUT)
                            },
                            KixyuPopupMenuItem("阅读行为", KixyuSymbols.Tune) {
                                onSettings(); onSheet(ReaderSheet.SETTINGS)
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReaderControlIconButton(
    onClick: () -> Unit,
    accentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val containerColor = lerp(backgroundColor, accentColor, READER_CONTROL_ACCENT_MIX)
    val contentColor = if (accentColor.contrastRatio(containerColor) >= MIN_ICON_CONTRAST) {
        accentColor
    } else {
        containerColor.highContrastContentColor()
    }
    KixyuTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = lerp(backgroundColor, accentColor, READER_CONTROL_DISABLED_ACCENT_MIX),
        disabledContentColor = contentColor.copy(alpha = .5f),
        content = content,
    )
}

internal fun Color.contrastRatio(other: Color): Float {
    val first = luminance()
    val second = other.luminance()
    val lighter = maxOf(first, second)
    val darker = minOf(first, second)
    return (lighter + .05f) / (darker + .05f)
}

/** Chooses the WCAG contrast winner so arbitrary custom accent colors remain legible. */
internal fun Color.highContrastContentColor(): Color {
    val relativeLuminance = luminance()
    val blackContrast = (relativeLuminance + .05f) / .05f
    val whiteContrast = 1.05f / (relativeLuminance + .05f)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private const val MIN_TEXT_CONTRAST = 4.5f

internal fun Modifier.predictivePopupTransform(progress: Float): Modifier = graphicsLayer {
    alpha = 1f - progress
    scaleX = 1f - progress * .08f
    scaleY = scaleX
}
