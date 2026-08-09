package com.kixyu9527.kixyubook.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.graphics.toColorInt
import android.view.WindowManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowSizeClass
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppColorControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppUiStyleControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomSheet
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuFontControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuInteractivePopupSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuListRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuOverlayHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenu
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupMenuItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderBehaviorControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderLayoutControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderThemeControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSearchField
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTonalIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPopupSpring
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import com.kixyu9527.kixyubook.core.reader.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderSearchOverlay(
    visible: Boolean,
    progress: Float,
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onMove: (Int) -> Unit,
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
                    .predictivePopupTransform(progress),
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
                                Icon(Icons.Outlined.Close, "关闭搜索")
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
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            KixyuIconButton(onClick = ::submit, enabled = query.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, "搜索")
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
                        ) { Icon(Icons.Outlined.KeyboardArrowUp, "上一个结果") }
                        KixyuIconButton(
                            onClick = { onMove(1) },
                            enabled = state.selectedSearchIndex < state.searchResults.lastIndex,
                        ) { Icon(Icons.Outlined.KeyboardArrowDown, "下一个结果") }
                        KixyuIconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, "退出搜索")
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
                            trailing = { Icon(Icons.Outlined.ChevronRight, null) },
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

@Composable internal fun ThemeSheet(settings: ReaderSettings, update: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = KixyuSpacing.large),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { Text("阅读配色", style = MaterialTheme.typography.titleLarge, maxLines = 1) }
        item {
            KixyuSection(title = "应用界面") {
                KixyuAppUiStyleControl(settings) { updated -> update { updated } }
                KixyuDivider()
                KixyuAppColorControl(settings) { updated -> update { updated } }
            }
        }
        item {
            KixyuSection(title = "阅读配色") {
                KixyuReaderThemeControls(settings, { updated -> update { updated } }, modeTitle = "显示模式")
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
internal fun LayoutSheet(
    state: ReaderUiState,
    update: ((ReaderSettings) -> ReaderSettings) -> Unit,
    addFont: () -> Unit,
    deleteFont: (UserFont) -> Unit,
) {
    val settings = state.settings
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = KixyuSpacing.large),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { Text("排版与翻页", style = MaterialTheme.typography.titleLarge, maxLines = 1) }
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
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
internal fun ReaderSettingsSheet(
    settings: ReaderSettings,
    update: ((ReaderSettings) -> ReaderSettings) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = KixyuSpacing.large),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { Text("阅读行为", style = MaterialTheme.typography.titleLarge, maxLines = 1) }
        item {
            KixyuSection(title = "阅读行为") {
                KixyuReaderBehaviorControls(settings) { updated -> update { updated } }
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable internal fun BookInfoDialog(
    show: Boolean,
    book: Book?,
    dismiss: () -> Unit,
) {
    val current = book ?: return
    KixyuActionDialog(
        show = show,
        onDismissRequest = dismiss,
        title = "书籍信息",
        confirmLabel = "关闭",
        onConfirm = dismiss,
        dismissLabel = null,
    ) {
        // This dialog intentionally owns its scroll state. A fixed-height Column only reports
        // that fixed height to the parent even when Text draws beyond it, which made the overflow
        // visible but unreachable. Keeping the viewport fixed and scrolling its measured content
        // gives empty and long descriptions the same dialog size on phones and tablets.
        Column(
            modifier = Modifier.fillMaxWidth()
                .height(300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                ) {
                    Text(
                        current.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall)) {
                        Text(
                            "作者",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            current.author,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
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
internal const val READER_CONTROL_ACCENT_MIX = .18f
internal const val READER_CONTROL_DISABLED_ACCENT_MIX = .1f
internal const val MIN_ICON_CONTRAST = 3f
