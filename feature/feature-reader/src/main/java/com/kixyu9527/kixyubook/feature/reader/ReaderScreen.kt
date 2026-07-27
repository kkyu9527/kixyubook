package com.kixyu9527.kixyubook.feature.reader

import android.graphics.Color.parseColor
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.roundToInt

private enum class ReaderSheet { DIRECTORY, FONT, THEME, LAYOUT }
private data class ReaderPageInfo(val current: Int, val total: Int)

@Composable
fun ReaderRoute(onExit: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose(viewModel::finishSession) }
    ReaderScreen(state, onExit, viewModel::moveChapter, viewModel::jumpToChapter, viewModel::savePosition, viewModel::saveTextEdit, viewModel::updateSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    onExit: () -> Unit,
    moveChapter: (Int, Boolean) -> Unit,
    jumpChapter: (Int) -> Unit,
    savePosition: (Int, Boolean) -> Unit,
    saveEdit: (Int, String) -> Unit,
    updateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
) {
    var controls by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var editing by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var pageInfo by remember { mutableStateOf<ReaderPageInfo?>(null) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    val modalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(state.settings.pageMode) {
        if (state.settings.pageMode == PageMode.SCROLL) pageInfo = null
    }
    PredictiveBackHandler(enabled = sheet == null && editing == null && (menu || controls)) { events ->
        try {
            events.collect { backProgress = it.progress }
            if (menu) menu = false else controls = false
        } catch (_: CancellationException) { } finally { backProgress = 0f }
    }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = readerPalette(state.settings, systemDark)
    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(palette.accent, palette.accent.copy(alpha = .32f))) {
        Box(Modifier.fillMaxSize().background(palette.background)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.accent)
                state.error != null -> Text(state.error, color = palette.body, modifier = Modifier.align(Alignment.Center))
                state.chapter != null -> ReaderContent(
                    state = state,
                    palette = palette,
                    savePosition = savePosition,
                    moveChapter = moveChapter,
                    middleTap = { controls = !controls; if (!controls) menu = false },
                    dismissControls = { controls = false; menu = false },
                    edit = { index, text -> editing = index to text },
                    onPageInfo = { pageInfo = it },
                )
            }
            ReaderPageControls(
                info = pageInfo,
                showChapterActions = controls,
                palette = palette,
                hasPreviousChapter = state.chapterIndex > 0,
                hasNextChapter = state.chapterIndex < state.chapters.lastIndex,
                onPreviousChapter = { moveChapter(-1, false) },
                onNextChapter = { moveChapter(1, false) },
            )
            ReaderControls(
                visible = controls, menuVisible = menu, progress = backProgress,
                bookTitle = state.book?.title.orEmpty(),
                onExit = onExit, onDirectory = { sheet = ReaderSheet.DIRECTORY },
                onSettings = { menu = !menu }, onSheet = { sheet = it },
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
                ReaderSheet.DIRECTORY -> DirectorySheet(state, { index -> jumpChapter(index); sheet = null })
                ReaderSheet.FONT -> FontSheet(state, updateSettings)
                ReaderSheet.THEME -> ThemeSheet(state.settings, updateSettings)
                ReaderSheet.LAYOUT -> LayoutSheet(state.settings, updateSettings)
            }
        }
    }
    editing?.let { (index, original) -> EditParagraphDialog(original, { editing = null }, { saveEdit(index, it); editing = null }) }
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
) {
    val chapter = state.chapter ?: return
    val density = LocalDensity.current
    val topInsetDp = with(density) { WindowInsets.safeDrawing.getTop(this).toDp().value }
    val bottomInsetDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp().value }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safeViewportHeight = (maxHeight.value - topInsetDp - bottomInsetDp).coerceAtLeast(1f)
        val spec = ReaderLayoutSpec(maxWidth.value, safeViewportHeight, state.settings.fontSize, state.settings.lineHeight, state.settings.letterSpacing, state.settings.margin)
        key(chapter.id, state.settings.pageMode, spec) {
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
                ReaderScrollRenderer(
                    chapter, listState, spec, palette, state.fontPath, state.book?.isEditable == true, edit,
                    { fraction -> if (fraction in .33f..67f) middleTap() else dismissControls() },
                    { dismissControls(); moveChapter(-1, true) }, { dismissControls(); moveChapter(1, false) },
                    state.chapterIndex > 0, state.chapterIndex < state.chapters.lastIndex,
                    topInsetDp, bottomInsetDp,
                    Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().padding(top = topInsetDp.dp, bottom = bottomInsetDp.dp),
                ) {
                    PagedReader(
                        state, chapter, spec, palette, savePosition, moveChapter,
                        middleTap, dismissControls, edit, onPageInfo,
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
) {
    val pages = rememberMeasuredReaderPages(chapter, spec, state.fontPath)
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
        )
    }
}

@Composable
private fun ReaderControls(
    visible: Boolean,
    menuVisible: Boolean,
    progress: Float,
    bookTitle: String,
    onExit: () -> Unit,
    onDirectory: () -> Unit,
    onSettings: () -> Unit,
    onSheet: (ReaderSheet) -> Unit,
) {
    val controlsBackModifier = if (menuVisible) Modifier else Modifier.predictivePopupTransform(progress)
    val menuBackModifier = if (menuVisible) Modifier.predictivePopupTransform(progress) else Modifier
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
                        .widthIn(max = KixyuSize.readerBookTitleMaxWidth)
                        .then(controlsBackModifier),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = KixyuSpacing.extraSmall,
                ) {
                    Text(
                        bookTitle,
                        modifier = Modifier.padding(
                            horizontal = KixyuSpacing.large,
                            vertical = KixyuSpacing.small,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            FilledTonalIconButton(
                onExit,
                Modifier.align(Alignment.TopEnd)
                    .padding(top = KixyuSize.readerTopControlInset, end = KixyuSize.readerControlInset)
                    .then(controlsBackModifier),
            ) { Icon(Icons.Outlined.Close, "退出") }
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
                    MenuRow(Icons.Outlined.FontDownload, "字体") { onSheet(ReaderSheet.FONT) }
                    MenuRow(Icons.Outlined.Palette, "主题") { onSheet(ReaderSheet.THEME) }
                    MenuRow(Icons.Outlined.ViewCarousel, "页面布局") { onSheet(ReaderSheet.LAYOUT) }
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
                    Spacer(Modifier.width(KixyuSize.readerPageIndicatorWidth))
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

@Composable private fun DirectorySheet(state: ReaderUiState, select: (Int) -> Unit) {
    val currentIndex = state.chapterIndex.coerceIn(0, state.chapters.lastIndex.coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    LaunchedEffect(currentIndex, state.chapters.size) {
        if (state.chapters.isNotEmpty()) listState.scrollToItem(currentIndex)
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "目录 · ${state.chapters.size} 章",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = KixyuSpacing.large, vertical = KixyuSpacing.medium),
            maxLines = 1,
        )
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
                    ListItem(
                        headlineContent = { Text(state.chapters[index].title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        leadingContent = { if (current) Icon(Icons.Outlined.PlayArrow, null, Modifier.size(KixyuSize.icon), tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                        modifier = Modifier.pointerInput(index) { detectTapGestures { select(index) } },
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

@Composable private fun FontSheet(state: ReaderUiState, update: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(horizontal = KixyuSpacing.large),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { Text("字体", style = MaterialTheme.typography.titleLarge, maxLines = 1) }
        item {
            KixyuSection {
                KixyuSettingsRow("系统默认", onClick = { update { it.copy(fontUuid = null) } }) {
                    RadioButton(state.settings.fontUuid == null, null)
                }
                state.availableFonts.forEach { font ->
                    KixyuDivider()
                    KixyuSettingsRow(font.name, onClick = { update { it.copy(fontUuid = font.uuid) } }) {
                        RadioButton(state.settings.fontUuid == font.uuid, null)
                    }
                }
            }
        }
        item {
            ReaderSettingStepper("字号", state.settings.fontSize, .5f, 15f..30f, "sp") { value ->
                update { it.copy(fontSize = value) }
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
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

@Composable private fun LayoutSheet(settings: ReaderSettings, update: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = KixyuSpacing.large),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { Text("页面布局", style = MaterialTheme.typography.titleLarge, maxLines = 1) }
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
