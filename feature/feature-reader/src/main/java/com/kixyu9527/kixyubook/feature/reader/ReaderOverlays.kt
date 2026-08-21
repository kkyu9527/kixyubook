package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.graphics.toColorInt
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuFontControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuGlassSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuInteractivePopupSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuListRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderBehaviorControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderBrightnessControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderInformationControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderLayoutControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderThemeControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSearchField
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSwitch
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPredictivePopupTransform
import com.kixyu9527.kixyubook.core.reader.engine.*


@Composable
internal fun ReaderFloatingSheet(
    show: Boolean,
    progress: Float,
    onDismissRequest: () -> Unit,
    backdrop: KixyuNavigationBackdrop,
    maxContentWidth: Dp,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = show,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)),
        exit = fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sheetMaxHeight = maxHeight * .82f
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = .28f * (1f - progress)))
                    .clickable(onClick = onDismissRequest),
            )
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        PaddingValues(
                            start = KixyuSpacing.medium,
                            end = KixyuSpacing.medium,
                            bottom = KixyuSize.floatingSurfaceBottomGap,
                        ),
                    )
                    .animateEnterExit(
                        enter = slideInVertically(tween(KixyuMotion.ReaderPopupEnterMillis)) { it / 3 } +
                            fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)),
                        exit = slideOutVertically(tween(KixyuMotion.ReaderPopupExitMillis)) { it / 3 } +
                            fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)),
                    )
                    .kixyuPredictivePopupTransform(progress),
            ) {
                KixyuGlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier.widthIn(max = maxContentWidth)
                        .fillMaxWidth()
                        .heightIn(max = sheetMaxHeight)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent()
                            }
                        },
                    fallbackContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    // The scroll/content owner reaches the clipped surface bounds. Individual
                    // screens provide visual content padding, never a second container inset.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { content() }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderSearchOverlay(
    visible: Boolean,
    progress: Float,
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onMove: (Int) -> Unit,
    onReturn: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(state.searchQuery) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) {
            query = state.searchQuery
            expanded = true
            withFrameNanos { }
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }
    fun submit() {
        onSearch(query.trim())
        focusManager.clearFocus()
        expanded = true
    }
    AnimatedVisibility(
        visible = visible && expanded,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(KixyuMotion.ReaderSearchEnterMillis)),
        exit = fadeOut(tween(KixyuMotion.ReaderSearchExitMillis)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .28f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(KixyuMotion.ReaderSearchEnterMillis)) +
            slideInVertically(tween(KixyuMotion.ReaderSearchEnterMillis)) { it / 5 },
        exit = fadeOut(tween(KixyuMotion.ReaderSearchExitMillis)) +
            slideOutVertically(tween(KixyuMotion.ReaderSearchExitMillis)) { it / 5 },
    ) {
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBarsIgnoringVisibility)
                        .only(WindowInsetsSides.Bottom),
                )
                .padding(horizontal = KixyuSpacing.medium, vertical = KixyuSpacing.small),
            contentAlignment = Alignment.BottomCenter,
        ) {
            KixyuInteractivePopupSurface(
                modifier = Modifier.fillMaxWidth()
                    .widthIn(max = KixyuSize.readerSearchPanelMaxWidth)
                    .heightIn(max = KixyuSize.readerSearchPanelMaxHeight)
                    .animateContentSize(tween(KixyuMotion.ReaderSearchEnterMillis))
                    .kixyuPredictivePopupTransform(progress),
                shadowElevation = 0.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(KixyuSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                ) {
                    AnimatedVisibility(visible = expanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "全文搜索",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                            )
                            KixyuIconButton(onClick = onDismiss) {
                                Icon(KixyuSymbols.Close, "关闭搜索")
                            }
                        }
                    }
                    KixyuSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = ::submit,
                        expanded = expanded,
                        onExpandedChange = { if (it) expanded = true },
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) expanded = true },
                        placeholder = "搜索书中内容",
                        leadingIcon = { Icon(KixyuSymbols.Search, null) },
                        trailingIcon = {
                            KixyuIconButton(onClick = ::submit, enabled = query.isNotBlank()) {
                                Icon(KixyuSymbols.ArrowForward, "搜索")
                            }
                        },
                    )
                    if (state.searchResults.isNotEmpty() && query.trim() == state.searchQuery) {
                if (!expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${state.selectedSearchIndex + 1}/${state.searchResults.size}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        KixyuIconButton(
                            onClick = { onMove(-1) },
                            enabled = state.selectedSearchIndex > 0,
                        ) { Icon(KixyuSymbols.KeyboardArrowUp, "上一个结果") }
                        KixyuIconButton(
                            onClick = { onMove(1) },
                            enabled = state.selectedSearchIndex < state.searchResults.lastIndex,
                        ) { Icon(KixyuSymbols.KeyboardArrowDown, "下一个结果") }
                        if (state.searchReturnAvailable) {
                            KixyuIconButton(
                                onClick = {
                                    onReturn()
                                    expanded = true
                                },
                            ) { Icon(KixyuSymbols.ArrowBack, "返回跳转前位置") }
                        }
                        KixyuIconButton(onClick = onDismiss) {
                            Icon(KixyuSymbols.Close, "退出搜索")
                        }
                    }
                } else {
                Text(
                    "${state.searchResults.size} 个匹配结果",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(bottom = KixyuSpacing.small),
                ) {
                    items(state.searchResults.size) { index ->
                        val result = state.searchResults[index]
                        KixyuListRow(
                            title = result.chapterTitle,
                            supportingText = result.text,
                            selected = index == state.selectedSearchIndex,
                            leading = { Text("${index + 1}", style = MaterialTheme.typography.labelMedium) },
                            trailing = { Icon(KixyuSymbols.ChevronRight, null) },
                            onClick = {
                                onSelect(index)
                                focusManager.clearFocus()
                                expanded = false
                            },
                        )
                    }
                }
                }
                    } else if (query.trim() == state.searchQuery && state.searchQuery.isNotBlank()) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    Text("没有找到匹配内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                    } else if (query.isNotBlank()) {
                Text(
                    "修改后按搜索更新结果",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                    }
                }
            }
        }
    }
}

@Composable internal fun ThemeSheet(
    settings: ReaderSettings,
    update: ((ReaderSettings) -> ReaderSettings) -> Unit,
    previewBrightness: (Float?) -> Unit,
    onBack: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = KixyuSpacing.large,
            vertical = KixyuSpacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { ReaderSettingsSheetHeader("显示与亮度", onBack) }
        item {
            KixyuSection(title = "显示与配色") {
                KixyuReaderThemeControls(settings, { updated -> update { updated } }, modeTitle = "显示模式")
            }
        }
        item {
            KixyuSection(title = "屏幕亮度") {
                KixyuReaderBrightnessControls(
                    settings = settings,
                    onSettingsChange = { updated -> update { updated } },
                    onBrightnessPreview = previewBrightness,
                )
            }
        }
    }
}

@Composable
internal fun LayoutSheet(
    state: ReaderUiState,
    update: ((ReaderSettings) -> ReaderSettings) -> Unit,
    addFont: () -> Unit,
    deleteFont: (UserFont) -> Unit,
    onBack: () -> Unit,
) {
    val settings = state.settings
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = KixyuSpacing.large,
            vertical = KixyuSpacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { ReaderSettingsSheetHeader("排版与翻页", onBack) }
        item {
            KixyuSection(title = "排版与翻页") {
                KixyuFontControls(
                    fonts = state.availableFonts,
                    selectedFontUuid = settings.fontUuid,
                    onSelectFont = { uuid -> update { it.copy(fontUuid = uuid) } },
                    onAddFont = addFont,
                    onDeleteFont = deleteFont,
                )
                KixyuDivider()
                KixyuReaderLayoutControls(settings) { updated -> update { updated } }
            }
        }
    }
}

@Composable
internal fun ReaderInformationSheet(
    settings: ReaderSettings,
    update: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onBack: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = KixyuSpacing.large,
            vertical = KixyuSpacing.large,
        ),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { ReaderSettingsSheetHeader("阅读控制", onBack) }
        item {
            KixyuSection(title = "翻页控制") {
                KixyuReaderBehaviorControls(settings) { updated -> update { updated } }
            }
        }
        item {
            KixyuSection(title = "阅读信息") {
                KixyuReaderInformationControls(settings) { updated -> update { updated } }
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheetHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KixyuIconButton(onClick = onBack) {
            Icon(KixyuSymbols.ArrowBack, "返回阅读设置")
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
        )
    }
}


@Composable internal fun BookInfoDialog(
    show: Boolean,
    book: Book?,
    progress: Float,
    backdrop: KixyuNavigationBackdrop,
    dismiss: () -> Unit,
) {
    val current = book ?: return
    AnimatedVisibility(
        visible = show,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)),
        exit = fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .28f * (1f - progress)))
                .clickable(onClick = dismiss),
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(KixyuSpacing.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            val dialogHeight = minOf(maxHeight, 480.dp)
            KixyuGlassSurface(
                backdrop = backdrop,
                modifier = Modifier.widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .height(dialogHeight)
                    .kixyuPredictivePopupTransform(progress)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent()
                        }
                    },
                fallbackContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        "书籍信息",
                        modifier = Modifier.padding(
                            start = KixyuSpacing.extraLarge,
                            end = KixyuSpacing.extraLarge,
                            top = KixyuSpacing.extraLarge,
                            bottom = KixyuSpacing.medium,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = KixyuSpacing.extraLarge,
                                end = KixyuSpacing.extraLarge,
                                bottom = KixyuSpacing.medium,
                            ),
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                            ) {
                                Text(current.title, style = MaterialTheme.typography.titleLarge)
                                Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall)) {
                                    Text(
                                        "作者",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(current.author, style = MaterialTheme.typography.bodyLarge)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall)) {
                                    Text(
                                        "简介",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        current.description.ifBlank { "暂无简介" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (current.description.isBlank()) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(
                            start = KixyuSpacing.large,
                            end = KixyuSpacing.large,
                            bottom = KixyuSpacing.medium,
                        ),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        KixyuTextButton(text = "关闭", onClick = dismiss)
                    }
                }
            }
        }
    }
}

internal fun readerPalette(settings: ReaderSettings, systemDark: Boolean): ReaderRenderPalette {
    // Built-in night reading always uses true black for OLED panels. Custom reading themes
    // intentionally keep the exact background selected by the user below.
    val dark = ReaderRenderPalette(Color.Black, Color(0xFFD9D9D0), Color(0xFFF0F0E7), Color(0xFFB8CCBD), Color(0xFF92948B))
    val day = ReaderRenderPalette(Color(0xFFFAF8F2), Color(0xFF282620), Color(0xFF171713), Color(0xFF52655A), Color(0xFF716F67))
    val useNightColors = when (settings.theme) {
        ReaderTheme.SYSTEM -> systemDark
        ReaderTheme.DAY -> false
        ReaderTheme.NIGHT -> true
    }
    val default = if (useNightColors) dark else day
    if (!settings.customThemeEnabled) return default
    val custom = if (useNightColors) settings.customNightTheme else settings.customDayTheme
    return ReaderRenderPalette(
        custom.backgroundHex.colorOr(default.background),
        custom.bodyHex.colorOr(default.body),
        custom.titleHex.colorOr(default.title),
        custom.accentHex.colorOr(default.accent),
        custom.bodyHex.colorOr(default.secondary).copy(alpha = .62f),
    )
}

internal fun String.colorOr(fallback: Color) = runCatching { Color(toColorInt()) }.getOrDefault(fallback)

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Pagination inputs are almost always unique remainders. Caching their TextLayoutResult objects
// retains large native buffers without producing useful hits, especially for malformed EPUB text.
internal const val READER_TEXT_MEASURE_CACHE_SIZE = 0
internal const val PAGER_NAVIGATION_RADIUS = 10
internal const val CHAPTER_LOADING_INDICATOR_DELAY_MILLIS = 180L
internal const val READER_OVERLAY_SETTLE_MILLIS = 320L
internal const val SYSTEM_BAR_GESTURE_HIDE_MILLIS = 2_000L
internal const val READER_CONTROL_FALLBACK_ACCENT_MIX = .1f
internal const val MIN_ICON_CONTRAST = 3f
