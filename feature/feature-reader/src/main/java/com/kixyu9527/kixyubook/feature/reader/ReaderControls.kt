package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
        Box(Modifier.fillMaxSize().windowInsetsPadding(stableControlInsets)) {
            if (bookTitle.isNotBlank()) {
                KixyuPopupSurface(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(top = KixyuSize.readerTopControlInset)
                        .height(KixyuSize.readerBookTitleHeight)
                        .widthIn(max = KixyuSize.readerBookTitleMaxWidth)
                        .then(controlsBackModifier),
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .clip(MaterialTheme.shapes.extraLarge)
                            .clickable(onClick = onBookInfo)
                            .padding(horizontal = KixyuSpacing.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            bookTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                ) { Icon(Icons.Outlined.Close, "退出") }
                ReaderControlIconButton(
                    onClick = onDirectory,
                    accentColor = accentColor,
                    backgroundColor = backgroundColor,
                    modifier = Modifier.size(KixyuSize.readerControlButton),
                ) { Icon(Icons.AutoMirrored.Outlined.Toc, "目录") }
                ReaderControlIconButton(
                    onClick = onPreviousChapter,
                    accentColor = accentColor,
                    backgroundColor = backgroundColor,
                    enabled = hasPreviousChapter,
                    modifier = Modifier.size(KixyuSize.readerControlButton),
                ) {
                    Icon(Icons.Outlined.SkipPrevious, "上一章")
                }
                ReaderControlIconButton(
                    onClick = onNextChapter,
                    accentColor = accentColor,
                    backgroundColor = backgroundColor,
                    enabled = hasNextChapter,
                    modifier = Modifier.size(KixyuSize.readerControlButton),
                ) {
                    Icon(Icons.Outlined.SkipNext, "下一章")
                }
                Box {
                    ReaderControlIconButton(
                        onClick = onTools,
                        accentColor = accentColor,
                        backgroundColor = backgroundColor,
                        modifier = Modifier.size(KixyuSize.readerControlButton),
                    ) { Icon(Icons.Outlined.MoreHoriz, "阅读工具") }
                    KixyuPopupMenu(
                        expanded = toolsMenuVisible,
                        onDismissRequest = { if (toolsMenuVisible) onTools() },
                        alignEnd = true,
                        items = listOf(
                            KixyuPopupMenuItem(
                                label = if (currentPageBookmarked) "移除当前页书签" else "添加当前页书签",
                                icon = if (currentPageBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                                onClick = onToggleBookmark,
                            ),
                            KixyuPopupMenuItem(
                                label = "全文搜索",
                                icon = Icons.Outlined.Search,
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
                    ) { Icon(Icons.Outlined.Settings, "设置") }
                    KixyuPopupMenu(
                        expanded = menuVisible,
                        onDismissRequest = { if (menuVisible) onSettings() },
                        alignEnd = true,
                        items = listOf(
                            KixyuPopupMenuItem("阅读配色", Icons.Outlined.Palette) {
                                onSettings(); onSheet(ReaderSheet.THEME)
                            },
                            KixyuPopupMenuItem("排版与翻页", Icons.Outlined.ViewCarousel) {
                                onSettings(); onSheet(ReaderSheet.LAYOUT)
                            },
                            KixyuPopupMenuItem("阅读行为", Icons.Outlined.Tune) {
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

internal fun Modifier.predictivePopupTransform(progress: Float): Modifier = graphicsLayer {
    alpha = 1f - progress
    scaleX = 1f - progress * .08f
    scaleY = scaleX
}

@Composable internal fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, click: () -> Unit) {
    Row(
        Modifier.pointerInput(Unit) { detectTapGestures { click() } }
            .padding(horizontal = KixyuSpacing.large, vertical = KixyuSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
    ) { Icon(icon, null, Modifier.size(KixyuSize.icon)); Text(text, maxLines = 1) }
}
