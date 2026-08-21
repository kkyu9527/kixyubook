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
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenuItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuGlassSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTonalIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPredictivePopupTransform
import com.kixyu9527.kixyubook.core.reader.engine.*


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderControls(
    visible: Boolean,
    menuVisible: Boolean,
    toolsMenuVisible: Boolean,
    controlsBackProgress: Float,
    popupBackProgress: Float,
    bookTitle: String,
    accentColor: Color,
    backgroundColor: Color,
    backdrop: KixyuNavigationBackdrop,
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
    var retainedToolsPopup by remember { mutableStateOf(false) }
    LaunchedEffect(menuVisible, toolsMenuVisible) {
        when {
            toolsMenuVisible -> retainedToolsPopup = true
            menuVisible -> retainedToolsPopup = false
        }
    }
    val showToolsPopup = if (popupVisible) toolsMenuVisible else retainedToolsPopup
    val controlsBackModifier = Modifier.kixyuPredictivePopupTransform(controlsBackProgress)
    ReaderControlVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
    ) {
        val stableControlInsets = WindowInsets.statusBarsIgnoringVisibility
            .union(WindowInsets.navigationBarsIgnoringVisibility)
            .union(WindowInsets.displayCutout)
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(stableControlInsets)) {
            if (bookTitle.isNotBlank()) {
                val titleContainerColor = lerp(backgroundColor, accentColor, READER_CONTROL_FALLBACK_ACCENT_MIX)
                val titleContentColor = if (
                    accentColor.contrastRatio(backgroundColor) >= MIN_TEXT_CONTRAST
                ) accentColor else backgroundColor.highContrastContentColor()
                val titleWidth = minOf(
                    maxWidth * KixyuSize.readerBookTitleWidthFraction,
                    KixyuSize.readerBookTitleMaxWidth,
                )
                KixyuGlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(top = KixyuSize.readerTopControlInset)
                        .width(titleWidth)
                        .height(KixyuSize.readerBookTitleMinHeight)
                        .then(controlsBackModifier),
                    fallbackContainerColor = titleContainerColor,
                    contentColor = titleContentColor,
                ) {
                    Row(
                        Modifier.fillMaxSize()
                            .clip(MaterialTheme.shapes.extraLarge)
                            .clickable(onClick = onBookInfo)
                            .padding(
                                start = KixyuSpacing.large,
                                end = KixyuSpacing.medium,
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
            val dockContainerColor = lerp(backgroundColor, accentColor, READER_CONTROL_FALLBACK_ACCENT_MIX)
            val dockContentColor = if (accentColor.contrastRatio(backgroundColor) >= MIN_ICON_CONTRAST) {
                accentColor
            } else {
                backgroundColor.highContrastContentColor()
            }
            val popupItems = if (showToolsPopup) {
                listOf(
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
                )
            } else {
                listOf(
                    KixyuPopupMenuItem("显示与亮度", KixyuSymbols.Palette) {
                        onSheet(ReaderSheet.THEME)
                    },
                    KixyuPopupMenuItem("排版与翻页", KixyuSymbols.ViewCarousel) {
                        onSheet(ReaderSheet.LAYOUT)
                    },
                    KixyuPopupMenuItem("阅读控制", KixyuSymbols.Tune) {
                        onSheet(ReaderSheet.INFORMATION)
                    },
                )
            }
            AnimatedVisibility(
                visible = popupVisible,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = KixyuSize.readerMenuBottomOffset + KixyuSize.readerControlInset),
                enter = fadeIn() + slideInVertically { it / 5 },
                exit = fadeOut() + slideOutVertically { it / 5 },
            ) {
                ReaderInlinePopup(
                    items = popupItems,
                    backdrop = backdrop,
                    backgroundColor = backgroundColor,
                    contentColor = dockContentColor,
                    modifier = Modifier.kixyuPredictivePopupTransform(popupBackProgress),
                )
            }
            KixyuGlassSurface(
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = KixyuSize.readerControlInset)
                    .then(controlsBackModifier),
                fallbackContainerColor = dockContainerColor,
                contentColor = dockContentColor,
            ) {
                Row(
                    modifier = Modifier.padding(KixyuSpacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KixyuSize.readerChapterActionGap),
                ) {
                    ReaderDockIconButton(onClick = onExit, contentColor = dockContentColor) {
                        Icon(KixyuSymbols.Close, "退出")
                    }
                    ReaderDockIconButton(onClick = onDirectory, contentColor = dockContentColor) {
                        Icon(KixyuSymbols.Toc, "目录")
                    }
                    ReaderDockIconButton(
                        onClick = onPreviousChapter,
                        contentColor = dockContentColor,
                        enabled = hasPreviousChapter,
                    ) { Icon(KixyuSymbols.SkipPrevious, "上一章") }
                    ReaderDockIconButton(
                        onClick = onNextChapter,
                        contentColor = dockContentColor,
                        enabled = hasNextChapter,
                    ) { Icon(KixyuSymbols.SkipNext, "下一章") }
                    ReaderDockIconButton(onClick = onTools, contentColor = dockContentColor) {
                        Icon(KixyuSymbols.MoreHoriz, "阅读工具")
                    }
                    ReaderDockIconButton(onClick = onSettings, contentColor = dockContentColor) {
                        Icon(KixyuSymbols.Settings, "设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderInlinePopup(
    items: List<KixyuPopupMenuItem>,
    backdrop: KixyuNavigationBackdrop,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    KixyuGlassSurface(
        backdrop = backdrop,
        modifier = modifier.widthIn(min = 208.dp, max = 320.dp),
        fallbackContainerColor = backgroundColor,
        contentColor = contentColor,
    ) {
        Column(Modifier.padding(vertical = KixyuSpacing.extraSmall)) {
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = item.enabled, onClick = item.onClick)
                        .padding(horizontal = KixyuSpacing.large),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(KixyuSize.icon),
                        tint = if (item.enabled) contentColor else contentColor.copy(alpha = .38f),
                    )
                    Spacer(Modifier.width(KixyuSpacing.medium))
                    Text(
                        text = item.label,
                        color = if (item.enabled) contentColor else contentColor.copy(alpha = .38f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderDockIconButton(
    onClick: () -> Unit,
    contentColor: Color,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    KixyuTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(KixyuSize.readerControlButton),
        enabled = enabled,
        containerColor = Color.Transparent,
        contentColor = contentColor,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = contentColor.copy(alpha = .38f),
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
