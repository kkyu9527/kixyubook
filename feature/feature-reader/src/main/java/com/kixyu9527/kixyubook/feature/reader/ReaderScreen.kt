package com.kixyu9527.kixyubook.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color.parseColor
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageModeControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderThemeControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuStepperRow
import com.kixyu9527.kixyubook.core.reader.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.roundToInt

private enum class ReaderSheet { DIRECTORY, THEME, LAYOUT, SETTINGS, SEARCH }
private data class ReaderPageInfo(val current: Int, val total: Int)

@Composable
fun ReaderRoute(onExit: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose(viewModel::finishSession) }
    ReaderScreen(
        state = state,
        onExit = onExit,
        moveChapter = viewModel::moveChapter,
        jumpChapter = viewModel::jumpToChapter,
        jumpPosition = viewModel::jumpToPosition,
        savePosition = viewModel::savePosition,
        saveEdit = viewModel::saveTextEdit,
        updateSettings = viewModel::updateSettings,
        addBookmark = viewModel::addBookmark,
        deleteBookmark = viewModel::deleteBookmark,
        search = viewModel::search,
        selectSearchResult = viewModel::selectSearchResult,
        moveSearchResult = viewModel::moveSearchResult,
        clearSearch = viewModel::clearSearch,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    onExit: () -> Unit,
    moveChapter: (Int, Boolean) -> Unit,
    jumpChapter: (Int) -> Unit,
    jumpPosition: (Int, Int) -> Unit,
    savePosition: (Int, Boolean) -> Unit,
    saveEdit: (Int, String) -> Unit,
    updateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    addBookmark: () -> Unit,
    deleteBookmark: (String) -> Unit,
    search: (String) -> Unit,
    selectSearchResult: (Int) -> Unit,
    moveSearchResult: (Int) -> Unit,
    clearSearch: () -> Unit,
) {
    var controls by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var toolsMenu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var editing by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var pageInfo by remember { mutableStateOf<ReaderPageInfo?>(null) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    val volumeTurns = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val view = LocalView.current
    val modalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    DisposableEffect(state.settings.showStatusBar, view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (state.settings.showStatusBar) {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
    DisposableEffect(state.settings.keepScreenOn, view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.settings.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(state.settings.pageMode) {
        if (state.settings.pageMode == PageMode.SCROLL) pageInfo = null
    }
    PredictiveBackHandler(
        enabled = sheet == null && editing == null && (menu || toolsMenu || controls || state.searchResults.isNotEmpty()),
    ) { events ->
        try {
            events.collect { backProgress = it.progress }
            when {
                toolsMenu -> toolsMenu = false
                menu -> menu = false
                state.searchResults.isNotEmpty() -> clearSearch()
                else -> controls = false
            }
        } catch (_: CancellationException) { } finally { backProgress = 0f }
    }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = readerPalette(state.settings, systemDark)
    val currentPageBookmark = state.chapter?.let { chapter ->
        state.bookmarks.firstOrNull { bookmark ->
            bookmark.chapterId == chapter.id && bookmark.position == state.currentPosition
        }
    }
    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(palette.accent, palette.accent.copy(alpha = .32f))) {
        Box(
            Modifier.fillMaxSize()
                .background(palette.background)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    val isVolumeKey = event.key == Key.VolumeUp || event.key == Key.VolumeDown
                    if (!state.settings.volumeKeyPageTurn || !isVolumeKey) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        controls = false
                        menu = false
                        toolsMenu = false
                        volumeTurns.tryEmit(if (event.key == Key.VolumeUp) -1 else 1)
                    }
                    true
                }
                .focusable(),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.accent)
                state.error != null -> Text(state.error, color = palette.body, modifier = Modifier.align(Alignment.Center))
                state.chapter != null -> ReaderContent(
                    state = state,
                    palette = palette,
                    savePosition = savePosition,
                    moveChapter = moveChapter,
                    middleTap = { controls = !controls; if (!controls) { menu = false; toolsMenu = false } },
                    dismissControls = { controls = false; menu = false; toolsMenu = false },
                    edit = { index, text -> editing = index to text },
                    onPageInfo = { pageInfo = it },
                    volumeTurns = volumeTurns,
                )
            }
            ReaderPageControls(
                info = pageInfo.takeIf { state.settings.showPageNumber && state.searchResults.isEmpty() },
                showChapterActions = controls,
                palette = palette,
                hasPreviousChapter = state.chapterIndex > 0,
                hasNextChapter = state.chapterIndex < state.chapters.lastIndex,
                onPreviousChapter = { moveChapter(-1, false) },
                onNextChapter = { moveChapter(1, false) },
            )
            ReaderControls(
                visible = controls, menuVisible = menu, toolsMenuVisible = toolsMenu, progress = backProgress,
                bookTitle = state.book?.title.orEmpty().takeIf { state.searchResults.isEmpty() }.orEmpty(),
                currentPageBookmarked = currentPageBookmark != null,
                onExit = onExit, onDirectory = { sheet = ReaderSheet.DIRECTORY },
                onSettings = { menu = !menu; toolsMenu = false },
                onTools = { toolsMenu = !toolsMenu; menu = false },
                onToggleBookmark = {
                    currentPageBookmark?.let { deleteBookmark(it.uuid) } ?: addBookmark()
                    toolsMenu = false
                },
                onSearch = { sheet = ReaderSheet.SEARCH; toolsMenu = false },
                onSheet = { sheet = it },
            )
            SearchNavigator(
                modifier = Modifier.align(Alignment.BottomCenter),
                state = state,
                visible = state.searchResults.isNotEmpty() && sheet == null,
                onMove = moveSearchResult,
                onClose = clearSearch,
            )
        }
    }

    sheet?.let { active ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = modalSheetState,
            contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal) },
        ) {
            when (active) {
                ReaderSheet.DIRECTORY -> DirectorySheet(
                    state = state,
                    selectChapter = { index -> jumpChapter(index); sheet = null },
                    selectBookmark = { bookmark -> jumpPosition(bookmark.chapterIndex, bookmark.position); sheet = null },
                    deleteBookmark = deleteBookmark,
                )
                ReaderSheet.THEME -> ThemeSheet(state.settings, updateSettings)
                ReaderSheet.LAYOUT -> LayoutSheet(state, updateSettings)
                ReaderSheet.SETTINGS -> ReaderSettingsSheet(state.settings, updateSettings)
                ReaderSheet.SEARCH -> SearchSheet(
                    state = state,
                    onSearch = search,
                    onSelect = { index ->
                        selectSearchResult(index)
                        controls = false
                        menu = false
                        toolsMenu = false
                        sheet = null
                    },
                )
            }
        }
    }
    editing?.let { (index, original) -> EditParagraphDialog(original, { editing = null }, { saveEdit(index, it); editing = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchNavigator(
    modifier: Modifier = Modifier,
    state: ReaderUiState,
    visible: Boolean,
    onMove: (Int) -> Unit,
    onClose: () -> Unit,
) {
    if (visible) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(bottom = KixyuSpacing.small, start = KixyuSpacing.large, end = KixyuSpacing.large),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .height(KixyuSize.readerControlButton)
                    .widthIn(max = 280.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = KixyuSpacing.extraSmall,
            ) {
                Row(
                    Modifier.padding(start = KixyuSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${state.searchQuery}  ${state.selectedSearchIndex + 1}/${state.searchResults.size}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { onMove(-1) },
                        enabled = state.selectedSearchIndex > 0,
                    ) { Icon(Icons.Outlined.KeyboardArrowUp, "上一个结果") }
                    IconButton(
                        onClick = { onMove(1) },
                        enabled = state.selectedSearchIndex < state.searchResults.lastIndex,
                    ) { Icon(Icons.Outlined.KeyboardArrowDown, "下一个结果") }
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "退出搜索") }
                }
            }
        }
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun ReaderContent(
    state: ReaderUiState,
    palette: ReaderRenderPalette,
    savePosition: (Int, Boolean) -> Unit,
    moveChapter: (Int, Boolean) -> Unit,
    middleTap: () -> Unit,
    dismissControls: () -> Unit,
    edit: (Int, String) -> Unit,
    onPageInfo: (ReaderPageInfo?) -> Unit,
    volumeTurns: SharedFlow<Int>,
) {
    val chapter = state.chapter ?: return
    val density = LocalDensity.current
    val topInsetDp = with(density) { WindowInsets.safeDrawing.getTop(this).toDp().value }
    val bottomInsetDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp().value }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safeViewportHeight = (maxHeight.value - topInsetDp - bottomInsetDp).coerceAtLeast(1f)
        val spec = ReaderLayoutSpec(maxWidth.value, safeViewportHeight, state.settings.fontSize, state.settings.lineHeight, state.settings.letterSpacing, state.settings.margin)
        key(chapter.id, state.settings.pageMode, spec, state.navigationVersion) {
            if (state.settings.pageMode == PageMode.SCROLL) {
                LaunchedEffect(chapter.id) { onPageInfo(null) }
                val contentParagraphs = remember(chapter) { chapter.contentParagraphs() }
                val restoredItem = contentParagraphs.indexOfFirst { it.index >= state.restorePosition }
                    .let { if (it < 0) contentParagraphs.lastIndex else it }
                    .coerceAtLeast(0)
                val listState = rememberLazyListState(restoredItem + 1)
                LaunchedEffect(listState, chapter.id) {
                    snapshotFlow {
                        val hasVisibleItems = listState.layoutInfo.visibleItemsInfo.isNotEmpty()
                        val chapterComplete = hasVisibleItems && !listState.canScrollForward
                        val visibleItem = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                        val position = if (chapterComplete) {
                            contentParagraphs.lastOrNull()?.index ?: 0
                        } else {
                            contentParagraphs.getOrNull(visibleItem)?.index
                                ?: contentParagraphs.firstOrNull()?.index
                                ?: 0
                        }
                        position to chapterComplete
                    }.distinctUntilChanged().debounce(500).collect { (position, complete) ->
                        savePosition(position, complete)
                    }
                }
                LaunchedEffect(listState) {
                    snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
                        if (scrolling) dismissControls()
                    }
                }
                LaunchedEffect(listState, volumeTurns) {
                    volumeTurns.collect { direction ->
                        dismissControls()
                        when {
                            direction < 0 && !listState.canScrollBackward -> moveChapter(-1, true)
                            direction > 0 && !listState.canScrollForward -> moveChapter(1, false)
                            else -> {
                                val viewport = listState.layoutInfo.run { viewportEndOffset - viewportStartOffset }
                                listState.animateScrollBy(viewport * direction.toFloat())
                            }
                        }
                    }
                }
                ReaderScrollRenderer(
                    chapter, listState, spec, palette, state.fontPath, state.book?.isEditable == true, edit,
                    { fraction -> if (fraction in .33f..67f) middleTap() else dismissControls() },
                    { dismissControls(); moveChapter(-1, true) }, { dismissControls(); moveChapter(1, false) },
                    state.chapterIndex > 0, state.chapterIndex < state.chapters.lastIndex,
                    topInsetDp, bottomInsetDp,
                    Modifier.fillMaxSize(),
                    highlightQuery = state.searchQuery,
                )
            } else {
                Box(
                    Modifier.fillMaxSize().padding(top = topInsetDp.dp, bottom = bottomInsetDp.dp),
                ) {
                    PagedReader(
                        state, chapter, spec, palette, savePosition, moveChapter,
                        middleTap, dismissControls, edit, onPageInfo,
                        volumeTurns,
                    )
                }
            }
        }
    }
}

@Composable
private fun PagedReader(
    state: ReaderUiState,
    chapter: ReaderChapter,
    spec: ReaderLayoutSpec,
    palette: ReaderRenderPalette,
    savePosition: (Int, Boolean) -> Unit,
    moveChapter: (Int, Boolean) -> Unit,
    middleTap: () -> Unit,
    dismissControls: () -> Unit,
    edit: (Int, String) -> Unit,
    onPageInfo: (ReaderPageInfo?) -> Unit,
    volumeTurns: SharedFlow<Int>,
) {
    val pages = rememberMeasuredReaderPages(
        chapter = chapter,
        spec = spec,
        fontPath = state.fontPath,
        showRegularChapterTitle = state.settings.showChapterTitle,
    )
    val positions = remember { ReaderPositionManager() }
    val hasPrevious = state.chapterIndex > 0; val hasNext = state.chapterIndex < state.chapters.lastIndex
    val leading = if (hasPrevious) 1 else 0
    val virtualCount = pages.size + leading + if (hasNext) 1 else 0
    val initial = positions.pageFor(pages, state.restorePosition) + leading
    val pager = rememberPagerState(initialPage = initial.coerceIn(0, virtualCount - 1), pageCount = { virtualCount })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, chapter.id) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            when {
                hasPrevious && page == 0 -> moveChapter(-1, true)
                hasNext && page == virtualCount - 1 -> moveChapter(1, false)
                else -> {
                    val actualPage = page - leading
                    savePosition(
                        pages[actualPage].startParagraph,
                        actualPage == pages.lastIndex,
                    )
                }
            }
        }
    }
    LaunchedEffect(pager, chapter.id, pages.size, leading) {
        snapshotFlow { pager.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val actualPage = (page - leading).coerceIn(pages.indices)
                onPageInfo(ReaderPageInfo(actualPage + 1, pages.size))
            }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (scrolling) dismissControls()
        }
    }
    LaunchedEffect(pager, volumeTurns) {
        volumeTurns.collect { direction ->
            dismissControls()
            val target = (pager.currentPage + direction).coerceIn(0, virtualCount - 1)
            if (target != pager.currentPage) pager.animateScrollToPage(target)
        }
    }
    HorizontalPager(pager, Modifier.fillMaxSize()) { virtualPage ->
        val actual = virtualPage - leading
        if (actual !in pages.indices) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (actual < 0) "上一章" else "下一章", color = palette.secondary) }
        else ReaderPageRenderer(
            pages[actual], spec, palette, state.fontPath, state.book?.isEditable == true, edit,
            { fraction -> scope.launch {
                when {
                    fraction < .33f && pager.currentPage > 0 -> {
                        dismissControls(); pager.animateScrollToPage(pager.currentPage - 1)
                    }
                    fraction > .67f && pager.currentPage < virtualCount - 1 -> {
                        dismissControls(); pager.animateScrollToPage(pager.currentPage + 1)
                    }
                    else -> middleTap()
                }
            } },
            showRegularChapterTitle = state.settings.showChapterTitle,
            highlightQuery = state.searchQuery,
        )
    }
}

@Composable
private fun ReaderControls(
    visible: Boolean,
    menuVisible: Boolean,
    toolsMenuVisible: Boolean,
    progress: Float,
    bookTitle: String,
    currentPageBookmarked: Boolean,
    onExit: () -> Unit,
    onDirectory: () -> Unit,
    onSettings: () -> Unit,
    onTools: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSearch: () -> Unit,
    onSheet: (ReaderSheet) -> Unit,
) {
    val popupVisible = menuVisible || toolsMenuVisible
    val controlsBackModifier = if (popupVisible) Modifier else Modifier.predictivePopupTransform(progress)
    val menuBackModifier = if (popupVisible) Modifier.predictivePopupTransform(progress) else Modifier
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn() + scaleIn(initialScale = .9f),
        exit = fadeOut() + scaleOut(targetScale = .9f),
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (bookTitle.isNotBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(top = KixyuSize.readerTopControlInset)
                        .height(KixyuSize.readerControlButton)
                        .widthIn(max = KixyuSize.readerBookTitleMaxWidth)
                        .then(controlsBackModifier),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = KixyuSpacing.extraSmall,
                ) {
                    Box(
                        Modifier.fillMaxHeight().padding(horizontal = KixyuSpacing.small),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            bookTitle,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            FilledTonalIconButton(
                onExit,
                Modifier.align(Alignment.TopEnd)
                    .padding(top = KixyuSize.readerTopControlInset, end = KixyuSize.readerControlInset)
                    .size(KixyuSize.readerControlButton)
                    .then(controlsBackModifier),
            ) { Icon(Icons.Outlined.Close, "退出") }
            Box(
                Modifier.align(Alignment.TopStart)
                    .padding(top = KixyuSize.readerTopControlInset, start = KixyuSize.readerControlInset),
            ) {
                FilledTonalIconButton(
                    onClick = onTools,
                    modifier = Modifier.size(KixyuSize.readerControlButton).then(controlsBackModifier),
                ) { Icon(Icons.Outlined.MoreHoriz, "阅读工具") }
                DropdownMenu(
                    expanded = toolsMenuVisible,
                    onDismissRequest = onTools,
                    offset = DpOffset(0.dp, KixyuSpacing.small),
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (currentPageBookmarked) "移除当前页书签" else "添加当前页书签",
                                maxLines = 1,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (currentPageBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                                null,
                            )
                        },
                        onClick = onToggleBookmark,
                    )
                    DropdownMenuItem(
                        text = { Text("全文搜索", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        onClick = onSearch,
                    )
                }
            }
            FilledTonalIconButton(onDirectory, Modifier.align(Alignment.BottomStart).padding(KixyuSize.readerControlInset).then(controlsBackModifier)) { Icon(Icons.AutoMirrored.Outlined.Toc, "目录") }
            FilledTonalIconButton(onSettings, Modifier.align(Alignment.BottomEnd).padding(KixyuSize.readerControlInset).then(controlsBackModifier)) { Icon(Icons.Outlined.Settings, "设置") }
            AnimatedVisibility(
                visible = menuVisible,
                modifier = Modifier.align(Alignment.BottomEnd).padding(
                    end = KixyuSpacing.large,
                    bottom = KixyuSize.readerMenuBottomOffset,
                ),
                enter = fadeIn() + scaleIn(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
                exit = fadeOut() + scaleOut(),
            ) {
                Surface(modifier = menuBackModifier, shape = MaterialTheme.shapes.large, tonalElevation = KixyuSpacing.small, shadowElevation = KixyuSpacing.small) { Column(Modifier.padding(vertical = KixyuSpacing.extraSmall)) {
                    MenuRow(Icons.Outlined.Palette, "主题") { onSheet(ReaderSheet.THEME) }
                    MenuRow(Icons.Outlined.ViewCarousel, "页面布局") { onSheet(ReaderSheet.LAYOUT) }
                    MenuRow(Icons.Outlined.Tune, "阅读设置") { onSheet(ReaderSheet.SETTINGS) }
                } }
            }
        }
    }
}

@Composable
private fun ReaderPageControls(
    info: ReaderPageInfo?,
    showChapterActions: Boolean,
    palette: ReaderRenderPalette,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
) {
    if (info == null && !showChapterActions) return
    if (info != null && !showChapterActions) {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = KixyuSpacing.hairline),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                "${info.current}/${info.total}",
                color = palette.secondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        AnimatedVisibility(
            visible = showChapterActions,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = KixyuSize.readerControlInset),
            enter = fadeIn() + scaleIn(initialScale = .9f),
            exit = fadeOut() + scaleOut(targetScale = .9f),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = KixyuSpacing.extraSmall,
            ) {
                Row(
                    modifier = Modifier.height(KixyuSize.readerControlButton),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPreviousChapter, enabled = hasPreviousChapter) {
                        Icon(Icons.Outlined.SkipPrevious, "上一章")
                    }
                    Spacer(Modifier.width(KixyuSize.readerChapterActionGap))
                    IconButton(onClick = onNextChapter, enabled = hasNextChapter) {
                        Icon(Icons.Outlined.SkipNext, "下一章")
                    }
                }
            }
        }
    }
}

private fun Modifier.predictivePopupTransform(progress: Float): Modifier = graphicsLayer {
    alpha = 1f - progress
    scaleX = 1f - progress * .08f
    scaleY = scaleX
}

@Composable private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, click: () -> Unit) {
    Row(
        Modifier.pointerInput(Unit) { detectTapGestures { click() } }
            .padding(horizontal = KixyuSpacing.large, vertical = KixyuSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
    ) { Icon(icon, null, Modifier.size(KixyuSize.icon)); Text(text, maxLines = 1) }
}

private enum class DirectoryView { CHAPTERS, BOOKMARKS }

@Composable
private fun DirectorySheet(
    state: ReaderUiState,
    selectChapter: (Int) -> Unit,
    selectBookmark: (Bookmark) -> Unit,
    deleteBookmark: (String) -> Unit,
) {
    var directoryView by rememberSaveable { mutableStateOf(DirectoryView.CHAPTERS) }
    val bookmarkedChapterIds = remember(state.bookmarks) { state.bookmarks.mapTo(mutableSetOf(), Bookmark::chapterId) }
    val currentIndex = state.chapterIndex.coerceIn(0, state.chapters.lastIndex.coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    LaunchedEffect(currentIndex, state.chapters.size) {
        if (state.chapters.isNotEmpty()) listState.scrollToItem(currentIndex)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = KixyuSpacing.large, end = KixyuSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (directoryView == DirectoryView.CHAPTERS) "目录 · ${state.chapters.size} 章" else "书签 · ${state.bookmarks.size}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            IconButton(onClick = {
                directoryView = if (directoryView == DirectoryView.CHAPTERS) DirectoryView.BOOKMARKS else DirectoryView.CHAPTERS
            }) {
                Icon(
                    if (directoryView == DirectoryView.CHAPTERS) Icons.Outlined.Bookmarks else Icons.AutoMirrored.Outlined.Toc,
                    if (directoryView == DirectoryView.CHAPTERS) "查看书签" else "查看目录",
                )
            }
        }
        AnimatedContent(
            targetState = directoryView,
            transitionSpec = {
                if (targetState == DirectoryView.BOOKMARKS) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "directoryBookmarks",
        ) { view ->
            if (view == DirectoryView.CHAPTERS) {
                Box(Modifier.fillMaxWidth().heightIn(max = KixyuSize.readerSheetMaxContent)) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(
                            end = if (state.chapters.size >= FAST_SCROLLER_MIN_CHAPTERS) {
                                KixyuSize.directoryFastScrollerWidth
                            } else 0.dp,
                        ),
                        state = listState,
                    ) {
                        items(state.chapters.size) { index ->
                            val current = index == state.chapterIndex
                            val hasBookmark = state.chapters[index].id in bookmarkedChapterIds
                            ListItem(
                                headlineContent = { Text(state.chapters[index].title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                leadingContent = { if (current) Icon(Icons.Outlined.PlayArrow, null, Modifier.size(KixyuSize.icon), tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = {
                                    if (hasBookmark) {
                                        Icon(
                                            Icons.Filled.Bookmark,
                                            "本章有书签",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = when {
                                        current -> MaterialTheme.colorScheme.secondaryContainer
                                        hasBookmark -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        else -> Color.Transparent
                                    },
                                ),
                                modifier = Modifier.pointerInput(index) { detectTapGestures { selectChapter(index) } },
                            )
                        }
                        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                    }
                    if (state.chapters.size >= FAST_SCROLLER_MIN_CHAPTERS) {
                        DirectoryFastScroller(
                            itemCount = state.chapters.size,
                            listState = listState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
            } else if (state.bookmarks.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("还没有书签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = KixyuSize.readerSheetMaxContent),
                ) {
                    items(state.bookmarks, key = Bookmark::uuid) { bookmark ->
                        ListItem(
                            headlineContent = { Text(bookmark.chapterTitle, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    bookmark.preview.ifBlank { "第 ${bookmark.position + 1} 段" },
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { Icon(Icons.Outlined.Bookmark, null) },
                            trailingContent = {
                                IconButton(onClick = { deleteBookmark(bookmark.uuid) }) {
                                    Icon(Icons.Outlined.DeleteOutline, "删除书签")
                                }
                            },
                            modifier = Modifier.pointerInput(bookmark.uuid) { detectTapGestures { selectBookmark(bookmark) } },
                        )
                    }
                    item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                }
            }
        }
    }
}

private const val FAST_SCROLLER_MIN_CHAPTERS = 30

@Composable
private fun DirectoryFastScroller(
    itemCount: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbHeightPx = with(density) { KixyuSize.directoryFastScrollerThumbHeight.toPx() }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val listFraction by remember(itemCount, listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex.toFloat() / (itemCount - 1).coerceAtLeast(1)
        }
    }

    fun scrollToFraction(value: Float) {
        dragFraction = value.coerceIn(0f, 1f)
        val target = ((itemCount - 1) * dragFraction).roundToInt()
        scrollJob?.cancel()
        scrollJob = scope.launch { listState.scrollToItem(target) }
    }

    val visibleFraction = if (dragging) dragFraction else listFraction
    val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    Box(
        modifier.width(KixyuSize.directoryFastScrollerWidth)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(itemCount, trackHeightPx) {
                detectTapGestures { point ->
                    val travel = (size.height - thumbHeightPx).coerceAtLeast(1f)
                    scrollToFraction((point.y - thumbHeightPx / 2f) / travel)
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.align(Alignment.Center)
                .fillMaxHeight()
                .width(KixyuSize.directoryFastScrollerTrackWidth)
                .background(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge),
        )
        Surface(
            modifier = Modifier
                .offset { IntOffset(0, (travelPx * visibleFraction).roundToInt()) }
                .size(
                    KixyuSize.directoryFastScrollerThumbWidth,
                    KixyuSize.directoryFastScrollerThumbHeight,
                )
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val travel = travelPx.coerceAtLeast(1f)
                        scrollToFraction(dragFraction + delta / travel)
                    },
                    onDragStarted = {
                        dragging = true
                        dragFraction = listFraction
                    },
                    onDragStopped = { dragging = false },
                ),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = KixyuSpacing.extraSmall,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DragHandle, "快速滚动目录", Modifier.size(KixyuSize.icon))
            }
        }
    }
}

@Composable
private fun SearchSheet(
    state: ReaderUiState,
    onSearch: (String) -> Unit,
    onSelect: (Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(state.searchQuery) }
    val focusManager = LocalFocusManager.current
    fun submit() {
        onSearch(query)
        focusManager.clearFocus()
    }
    Column(
        Modifier.fillMaxWidth().imePadding(),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
    ) {
        Text(
            "全文搜索",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = KixyuSpacing.large),
            maxLines = 1,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = KixyuSpacing.large),
            placeholder = { Text("搜索书中内容", maxLines = 1) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                IconButton(onClick = ::submit, enabled = query.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "搜索")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )
        when {
            state.searchResults.isNotEmpty() -> {
                Text(
                    "${state.searchResults.size} 个匹配结果",
                    modifier = Modifier.padding(horizontal = KixyuSpacing.large),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 480.dp),
                ) {
                    items(state.searchResults.size) { index ->
                        val result = state.searchResults[index]
                        ListItem(
                            headlineContent = {
                                Text(
                                    result.chapterTitle,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    result.text,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { Text("${index + 1}", style = MaterialTheme.typography.labelMedium) },
                            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                            colors = ListItemDefaults.colors(
                                containerColor = if (index == state.selectedSearchIndex) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else Color.Transparent,
                            ),
                            modifier = Modifier.pointerInput(index) { detectTapGestures { onSelect(index) } },
                        )
                    }
                }
            }
            state.searchQuery.isNotBlank() -> {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text("没有找到匹配内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable private fun ThemeSheet(settings: ReaderSettings, update: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(horizontal = KixyuSpacing.large),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { Text("主题", style = MaterialTheme.typography.titleLarge, maxLines = 1) }
        item {
            KixyuSection {
                KixyuReaderThemeControls(settings, { updated -> update { updated } }, modeTitle = "显示模式")
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable private fun LayoutSheet(state: ReaderUiState, update: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    val settings = state.settings
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = KixyuSpacing.large),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { Text("页面布局", style = MaterialTheme.typography.titleLarge, maxLines = 1) }
        item {
            KixyuSection(title = "字体") {
                KixyuSettingsRow("系统默认", onClick = { update { it.copy(fontUuid = null) } }) {
                    RadioButton(settings.fontUuid == null, null)
                }
                state.availableFonts.forEach { font ->
                    KixyuDivider()
                    KixyuSettingsRow(font.name, onClick = { update { it.copy(fontUuid = font.uuid) } }) {
                        RadioButton(settings.fontUuid == font.uuid, null)
                    }
                }
            }
        }
        item {
            ReaderSettingStepper("字号", settings.fontSize, .5f, 15f..30f, "sp") { value ->
                update { it.copy(fontSize = value) }
            }
        }
        item { KixyuSection { KixyuPageModeControl(settings) { updated -> update { updated } } } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                ReaderSettingStepper("行间距", settings.lineHeight, .1f, 1.2f..2.2f) { value -> update { it.copy(lineHeight = value) } }
                ReaderSettingStepper("字间距", settings.letterSpacing, .1f, 0f..0.2f, "em") { value -> update { it.copy(letterSpacing = value) } }
                ReaderSettingStepper("页边距", settings.margin, .1f, 12f..52f, "dp") { value -> update { it.copy(margin = value) } }
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    update: ((ReaderSettings) -> ReaderSettings) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = KixyuSpacing.large),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { Text("阅读设置", style = MaterialTheme.typography.titleLarge, maxLines = 1) }
        item {
            KixyuSection {
                ReaderSwitchRow(
                    title = "显示状态栏",
                    supportingText = "阅读时显示时间和系统状态",
                    checked = settings.showStatusBar,
                ) { enabled -> update { it.copy(showStatusBar = enabled) } }
                KixyuDivider()
                ReaderSwitchRow(
                    title = "显示页码",
                    supportingText = "翻页模式底部显示当前页/总页数",
                    checked = settings.showPageNumber,
                ) { enabled -> update { it.copy(showPageNumber = enabled) } }
                KixyuDivider()
                ReaderSwitchRow(
                    title = "显示章节名",
                    supportingText = "非章节首页顶部显示当前章节名",
                    checked = settings.showChapterTitle,
                ) { enabled -> update { it.copy(showChapterTitle = enabled) } }
                KixyuDivider()
                ReaderSwitchRow(
                    title = "音量键翻页",
                    supportingText = "音量加键上一页，音量减键下一页",
                    checked = settings.volumeKeyPageTurn,
                ) { enabled -> update { it.copy(volumeKeyPageTurn = enabled) } }
                KixyuDivider()
                ReaderSwitchRow(
                    title = "保持屏幕常亮",
                    supportingText = "阅读期间不自动熄屏",
                    checked = settings.keepScreenOn,
                ) { enabled -> update { it.copy(keepScreenOn = enabled) } }
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun ReaderSwitchRow(
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
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ReaderSettingStepper(
    title: String,
    value: Float,
    step: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    update: (Float) -> Unit,
) {
    val label = String.format(
        java.util.Locale.getDefault(),
        "%.1f%s",
        value,
        if (suffix.isEmpty()) "" else " $suffix",
    )
    KixyuStepperRow(
        title = title,
        valueLabel = label,
        onDecrease = { update(value.stepped(-1, step, range)) },
        onIncrease = { update(value.stepped(1, step, range)) },
        decreaseEnabled = value > range.start,
        increaseEnabled = value < range.endInclusive,
    )
}

private fun Float.stepped(
    direction: Int,
    step: Float,
    range: ClosedFloatingPointRange<Float>,
): Float {
    val tick = (this / step).roundToInt() + direction
    return ((tick * step * 1_000f).roundToInt() / 1_000f).coerceIn(range)
}

@Composable private fun EditParagraphDialog(original: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var text by remember { mutableStateOf(original) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("编辑 TXT 正文", maxLines = 1) },
        text = {
            Column {
                Text(
                    "修改会直接写入已导入的 TXT 原始文件，并重新解析当前书籍。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    text,
                    { text = it },
                    Modifier.fillMaxWidth().padding(top = KixyuSpacing.medium),
                    minLines = 5,
                )
            }
        },
        confirmButton = { TextButton({ save(text) }, enabled = text != original && text.isNotBlank()) { Text("保存修改") } },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}

private fun readerPalette(settings: ReaderSettings, systemDark: Boolean): ReaderRenderPalette {
    val dark = ReaderRenderPalette(Color(0xFF11120F), Color(0xFFD9D9D0), Color(0xFFF0F0E7), Color(0xFFB8CCBD), Color(0xFF92948B))
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

private fun String.colorOr(fallback: Color) = runCatching { Color(parseColor(this)) }.getOrDefault(fallback)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
