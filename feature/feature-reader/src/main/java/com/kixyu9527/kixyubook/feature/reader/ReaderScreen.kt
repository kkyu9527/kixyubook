package com.kixyu9527.kixyubook.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color.parseColor
import android.view.WindowManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppColorControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppUiStyleControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomSheet
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuFontControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
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
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTonalIconButton
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import com.kixyu9527.kixyubook.core.reader.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.roundToInt

private enum class ReaderSheet { DIRECTORY, THEME, LAYOUT, SETTINGS }

/** All floating reader controls share one enter/exit clock and transform. */
@Composable
private fun ReaderControlVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)) +
            scaleIn(tween(KixyuMotion.ReaderPopupEnterMillis), initialScale = .9f),
        exit = fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)) +
            scaleOut(tween(KixyuMotion.ReaderPopupExitMillis), targetScale = .9f),
        content = content,
    )
}

@Composable
fun ReaderRoute(
    onExit: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var readerResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var readerContentReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(READER_ENTRY_CONTENT_DELAY_MILLIS)
        readerContentReady = true
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFont(it.toString()) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> readerResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> readerResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(readerResumed, state.loading, state.chapter) {
        viewModel.setReadingActive(readerResumed && !state.loading && state.chapter != null)
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.setReadingActive(false)
            viewModel.finishSession()
        }
    }
    ReaderScreen(
        state = state,
        readerContentReady = readerContentReady,
        onExit = onExit,
        moveChapter = viewModel::moveChapter,
        moveChapterFromPage = viewModel::moveChapterFromPage,
        jumpChapter = viewModel::jumpToChapter,
        jumpPosition = viewModel::jumpToPosition,
        savePosition = viewModel::savePosition,
        updateSettings = viewModel::updateSettings,
        addBookmark = viewModel::addBookmark,
        deleteBookmark = viewModel::deleteBookmark,
        search = viewModel::search,
        selectSearchResult = viewModel::selectSearchResult,
        moveSearchResult = viewModel::moveSearchResult,
        clearSearch = viewModel::clearSearch,
        chapterRendered = viewModel::chapterRendered,
        addFont = {
            fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
        },
        deleteFont = viewModel::deleteFont,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    readerContentReady: Boolean,
    onExit: () -> Unit,
    moveChapter: (Int, Boolean) -> Unit,
    moveChapterFromPage: (Int, Int, Boolean) -> Unit,
    jumpChapter: (Int) -> Unit,
    jumpPosition: (Int, Int) -> Unit,
    savePosition: (Int, Boolean) -> Unit,
    updateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    addBookmark: () -> Unit,
    deleteBookmark: (String) -> Unit,
    search: (String) -> Unit,
    selectSearchResult: (Int) -> Unit,
    moveSearchResult: (Int) -> Unit,
    clearSearch: () -> Unit,
    chapterRendered: (Int) -> Unit,
    addFont: () -> Unit,
    deleteFont: (UserFont) -> Unit,
) {
    var controls by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var toolsMenu by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var bookInfoVisible by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    val volumeTurns = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var exitRequested by remember { mutableStateOf(false) }
    var retainedSheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var showChapterLoading by remember { mutableStateOf(false) }
    val isMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = readerPalette(state.settings, systemDark)
    LaunchedEffect(state.chapterLoading) {
        if (state.chapterLoading) {
            delay(CHAPTER_LOADING_INDICATOR_DELAY_MILLIS)
            showChapterLoading = true
        } else {
            showChapterLoading = false
        }
    }
    LaunchedEffect(sheet) { sheet?.let { retainedSheet = it } }
    DisposableEffect(state.settings.showStatusBar, palette.background, view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val useDarkSystemIcons = palette.background.luminance() > .5f
        controller?.isAppearanceLightNavigationBars = useDarkSystemIcons
        controller?.isAppearanceLightStatusBars = useDarkSystemIcons
        if (state.settings.showStatusBar) {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
    // Navigation starts its pop transition before this composition is disposed.
    // Restore the status-bar inset at ON_PAUSE as well, so the destination
    // underneath is never laid out once against the reader's fullscreen inset.
    DisposableEffect(lifecycleOwner, state.settings.showStatusBar, view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val observer = LifecycleEventObserver { _, event ->
            if (!state.settings.showStatusBar) {
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> controller?.show(WindowInsetsCompat.Type.statusBars())
                    Lifecycle.Event.ON_RESUME -> controller?.hide(WindowInsetsCompat.Type.statusBars())
                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(state.settings.keepScreenOn, view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.settings.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }
    DisposableEffect(searchVisible, view) {
        val window = context.findActivity()?.window
        val previousSoftInputMode = window?.attributes?.softInputMode
        if (searchVisible) {
            // Keep the reader viewport stable while the IME is visible. The
            // floating search panel alone follows WindowInsets.ime.
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        onDispose {
            if (searchVisible && previousSoftInputMode != null) {
                window.setSoftInputMode(previousSoftInputMode)
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    PredictiveBackHandler(
        enabled = sheet == null && (searchVisible || menu || toolsMenu || state.searchResults.isNotEmpty()),
    ) { events ->
        try {
            events.collect { backProgress = it.progress }
            when {
                searchVisible -> {
                    searchVisible = false
                    clearSearch()
                }
                toolsMenu -> toolsMenu = false
                menu -> menu = false
                state.searchResults.isNotEmpty() -> clearSearch()
            }
        } catch (_: CancellationException) { } finally {
            backProgress = 0f
        }
    }

    val currentPageBookmark = state.chapter?.let { chapter ->
        state.bookmarks.firstOrNull { bookmark ->
            bookmark.chapterId == chapter.id && bookmark.position == state.currentPosition
        }
    }
    val exitReader: () -> Unit = {
        if (!exitRequested) {
            exitRequested = true
            val controller = context.findActivity()?.window?.let {
                WindowCompat.getInsetsController(it, view)
            }
            controller?.show(WindowInsetsCompat.Type.statusBars())
            scope.launch {
                if (!state.settings.showStatusBar) {
                    var previousTop = -1
                    var stableFrames = 0
                    for (frame in 0 until 12) {
                        withFrameNanos { }
                        val top = ViewCompat.getRootWindowInsets(view)
                            ?.getInsets(WindowInsetsCompat.Type.statusBars())
                            ?.top ?: 0
                        stableFrames = if (top > 0 && top == previousTop) stableFrames + 1 else 0
                        previousTop = top
                        if (stableFrames >= 1) break
                    }
                }
                onExit()
            }
        }
    }
    KixyuOverlayHost(Modifier.fillMaxSize()) {
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
                !readerContentReady -> Unit
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.accent)
                state.error != null -> Text(state.error, color = palette.body, modifier = Modifier.align(Alignment.Center))
            }
            AnimatedVisibility(
                visible = readerContentReady && !state.loading && state.error == null && state.chapter != null,
                enter = fadeIn(tween(KixyuMotion.ReaderPopupExitMillis)),
                exit = fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)),
            ) {
                ReaderContent(
                    state = state,
                    palette = palette,
                    savePosition = savePosition,
                    moveChapterFromPage = moveChapterFromPage,
                    middleTap = { controls = !controls; if (!controls) { menu = false; toolsMenu = false } },
                    dismissControls = { controls = false; menu = false; toolsMenu = false },
                    volumeTurns = volumeTurns,
                    chapterRendered = chapterRendered,
                )
            }
            ReaderControlVisibility(
                visible = showChapterLoading,
                modifier = Modifier.align(Alignment.Center),
            ) {
                KixyuPopupSurface(shadowElevation = KixyuSpacing.extraSmall) {
                    Row(
                        modifier = Modifier.padding(horizontal = KixyuSpacing.medium, vertical = KixyuSpacing.small),
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = palette.accent,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = state.pendingChapterTitle?.let { "正在加载 · $it" } ?: "正在加载章节",
                            color = palette.body,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
            ReaderControls(
                visible = controls, menuVisible = menu, toolsMenuVisible = toolsMenu, progress = backProgress,
                bookTitle = state.book?.title.orEmpty().takeIf { state.searchResults.isEmpty() }.orEmpty(),
                currentPageBookmarked = currentPageBookmark != null,
                hasPreviousChapter = state.chapterIndex > 0,
                hasNextChapter = state.chapterIndex < state.chapters.lastIndex,
                onPreviousChapter = { moveChapter(-1, false) },
                onNextChapter = { moveChapter(1, false) },
                onExit = exitReader, onDirectory = { sheet = ReaderSheet.DIRECTORY },
                onBookInfo = { bookInfoVisible = true },
                onSettings = { menu = !menu; toolsMenu = false },
                onTools = { toolsMenu = !toolsMenu; menu = false },
                onToggleBookmark = {
                    currentPageBookmark?.let { deleteBookmark(it.uuid) } ?: addBookmark()
                    toolsMenu = false
                },
                onSearch = {
                    searchVisible = true
                    controls = false
                    menu = false
                    toolsMenu = false
                },
                onSheet = { sheet = it },
            )
            }
        }

        ReaderSearchOverlay(
            visible = searchVisible,
            progress = backProgress,
            state = state,
            onDismiss = {
                searchVisible = false
                clearSearch()
            },
            onSearch = search,
            onMove = moveSearchResult,
            onSelect = { index ->
                selectSearchResult(index)
                controls = false
                menu = false
                toolsMenu = false
            },
        )

        val activeSheet = sheet ?: retainedSheet
        KixyuBottomSheet(
            show = sheet != null,
            onDismissRequest = { sheet = null },
        ) {
            when (activeSheet) {
                ReaderSheet.DIRECTORY -> DirectorySheet(
                    state = state,
                    selectChapter = { index -> jumpChapter(index); sheet = null },
                    selectBookmark = { bookmark -> jumpPosition(bookmark.chapterIndex, bookmark.position); sheet = null },
                    deleteBookmark = deleteBookmark,
                )
                ReaderSheet.THEME -> ThemeSheet(state.settings, updateSettings)
                ReaderSheet.LAYOUT -> LayoutSheet(state, updateSettings, addFont, deleteFont)
                ReaderSheet.SETTINGS -> ReaderSettingsSheet(state.settings, updateSettings)
                null -> Unit
            }
        }
        BookInfoDialog(
            show = bookInfoVisible,
            book = state.book,
            dismiss = { bookInfoVisible = false },
        )
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun ReaderContent(
    state: ReaderUiState,
    palette: ReaderRenderPalette,
    savePosition: (Int, Boolean) -> Unit,
    moveChapterFromPage: (Int, Int, Boolean) -> Unit,
    middleTap: () -> Unit,
    dismissControls: () -> Unit,
    volumeTurns: SharedFlow<Int>,
    chapterRendered: (Int) -> Unit,
) {
    val chapter = state.chapter ?: return
    val density = LocalDensity.current
    val paginationCoordinator = rememberReaderPaginationCoordinator()
    val paginationMeasurer = rememberTextMeasurer(cacheSize = READER_TEXT_MEASURE_CACHE_SIZE)
    val topInsetDp = with(density) { WindowInsets.safeDrawing.getTop(this).toDp().value }
    val bottomInsetDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp().value }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safeViewportHeight = (maxHeight.value - topInsetDp - bottomInsetDp).coerceAtLeast(1f)
        val spec = ReaderLayoutSpec(maxWidth.value, safeViewportHeight, state.settings.fontSize, state.settings.lineHeight, state.settings.letterSpacing, state.settings.margin)
        if (state.settings.pageMode == PageMode.SCROLL) {
            key(chapter.id, spec, state.navigationVersion) {
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
                            direction < 0 && !listState.canScrollBackward -> moveChapterFromPage(state.chapterIndex, -1, true)
                            direction > 0 && !listState.canScrollForward -> moveChapterFromPage(state.chapterIndex, 1, false)
                            else -> {
                                val viewport = listState.layoutInfo.run { viewportEndOffset - viewportStartOffset }
                                listState.animateScrollBy(viewport * direction.toFloat())
                            }
                        }
                    }
                }
                ReaderScrollRenderer(
                    chapter, listState, spec, palette, state.fontPath,
                    { fraction -> if (fraction in .33f..67f) middleTap() else dismissControls() },
                    { dismissControls(); moveChapterFromPage(state.chapterIndex, -1, true) },
                    { dismissControls(); moveChapterFromPage(state.chapterIndex, 1, false) },
                    state.chapterIndex > 0, state.chapterIndex < state.chapters.lastIndex,
                    topInsetDp, bottomInsetDp,
                    epubPath = state.book?.takeIf { it.format == BookFormat.EPUB }?.storagePath,
                    modifier = Modifier.fillMaxSize(),
                    highlightQuery = state.searchQuery,
                )
                LaunchedEffect(chapter.id, state.navigationVersion) {
                    withFrameNanos { }
                    chapterRendered(state.navigationVersion)
                }
            }
        } else {
            Box(
                Modifier.fillMaxSize().padding(top = topInsetDp.dp, bottom = bottomInsetDp.dp),
            ) {
                PagedReader(
                    state, chapter, spec, palette, savePosition, moveChapterFromPage,
                    middleTap, dismissControls, volumeTurns, paginationCoordinator, paginationMeasurer,
                    chapterRendered,
                )
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
    moveChapterFromPage: (Int, Int, Boolean) -> Unit,
    middleTap: () -> Unit,
    dismissControls: () -> Unit,
    volumeTurns: SharedFlow<Int>,
    paginationCoordinator: ReaderPaginationCoordinator,
    paginationMeasurer: androidx.compose.ui.text.TextMeasurer,
    chapterRendered: (Int) -> Unit,
) {
    var retainedPage by remember(
        spec,
        state.fontPath,
        state.settings.showChapterTitle,
    ) { mutableStateOf<RetainedReaderPage?>(null) }
    // Always finish the requested chapter first. EPUB pagination includes rich spans and image
    // blocks, so starting three layouts together made the visible chapter compete with prefetch.
    val pages = rememberMeasuredReaderPages(
        chapter = chapter,
        spec = spec,
        fontPath = state.fontPath,
        showRegularChapterTitle = state.settings.showChapterTitle,
        coordinator = paginationCoordinator,
        measurer = paginationMeasurer,
    )
    if (pages.isEmpty()) {
        retainedPage?.let { retained ->
            ReaderPageRenderer(
                page = retained.page,
                spec = spec,
                palette = palette,
                fontPath = state.fontPath,
                onTapFraction = { fraction ->
                    if (fraction in .33f..67f) middleTap() else dismissControls()
                },
                epubPath = state.book?.takeIf { it.format == BookFormat.EPUB }?.storagePath,
                showRegularChapterTitle = state.settings.showChapterTitle,
                highlightQuery = state.searchQuery,
                pageNumber = retained.pageNumber,
            )
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = chapter.title.substringAfterLast('·').trim(),
                color = palette.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = state.settings.margin.dp),
            )
        }
        return
    }
    LaunchedEffect(chapter.id, state.navigationVersion, pages) {
        withFrameNanos { }
        chapterRendered(state.navigationVersion)
    }
    val nextChapter = state.prefetchedChapters[state.chapterIndex + 1]
    val nextPages = nextChapter?.let {
        rememberMeasuredReaderPages(
            chapter = it,
            spec = spec,
            fontPath = state.fontPath,
            showRegularChapterTitle = state.settings.showChapterTitle,
            coordinator = paginationCoordinator,
            measurer = paginationMeasurer,
            prefetch = true,
        )
    }.orEmpty()
    val previousChapter = state.prefetchedChapters[state.chapterIndex - 1]
    val previousPages = previousChapter?.let {
        rememberMeasuredReaderPages(
            chapter = it,
            spec = spec,
            fontPath = state.fontPath,
            showRegularChapterTitle = state.settings.showChapterTitle,
            coordinator = paginationCoordinator,
            measurer = paginationMeasurer,
            prefetch = true,
        )
    }.orEmpty()
    val positions = remember { ReaderPositionManager() }
    val hasPrevious = state.chapterIndex > 0; val hasNext = state.chapterIndex < state.chapters.lastIndex
    // Boundary pages must depend only on the chapter list. Neighbour pagination commonly finishes
    // after this pager is composed; tying a boundary to that asynchronous result permanently
    // disabled swipe-to-next/previous while tap navigation still worked. Keep the page count stable
    // and temporarily render the current edge page until the prefetched neighbour is ready.
    val hasPreviousCover = hasPrevious
    val hasNextCover = hasNext
    val leading = if (hasPreviousCover) 1 else 0
    val virtualCount = pages.size + leading + if (hasNextCover) 1 else 0
    val initial = positions.pageFor(pages, state.restorePosition) + leading
    key(chapter.id, state.navigationVersion) {
        val pager = rememberPagerState(
            initialPage = initial.coerceIn(0, virtualCount - 1),
            pageCount = { virtualCount },
        )
        val scope = rememberCoroutineScope()
        LaunchedEffect(pager, chapter.id) {
            snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
                when {
                    hasPreviousCover && page == 0 -> moveChapterFromPage(state.chapterIndex, -1, true)
                    hasNextCover && page == virtualCount - 1 -> moveChapterFromPage(state.chapterIndex, 1, false)
                    else -> {
                        val actualPage = page - leading
                        pages.getOrNull(actualPage)?.let { settledPage ->
                            retainedPage = RetainedReaderPage(
                                page = settledPage,
                                pageNumber = readerPageNumber(state, actualPage, pages.size),
                            )
                            savePosition(settledPage.startParagraph, actualPage == pages.lastIndex)
                        }
                    }
                }
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
                when {
                    target != pager.currentPage -> pager.animateScrollToPage(target)
                    direction < 0 && hasPrevious -> moveChapterFromPage(state.chapterIndex, -1, true)
                    direction > 0 && hasNext -> moveChapterFromPage(state.chapterIndex, 1, false)
                }
            }
        }
        val initialActual = (initial - leading).coerceIn(pages.indices)
        LaunchedEffect(chapter.id, initialActual, pages) {
            retainedPage = RetainedReaderPage(
                page = pages[initialActual],
                pageNumber = readerPageNumber(state, initialActual, pages.size),
            )
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { virtualPage ->
            val actual = virtualPage - leading
            val renderedPage = when {
                actual in pages.indices -> pages[actual]
                actual < 0 -> previousPages.lastOrNull() ?: pages.first()
                else -> nextPages.firstOrNull() ?: pages.last()
            }
            ReaderPageRenderer(
                renderedPage, spec, palette, state.fontPath,
                { fraction -> scope.launch {
                    when {
                        fraction < .33f && pager.currentPage > 0 -> {
                            dismissControls(); pager.animateScrollToPage(pager.currentPage - 1)
                        }
                        fraction < .33f && hasPrevious -> {
                            dismissControls(); moveChapterFromPage(state.chapterIndex, -1, true)
                        }
                        fraction > .67f && pager.currentPage < virtualCount - 1 -> {
                            dismissControls(); pager.animateScrollToPage(pager.currentPage + 1)
                        }
                        fraction > .67f && hasNext -> {
                            dismissControls(); moveChapterFromPage(state.chapterIndex, 1, false)
                        }
                        else -> middleTap()
                    }
                } },
                epubPath = state.book?.takeIf { it.format == BookFormat.EPUB }?.storagePath,
                showRegularChapterTitle = state.settings.showChapterTitle,
                highlightQuery = state.searchQuery,
                pageNumber = when {
                    actual in pages.indices -> readerPageNumber(state, actual, pages.size)
                    actual < 0 && previousPages.isNotEmpty() -> readerPageNumber(state, previousPages.lastIndex, previousPages.size)
                    actual >= pages.size && nextPages.isNotEmpty() -> readerPageNumber(state, 0, nextPages.size)
                    actual < 0 -> readerPageNumber(state, 0, pages.size)
                    else -> readerPageNumber(state, pages.lastIndex, pages.size)
                },
            )
        }
    }
}

private data class RetainedReaderPage(
    val page: ReaderPage,
    val pageNumber: String?,
)

private fun readerPageNumber(state: ReaderUiState, pageIndex: Int, pageCount: Int): String? =
    if (state.settings.showPageNumber && state.searchResults.isEmpty() && pageCount > 0) {
        "${pageIndex + 1}/$pageCount"
    } else {
        null
    }

@Composable
private fun ReaderControls(
    visible: Boolean,
    menuVisible: Boolean,
    toolsMenuVisible: Boolean,
    progress: Float,
    bookTitle: String,
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
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (bookTitle.isNotBlank()) {
                KixyuPopupSurface(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(top = KixyuSize.readerTopControlInset)
                        .height(KixyuSize.readerBookTitleHeight)
                        .widthIn(max = KixyuSize.readerBookTitleMaxWidth)
                        .clickable(onClick = onBookInfo)
                        .then(controlsBackModifier),
                ) {
                    Box(
                        Modifier.fillMaxHeight().padding(horizontal = KixyuSpacing.small),
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
                KixyuTonalIconButton(
                    onClick = onExit,
                    modifier = Modifier.size(KixyuSize.readerControlButton),
                ) { Icon(Icons.Outlined.Close, "退出") }
                KixyuTonalIconButton(
                    onClick = onDirectory,
                    modifier = Modifier.size(KixyuSize.readerControlButton),
                ) { Icon(Icons.AutoMirrored.Outlined.Toc, "目录") }
                KixyuTonalIconButton(
                    onClick = onPreviousChapter,
                    enabled = hasPreviousChapter,
                    modifier = Modifier.size(KixyuSize.readerControlButton),
                ) {
                    Icon(Icons.Outlined.SkipPrevious, "上一章")
                }
                KixyuTonalIconButton(
                    onClick = onNextChapter,
                    enabled = hasNextChapter,
                    modifier = Modifier.size(KixyuSize.readerControlButton),
                ) {
                    Icon(Icons.Outlined.SkipNext, "下一章")
                }
                Box {
                    KixyuTonalIconButton(
                        onClick = onTools,
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
                    KixyuTonalIconButton(
                        onClick = onSettings,
                        modifier = Modifier.size(KixyuSize.readerControlButton),
                    ) { Icon(Icons.Outlined.Settings, "设置") }
                    KixyuPopupMenu(
                        expanded = menuVisible,
                        onDismissRequest = { if (menuVisible) onSettings() },
                        alignEnd = true,
                        items = listOf(
                            KixyuPopupMenuItem("阅读主题", Icons.Outlined.Palette) {
                                onSettings(); onSheet(ReaderSheet.THEME)
                            },
                            KixyuPopupMenuItem("页面外观", Icons.Outlined.ViewCarousel) {
                                onSettings(); onSheet(ReaderSheet.LAYOUT)
                            },
                            KixyuPopupMenuItem("阅读设置", Icons.Outlined.Tune) {
                                onSettings(); onSheet(ReaderSheet.SETTINGS)
                            },
                        ),
                    )
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
    val isMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
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
            KixyuIconButton(onClick = {
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
                    (slideInHorizontally(tween(KixyuMotion.ReaderPopupEnterMillis)) { it / 3 } +
                        fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis))) togetherWith
                        (slideOutHorizontally(tween(KixyuMotion.ReaderPopupExitMillis)) { -it / 3 } +
                            fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)))
                } else {
                    (slideInHorizontally(tween(KixyuMotion.ReaderPopupEnterMillis)) { -it / 3 } +
                        fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis))) togetherWith
                        (slideOutHorizontally(tween(KixyuMotion.ReaderPopupExitMillis)) { it / 3 } +
                            fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)))
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
                            KixyuListRow(
                                title = state.chapters[index].title,
                                selected = current,
                                highlighted = hasBookmark,
                                onClick = { selectChapter(index) },
                                leading = {
                                    Box(Modifier.size(KixyuSize.icon), contentAlignment = Alignment.Center) {
                                        if (current) {
                                            Icon(
                                                Icons.Outlined.PlayArrow,
                                                null,
                                                Modifier.size(KixyuSize.icon),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                                trailing = {
                                    if (hasBookmark) {
                                        Icon(
                                            Icons.Filled.Bookmark,
                                            "本章有书签",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                modifier = if (isMiuix) {
                                    Modifier.padding(horizontal = KixyuSpacing.medium, vertical = KixyuSpacing.extraSmall)
                                } else Modifier,
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
                        KixyuListRow(
                            title = bookmark.chapterTitle,
                            supportingText = bookmark.preview.ifBlank { "第 ${bookmark.position + 1} 段" },
                            onClick = { selectBookmark(bookmark) },
                            leading = { Icon(Icons.Outlined.Bookmark, null) },
                            trailing = {
                                KixyuIconButton(onClick = { deleteBookmark(bookmark.uuid) }) {
                                    Icon(Icons.Outlined.DeleteOutline, "删除书签")
                                }
                            },
                            modifier = if (isMiuix) {
                                Modifier.padding(horizontal = KixyuSpacing.medium, vertical = KixyuSpacing.extraSmall)
                            } else Modifier,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderSearchOverlay(
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
            KixyuPopupSurface(
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
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) expanded = true },
                        placeholder = { Text("搜索书中内容", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            KixyuIconButton(onClick = ::submit, enabled = query.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, "搜索")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
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
                            modifier = Modifier.pointerInput(index) {
                                detectTapGestures {
                                    onSelect(index)
                                    focusManager.clearFocus()
                                    expanded = false
                                }
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

@Composable private fun ThemeSheet(settings: ReaderSettings, update: ((ReaderSettings) -> ReaderSettings) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = KixyuSpacing.large),
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
    ) {
        item { Text("阅读主题", style = MaterialTheme.typography.titleLarge, maxLines = 1) }
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
private fun LayoutSheet(
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
        item { Text("页面外观", style = MaterialTheme.typography.titleLarge, maxLines = 1) }
        item {
            KixyuSection(title = "字体") {
                KixyuFontControls(
                    fonts = state.availableFonts,
                    selectedFontUuid = settings.fontUuid,
                    onSelectFont = { uuid -> update { it.copy(fontUuid = uuid) } },
                    onAddFont = addFont,
                    onDeleteFont = deleteFont,
                )
            }
        }
        item {
            KixyuSection(title = "排版") {
                KixyuReaderLayoutControls(settings) { updated -> update { updated } }
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
                KixyuReaderBehaviorControls(settings) { updated -> update { updated } }
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable private fun BookInfoDialog(
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

private const val READER_ENTRY_CONTENT_DELAY_MILLIS = 120L
// Pagination inputs are almost always unique remainders. Caching their TextLayoutResult objects
// retains large native buffers without producing useful hits, especially for malformed EPUB text.
private const val READER_TEXT_MEASURE_CACHE_SIZE = 0
private const val CHAPTER_LOADING_INDICATOR_DELAY_MILLIS = 180L
