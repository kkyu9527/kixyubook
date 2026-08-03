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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.input.ImeAction
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
    initialSettings: ReaderSettings = ReaderSettings(),
    onExit: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var readerDestinationEntered by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) readerDestinationEntered = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!readerDestinationEntered) {
        ReaderEntrySurface(initialSettings)
    } else {
        // Create Hilt/ViewModel only after Navigation has committed the enter transition. This is
        // intentionally load-after-motion: Room, EPUB and pagination work cannot compete with the
        // single animated surface for a 120 Hz frame budget.
        LoadedReaderRoute(initialSettings = initialSettings, onExit = onExit)
    }
}

@Composable
private fun LoadedReaderRoute(
    initialSettings: ReaderSettings,
    onExit: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var readerResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
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
    val renderedState = if (state.settingsLoaded) state else state.copy(settings = initialSettings)
    if (state.loading) {
        // Keep the same lightweight cover visible from the first navigation frame until the
        // initial chapter is ready. ReaderScreen never exposes an intermediate blank frame.
        ReaderEntrySurface(renderedState.settings)
        return
    }
    ReaderScreen(
        state = renderedState,
        readerContentReady = state.settingsLoaded,
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
        setPageInteractionActive = viewModel::setPageInteractionActive,
        addFont = {
            fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
        },
        deleteFont = viewModel::deleteFont,
    )
}

/**
 * The navigation transition only needs an opaque destination surface. Keeping this deliberately
 * free of Scaffold, overlay hosts, focus, insets mutation and text measurement prevents Reader's
 * first composition from consuming the same frames as the horizontal route animation. The
 * ViewModel continues loading the current chapter while this surface is visible.
 */
@Composable
private fun ReaderEntrySurface(settings: ReaderSettings) {
    val palette = readerPalette(settings, androidx.compose.foundation.isSystemInDarkTheme())
    Box(
        modifier = Modifier.fillMaxSize().background(palette.background),
        contentAlignment = Alignment.Center,
    ) {
        ReaderLoadingIndicator(palette)
    }
}

@Composable
private fun ReaderLoadingIndicator(palette: ReaderRenderPalette) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = palette.body.copy(alpha = if (palette.background.luminance() > .5f) .08f else .14f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = KixyuSpacing.medium,
                vertical = KixyuSpacing.small,
            ),
            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = palette.accent,
                strokeWidth = 2.dp,
            )
            Text(
                text = "正在加载章节",
                color = palette.body,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    setPageInteractionActive: (Boolean) -> Unit,
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
    var pageInteractionActive by remember { mutableStateOf(false) }
    var overlayAnimationPriority by remember { mutableStateOf(false) }
    var overlayMotionObserved by remember { mutableStateOf(false) }
    val isMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = readerPalette(state.settings, systemDark)
    val overlayVisible = controls || menu || toolsMenu || searchVisible ||
        bookInfoVisible || sheet != null
    val statusBarVisible = state.settings.showStatusBar || overlayVisible
    val navigationBarVisible = !state.settings.hideNavigationBar || overlayVisible
    val systemBarDensity = LocalDensity.current
    val actualStatusBarVisible = WindowInsets.statusBars.getTop(systemBarDensity) > 0
    val actualNavigationBarVisible = WindowInsets.navigationBars.run {
        getBottom(systemBarDensity) > 0 || getLeft(systemBarDensity, LayoutDirection.Ltr) > 0 ||
            getRight(systemBarDensity, LayoutDirection.Ltr) > 0
    }
    val overlayMotionKey = listOf(
        controls,
        menu,
        toolsMenu,
        searchVisible,
        bookInfoVisible,
        sheet,
    )
    LaunchedEffect(overlayMotionKey) {
        if (!overlayMotionObserved) {
            overlayMotionObserved = true
            return@LaunchedEffect
        }
        // Pause pagination only while an overlay is actually moving. A visible, settled control
        // layer must never block a directory/search chapter jump indefinitely.
        overlayAnimationPriority = true
        delay(READER_OVERLAY_SETTLE_MILLIS)
        withFrameNanos { }
        withFrameNanos { }
        overlayAnimationPriority = false
    }
    // Background indexing and distant prefetch can remain paused while an overlay is visible;
    // unlike current-chapter pagination, neither is required to fulfil the user's active action.
    val resourcePriorityActive = pageInteractionActive || overlayVisible || overlayAnimationPriority
    LaunchedEffect(resourcePriorityActive) {
        setPageInteractionActive(resourcePriorityActive)
    }
    DisposableEffect(Unit) {
        onDispose { setPageInteractionActive(false) }
    }
    LaunchedEffect(state.chapterLoading) {
        if (state.chapterLoading) {
            delay(CHAPTER_LOADING_INDICATOR_DELAY_MILLIS)
            showChapterLoading = true
        } else {
            showChapterLoading = false
        }
    }
    LaunchedEffect(sheet) { sheet?.let { retainedSheet = it } }
    val systemBarsController = remember(context, view) {
        context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
    }
    val applyReaderSystemBars by rememberUpdatedState(newValue = {
        val useDarkSystemIcons = palette.background.luminance() > .5f
        // Default behavior keeps edge-to-edge bars fully transparent. Transient-bars behavior
        // adds a system-owned contrast scrim on several Android/HyperOS versions.
        systemBarsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        systemBarsController?.isAppearanceLightNavigationBars = useDarkSystemIcons
        systemBarsController?.isAppearanceLightStatusBars = useDarkSystemIcons
        if (statusBarVisible) {
            systemBarsController?.show(WindowInsetsCompat.Type.statusBars())
        } else {
            systemBarsController?.hide(WindowInsetsCompat.Type.statusBars())
        }
        if (navigationBarVisible) {
            systemBarsController?.show(WindowInsetsCompat.Type.navigationBars())
        } else {
            systemBarsController?.hide(WindowInsetsCompat.Type.navigationBars())
        }
    })
    val restoreSystemBars by rememberUpdatedState(newValue = {
        systemBarsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        systemBarsController?.show(WindowInsetsCompat.Type.systemBars())
    })
    DisposableEffect(statusBarVisible, navigationBarVisible, palette.background, systemBarsController) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            applyReaderSystemBars()
        }
        onDispose { }
    }
    // With the transparent default behavior, a system gesture reveals bars persistently. Mirror
    // the short auto-dismiss used by reader apps without adopting the system's transient scrim.
    LaunchedEffect(statusBarVisible, actualStatusBarVisible) {
        if (!statusBarVisible && actualStatusBarVisible) {
            delay(SYSTEM_BAR_GESTURE_HIDE_MILLIS)
            systemBarsController?.hide(WindowInsetsCompat.Type.statusBars())
        }
    }
    LaunchedEffect(navigationBarVisible, actualNavigationBarVisible) {
        if (!navigationBarVisible && actualNavigationBarVisible) {
            delay(SYSTEM_BAR_GESTURE_HIDE_MILLIS)
            systemBarsController?.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
    // Navigation starts its pop transition before this composition is disposed.
    // Restore both bars at ON_PAUSE, so the destination underneath is never laid out
    // once against the reader's immersive insets. ON_RESUME reapplies reader policy.
    DisposableEffect(lifecycleOwner, systemBarsController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> restoreSystemBars()
                Lifecycle.Event.ON_RESUME -> applyReaderSystemBars()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            restoreSystemBars()
        }
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
            restoreSystemBars()
            scope.launch {
                if (!state.settings.showStatusBar || state.settings.hideNavigationBar) {
                    var previousInsets = Int.MIN_VALUE to Int.MIN_VALUE
                    var stableFrames = 0
                    for (frame in 0 until 12) {
                        withFrameNanos { }
                        val insets = ViewCompat.getRootWindowInsets(view)
                        val currentInsets =
                            (insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0) to
                                (insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0)
                        stableFrames = if (
                            (currentInsets.first > 0 || currentInsets.second > 0) &&
                            currentInsets == previousInsets
                        ) stableFrames + 1 else 0
                        previousInsets = currentInsets
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
                .semantics { contentDescription = "阅读正文" }
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
                !readerContentReady -> state.chapter?.let { pendingChapter ->
                    Text(
                        text = pendingChapter.title.substringAfterLast('·').trim(),
                        color = palette.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center)
                            .padding(horizontal = state.settings.margin.dp),
                    )
                }
                state.error != null -> Text(state.error, color = palette.body, modifier = Modifier.align(Alignment.Center))
                state.chapter != null -> ReaderContent(
                    state = state,
                    palette = palette,
                    savePosition = savePosition,
                    moveChapterFromPage = moveChapterFromPage,
                    middleTap = { controls = !controls; if (!controls) { menu = false; toolsMenu = false } },
                    dismissControls = { controls = false; menu = false; toolsMenu = false },
                    volumeTurns = volumeTurns,
                    chapterRendered = chapterRendered,
                    setPageInteractionActive = { pageInteractionActive = it },
                    // A page drag needs the already-started previous/next page layouts. Only
                    // overlays may cancel pagination; the drag still pauses unrelated EPUB work
                    // through setPageInteractionActive above.
                    resourcePriorityActive = overlayAnimationPriority,
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
                accentColor = palette.accent,
                backgroundColor = palette.background,
                currentPageBookmarked = currentPageBookmark != null,
                hasPreviousChapter = state.chapterIndex > 0,
                hasNextChapter = state.chapterIndex < state.chapters.lastIndex,
                onPreviousChapter = { moveChapter(-1, false) },
                onNextChapter = { moveChapter(1, false) },
                onExit = exitReader,
                onDirectory = {
                    controls = false
                    menu = false
                    toolsMenu = false
                    sheet = ReaderSheet.DIRECTORY
                },
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
                onSheet = {
                    controls = false
                    menu = false
                    toolsMenu = false
                    sheet = it
                },
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
                    selectChapter = { index ->
                        sheet = null
                        controls = false
                        menu = false
                        toolsMenu = false
                        jumpChapter(index)
                    },
                    selectBookmark = { bookmark ->
                        sheet = null
                        controls = false
                        menu = false
                        toolsMenu = false
                        jumpPosition(bookmark.chapterIndex, bookmark.position)
                    },
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

@OptIn(kotlinx.coroutines.FlowPreview::class, ExperimentalLayoutApi::class)
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
    setPageInteractionActive: (Boolean) -> Unit,
    resourcePriorityActive: Boolean,
) {
    val chapter = state.chapter ?: return
    val density = LocalDensity.current
    val paginationCoordinator = rememberReaderPaginationCoordinator()
    val paginationMeasurer = rememberTextMeasurer(cacheSize = READER_TEXT_MEASURE_CACHE_SIZE)
    // The reading viewport must not change when transient system bars appear. Derive its
    // reserved space from the preference and ignoring-visibility insets, never current visibility.
    val stableTopInsets = if (state.settings.showStatusBar) {
        WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    } else {
        WindowInsets.displayCutout
    }
    val topInsetDp = with(density) { stableTopInsets.getTop(this).toDp().value }
    val bottomInsetDp = with(density) {
        WindowInsets.navigationBarsIgnoringVisibility.getBottom(this).toDp().value
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safeViewportHeight = (maxHeight.value - topInsetDp - bottomInsetDp).coerceAtLeast(1f)
        val twoPageSpread = maxWidth >= 840.dp && maxWidth > maxHeight
        val pageViewportWidth = if (twoPageSpread) {
            ((maxWidth - KixyuSize.readerSpreadGutter) / 2).value
        } else {
            maxWidth.value
        }
        // Pagination is measured against one physical leaf. In a tablet landscape spread two
        // leaves are rendered by one Pager item; portrait and narrow split-screen windows keep
        // the phone layout. Very wide leaves still retain a bounded, readable line measure.
        val adaptiveHorizontalMargin = maxOf(
            state.settings.margin,
            (pageViewportWidth - KixyuSize.readerTextMaxWidth.value) / 2f,
        )
        val spec = ReaderLayoutSpec(
            viewportWidthDp = pageViewportWidth,
            viewportHeightDp = safeViewportHeight,
            fontSizeSp = state.settings.fontSize,
            lineHeightMultiplier = state.settings.lineHeight,
            letterSpacingEm = state.settings.letterSpacing,
            horizontalMarginDp = adaptiveHorizontalMargin,
        )
        if (state.settings.pageMode == PageMode.SCROLL) {
            key(chapter.id, spec, state.navigationVersion) {
                val contentParagraphs = remember(chapter) { chapter.contentParagraphs() }
                val restoredItem = remember(contentParagraphs, state.restorePosition) {
                    ReaderPositionManager().contentItemFor(contentParagraphs, state.restorePosition)
                }
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
                    snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged().collectLatest { scrolling ->
                        if (scrolling) {
                            setPageInteractionActive(true)
                            dismissControls()
                        } else {
                            // Let the settled page draw before resuming speculative EPUB work.
                            // Frame-based deferral adapts to 60/90/120 Hz and is not a timer delay.
                            withFrameNanos { }
                            withFrameNanos { }
                            setPageInteractionActive(false)
                        }
                    }
                }
                DisposableEffect(listState) {
                    onDispose { setPageInteractionActive(false) }
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
                    chapterRendered, setPageInteractionActive, resourcePriorityActive, twoPageSpread,
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
    setPageInteractionActive: (Boolean) -> Unit,
    resourcePriorityActive: Boolean,
    twoPageSpread: Boolean,
) {
    var retainedPage by remember(
        spec,
        state.fontPath,
        state.settings.showChapterTitle,
    ) { mutableStateOf<RetainedReaderPage?>(null) }
    var textSelectionActive by remember { mutableStateOf(false) }
    // Always finish the requested chapter first. EPUB pagination includes rich spans and image
    // blocks, so starting three layouts together made the visible chapter compete with prefetch.
    val pages = rememberMeasuredReaderPages(
        chapter = chapter,
        contentHash = state.book?.contentHash.orEmpty(),
        spec = spec,
        fontPath = state.fontPath,
        showRegularChapterTitle = state.settings.showChapterTitle,
        coordinator = paginationCoordinator,
        measurer = paginationMeasurer,
        paused = resourcePriorityActive,
    )
    LaunchedEffect(resourcePriorityActive) {
        if (resourcePriorityActive) paginationCoordinator.pauseInFlight()
    }
    // The first chapter still needs a loading surface, but once a Pager has rendered it must stay
    // in composition across chapter pagination. Removing it while the newly activated chapter is
    // being measured cancels any immediately-following drag and produces a visible spring-back.
    if (pages.isEmpty() && retainedPage == null) {
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
            ReaderLoadingIndicator(palette)
        }
        return
    }
    LaunchedEffect(chapter.id, state.navigationVersion, pages) {
        if (pages.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        chapterRendered(state.navigationVersion)
    }
    val nextChapter = state.prefetchedChapters[state.chapterIndex + 1]
    val nextPages = nextChapter?.takeUnless { resourcePriorityActive }?.let {
        rememberMeasuredReaderPages(
            chapter = it,
            contentHash = state.book?.contentHash.orEmpty(),
            spec = spec,
            fontPath = state.fontPath,
            showRegularChapterTitle = state.settings.showChapterTitle,
            coordinator = paginationCoordinator,
            measurer = paginationMeasurer,
            prefetch = true,
            paused = resourcePriorityActive,
        )
    }.orEmpty()
    val previousChapter = state.prefetchedChapters[state.chapterIndex - 1]
    val previousPages = previousChapter?.takeUnless { resourcePriorityActive }?.let {
        rememberMeasuredReaderPages(
            chapter = it,
            contentHash = state.book?.contentHash.orEmpty(),
            spec = spec,
            fontPath = state.fontPath,
            showRegularChapterTitle = state.settings.showChapterTitle,
            coordinator = paginationCoordinator,
            measurer = paginationMeasurer,
            prefetch = true,
            paused = resourcePriorityActive,
        )
    }.orEmpty()
    val positions = remember { ReaderPositionManager() }
    val hasPrevious = state.chapterIndex > 0; val hasNext = state.chapterIndex < state.chapters.lastIndex
    // Keep one physical Pager alive across chapter changes. Its stable page keys let Compose retain
    // the page that crossed the boundary while the three-chapter window is recentered around it.
    // Recreating PagerState per chapter cancels a second gesture that starts immediately after the
    // first one settles, which is the root cause of rapid-swipe spring-back.
    val pagerWindow = remember(
        state.chapterIndex,
        state.restorePosition,
        state.chapters.size,
        pages,
        previousPages,
        nextPages,
        hasPrevious,
        hasNext,
        twoPageSpread,
    ) {
        buildReaderPagerWindow(
            currentChapterIndex = state.chapterIndex,
            currentPages = pages,
            previousPages = previousPages,
            nextPages = nextPages,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            currentPlaceholderPageIndex = if (state.restorePosition > 0) Int.MIN_VALUE else 0,
            chapterCount = state.chapters.size,
            neighbourLeafCount = if (twoPageSpread) 2 else 1,
        )
    }
    val pagerSpreads = remember(pagerWindow, twoPageSpread) {
        buildReaderPagerSpreads(pagerWindow, twoPageSpread)
    }
    val currentStart = pagerWindow.indexOfFirst {
        it.chapterIndex == state.chapterIndex
    }.coerceAtLeast(0)
    val selectedSearchResult = state.searchResults.getOrNull(state.selectedSearchIndex)
    val targetSearchQuery = state.searchQuery.takeIf {
        selectedSearchResult?.chapterId == chapter.id &&
            selectedSearchResult.paragraphIndex == state.restorePosition
    }
    val initialActual = if (pages.isEmpty()) {
        if (state.restorePosition > 0) Int.MIN_VALUE else 0
    } else {
        positions.pageFor(pages, state.restorePosition, targetSearchQuery).coerceIn(pages.indices)
    }
    val desiredItemKey = pagerWindow.firstOrNull {
        it.chapterIndex == state.chapterIndex && it.pageIndex == initialActual
    }?.key ?: pagerWindow[currentStart.coerceIn(pagerWindow.indices)].key
    val desiredSpreadIndex = pagerSpreads.indexOfFirst { spread ->
        spread.items.any { it.key == desiredItemKey }
    }.takeIf { it >= 0 } ?: 0
    val desiredSpreadKey = pagerSpreads[desiredSpreadIndex].key
    val pager = rememberPagerState(
        initialPage = desiredSpreadIndex,
        pageCount = { pagerSpreads.size },
    )
    val turnRequests = remember { Channel<Int>(Channel.UNLIMITED) }
    var settledSpreadKey by remember { mutableStateOf(desiredSpreadKey) }
    val latestPagerSpreads by rememberUpdatedState(pagerSpreads)
    val latestReaderState by rememberUpdatedState(state)

    // Directory/search jumps intentionally select another logical page. Boundary navigation does
    // not scroll here: the key recorded by the completed gesture is already the desired page and
    // Compose keeps it anchored while the surrounding window changes.
    // Overlay visibility changes the amount of neighbouring content retained for rendering. It
    // must not replay a chapter-entry restore against the Pager: doing so sent an already-read
    // page back to the chapter opening whenever the controls appeared. Only an explicit logical
    // destination change may drive this positioning effect.
    LaunchedEffect(state.navigationVersion, desiredSpreadKey) {
        if (settledSpreadKey != desiredSpreadKey) {
            val target = pagerSpreads.indexOfFirst { it.key == desiredSpreadKey }
            if (target >= 0) {
                pager.scrollToPage(target)
                settledSpreadKey = desiredSpreadKey
            }
        }
    }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { pageIndex ->
            val spreads = latestPagerSpreads
            val readerState = latestReaderState
            val spread = spreads.getOrNull(pageIndex) ?: return@collect
            val item = spread.items.firstOrNull() ?: return@collect
            settledSpreadKey = spread.key
            when {
                item.chapterIndex < readerState.chapterIndex -> {
                    moveChapterFromPage(
                        readerState.chapterIndex,
                        item.chapterIndex - readerState.chapterIndex,
                        true,
                    )
                }
                item.chapterIndex > readerState.chapterIndex -> {
                    moveChapterFromPage(
                        readerState.chapterIndex,
                        item.chapterIndex - readerState.chapterIndex,
                        false,
                    )
                }
                item.page != null -> {
                    retainedPage = RetainedReaderPage(
                        page = item.page,
                        pageNumber = readerPageNumber(readerState, item.pageIndex, item.pageCount),
                    )
                    val lastVisible = spread.items.lastOrNull { visible ->
                        visible.chapterIndex == item.chapterIndex && visible.page != null
                    } ?: item
                    savePosition(
                        item.page.startParagraph,
                        lastVisible.pageIndex == lastVisible.pageCount - 1,
                    )
                }
            }
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.distinctUntilChanged().collectLatest { scrolling ->
            if (scrolling) {
                setPageInteractionActive(true)
                dismissControls()
            } else {
                // Selection and the new page are attached first; background parsing resumes
                // only after two clean frames, regardless of the display refresh rate.
                withFrameNanos { }
                withFrameNanos { }
                setPageInteractionActive(false)
            }
        }
    }
    DisposableEffect(pager) {
        onDispose { setPageInteractionActive(false) }
    }
    LaunchedEffect(pager, volumeTurns, turnRequests) {
        launch {
            volumeTurns.collect { direction -> turnRequests.send(direction) }
        }
        for (direction in turnRequests) {
            dismissControls()
            val spreads = latestPagerSpreads
            val readerState = latestReaderState
            val target = (pager.settledPage + direction).coerceIn(0, spreads.lastIndex)
            when {
                target != pager.settledPage -> {
                    var settled = false
                    while (!settled) {
                        try {
                            pager.animateScrollToPage(target)
                            settled = true
                        } catch (cancellation: CancellationException) {
                            if (!currentCoroutineContext().isActive) throw cancellation
                            // Pointer input owns Pager's MutatorMutex until that gesture finishes.
                            // Retry this accepted turn on the next frame instead of racing it with
                            // scrollToPage (which can be cancelled a second time and kill the
                            // consumer). Newer turns remain ordered in the channel behind it.
                            withFrameNanos { }
                            settled = pager.settledPage == target
                        }
                    }
                }
                direction < 0 && readerState.chapterIndex > 0 -> {
                    moveChapterFromPage(readerState.chapterIndex, -1, true)
                }
                direction > 0 && readerState.chapterIndex < readerState.chapters.lastIndex -> {
                    moveChapterFromPage(readerState.chapterIndex, 1, false)
                }
            }
        }
    }
    LaunchedEffect(chapter.id, initialActual, pages) {
        if (pages.isEmpty()) return@LaunchedEffect
        retainedPage = RetainedReaderPage(
            page = pages[initialActual],
            pageNumber = readerPageNumber(state, initialActual, pages.size),
        )
    }
    val pagerTap by rememberUpdatedState<(Float) -> Unit> { fraction ->
        if (textSelectionActive) return@rememberUpdatedState
        when {
            fraction < .33f && hasPrevious -> turnRequests.trySend(-1)
            fraction > .67f && hasNext -> turnRequests.trySend(1)
            fraction in .33f..67f -> middleTap()
        }
    }
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize().observePagerTap(
            onTapFraction = { fraction -> pagerTap(fraction) },
        ),
        // Only one already-measured neighbour is precomposed. This keeps the gesture surface
        // continuous without laying out the entire retained chapter window.
        beyondViewportPageCount = if (resourcePriorityActive) 0 else 1,
        key = { virtualPage -> pagerSpreads[virtualPage].key },
    ) { virtualPage ->
        val spread = pagerSpreads[virtualPage]
        Row(Modifier.fillMaxSize()) {
            spread.items.forEachIndexed { index, item ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    ReaderPagerLeaf(
                        item = item,
                        state = state,
                        spec = spec,
                        palette = palette,
                        middleTap = middleTap,
                        selectionEnabled = item.page != null &&
                            !pager.isScrollInProgress && virtualPage == pager.settledPage,
                        onSelectionActiveChange = { active -> textSelectionActive = active },
                    )
                }
                if (index < spread.items.lastIndex) {
                    Spacer(Modifier.width(KixyuSize.readerSpreadGutter))
                }
            }
            if (twoPageSpread && spread.items.size == 1) {
                Spacer(Modifier.width(KixyuSize.readerSpreadGutter))
                Spacer(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun ReaderPagerLeaf(
    item: ReaderPagerItem,
    state: ReaderUiState,
    spec: ReaderLayoutSpec,
    palette: ReaderRenderPalette,
    middleTap: () -> Unit,
    selectionEnabled: Boolean,
    onSelectionActiveChange: (Boolean) -> Unit,
) {
    // A not-yet-paginated chapter is still a fully interactive lightweight page. Rendering a
    // spinner-only Box here discarded every tap that arrived after the first rapid turn.
    val renderedPage = item.page ?: state.chapters.getOrNull(item.chapterIndex)?.let { target ->
        ReaderPage(
            index = item.pageIndex,
            chapterIndex = target.index,
            chapterTitle = target.title,
            isChapterOpening = true,
            blocks = emptyList(),
        )
    }
    if (renderedPage != null) {
        ReaderPageRenderer(
            page = renderedPage,
            spec = spec,
            palette = palette,
            fontPath = state.fontPath,
            onTapFraction = { fraction -> if (fraction in .33f..67f) middleTap() },
            epubPath = state.book?.takeIf { it.format == BookFormat.EPUB }?.storagePath,
            modifier = Modifier.fillMaxSize(),
            showRegularChapterTitle = state.settings.showChapterTitle,
            highlightQuery = state.searchQuery,
            pageNumber = item.page?.let {
                readerPageNumber(state, item.pageIndex, item.pageCount)
            },
            selectionEnabled = selectionEnabled,
            onSelectionActiveChange = onSelectionActiveChange,
        )
    }
}

/**
 * Page taps belong to the stable Pager node instead of page-local content. The current page can
 * be replaced by a lightweight chapter placeholder in the same frame that a rapid tap arrives;
 * keeping this observer above those pages prevents that tap from being discarded. It also makes
 * the tablet spread gutter and an empty companion leaf participate in the center-tap interaction.
 */
private fun Modifier.observePagerTap(
    onTapFraction: (Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
        val delta = up.position - down.position
        val isShortTap = up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
        val stayedInPlace = delta.x * delta.x + delta.y * delta.y <=
            viewConfiguration.touchSlop * viewConfiguration.touchSlop
        val fraction = up.position.x / size.width.coerceAtLeast(1)
        if (isShortTap && stayedInPlace) {
            // Initial pass reaches this stable parent before the translated page-local handler.
            // Consume only the completed tap so leaf renderers cannot toggle the controls twice.
            // Drags remain unconsumed for Pager.
            up.consume()
            onTapFraction(fraction)
        }
    }
}

internal data class ReaderPagerItem(
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val page: ReaderPage?,
) {
    // Pager/LazyLayout keys participate in saveable state and therefore must be Bundle-compatible.
    val key = "$chapterIndex:$pageIndex"
}

internal data class ReaderPagerSpread(
    val items: List<ReaderPagerItem>,
    val key: String,
)

/** Groups two consecutive leaves from the same chapter into one landscape page turn. */
internal fun buildReaderPagerSpreads(
    items: List<ReaderPagerItem>,
    twoPageSpread: Boolean,
): List<ReaderPagerSpread> {
    if (!twoPageSpread) return items.map { ReaderPagerSpread(listOf(it), it.key) }
    return buildList {
        var index = 0
        while (index < items.size) {
            val first = items[index]
            val second = items.getOrNull(index + 1)
            val pairable = first.page != null &&
                first.pageIndex >= 0 &&
                first.pageIndex % 2 == 0 &&
                second?.page != null &&
                second.chapterIndex == first.chapterIndex &&
                second.pageIndex == first.pageIndex + 1
            if (pairable) {
                add(readerPagerSpread(listOf(first, second)))
                index += 2
            } else {
                add(readerPagerSpread(listOf(first)))
                index++
            }
        }
    }
}

/** A right leaf may arrive after the left one; the spread identity must not change when it does. */
private fun readerPagerSpread(items: List<ReaderPagerItem>): ReaderPagerSpread {
    val first = items.first()
    val spreadIndex = if (first.pageIndex >= 0) first.pageIndex / 2 else first.pageIndex
    return ReaderPagerSpread(
        items = items,
        key = "${first.chapterIndex}:spread:$spreadIndex",
    )
}

internal fun buildReaderPagerWindow(
    currentChapterIndex: Int,
    currentPages: List<ReaderPage>,
    previousPages: List<ReaderPage>,
    nextPages: List<ReaderPage>,
    hasPrevious: Boolean,
    hasNext: Boolean,
    currentPlaceholderPageIndex: Int,
    chapterCount: Int,
    neighbourLeafCount: Int = 1,
): List<ReaderPagerItem> = buildList {
    val firstChapter = (currentChapterIndex - PAGER_NAVIGATION_RADIUS).coerceAtLeast(0)
    for (chapterIndex in firstChapter until currentChapterIndex - 1) {
        add(ReaderPagerItem(chapterIndex, Int.MIN_VALUE, 0, null))
    }
    if (hasPrevious) {
        // A landscape spread must already contain the same final leaf pair before and after the
        // chapter boundary is crossed. For an odd page count the final spread contains one leaf;
        // for an even page count it contains the final two leaves.
        val previousLeafCount = when {
            previousPages.isEmpty() -> 0
            neighbourLeafCount < 2 -> 1
            previousPages.size % 2 == 0 -> 2
            else -> 1
        }
        val visiblePreviousPages = previousPages.takeLast(previousLeafCount)
        if (visiblePreviousPages.isEmpty()) {
            add(ReaderPagerItem(currentChapterIndex - 1, Int.MIN_VALUE, 0, null))
        } else {
            visiblePreviousPages.forEach { page ->
                add(
                    ReaderPagerItem(
                        chapterIndex = currentChapterIndex - 1,
                        pageIndex = page.index,
                        pageCount = previousPages.size,
                        page = page,
                    ),
                )
            }
        }
    }
    if (currentPages.isEmpty()) {
        add(
            ReaderPagerItem(
                chapterIndex = currentChapterIndex,
                pageIndex = currentPlaceholderPageIndex,
                pageCount = 0,
                page = null,
            ),
        )
    } else {
        currentPages.forEach { page ->
            add(ReaderPagerItem(currentChapterIndex, page.index, currentPages.size, page))
        }
    }
    if (hasNext) {
        val visibleNextPages = nextPages.take(neighbourLeafCount.coerceAtLeast(1))
        if (visibleNextPages.isEmpty()) {
            add(ReaderPagerItem(currentChapterIndex + 1, 0, 0, null))
        } else {
            visibleNextPages.forEach { page ->
                add(
                    ReaderPagerItem(
                        chapterIndex = currentChapterIndex + 1,
                        pageIndex = page.index,
                        pageCount = nextPages.size,
                        page = page,
                    ),
                )
            }
        }
    }
    val lastChapter = (currentChapterIndex + PAGER_NAVIGATION_RADIUS)
        .coerceAtMost(chapterCount - 1)
    if (currentChapterIndex + 2 <= lastChapter) {
        for (chapterIndex in currentChapterIndex + 2..lastChapter) {
            add(ReaderPagerItem(chapterIndex, 0, 0, null))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderControls(
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
private fun ReaderControlIconButton(
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

private fun Color.contrastRatio(other: Color): Float {
    val first = luminance()
    val second = other.luminance()
    val lighter = maxOf(first, second)
    val darker = minOf(first, second)
    return (lighter + .05f) / (darker + .05f)
}

/** Chooses the WCAG contrast winner so arbitrary custom accent colors remain legible. */
private fun Color.highContrastContentColor(): Color {
    val relativeLuminance = luminance()
    val blackContrast = (relativeLuminance + .05f) / .05f
    val whiteContrast = 1.05f / (relativeLuminance + .05f)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
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
    val currentVolume = state.chapters.getOrNull(currentIndex)?.volumeIndex
    val expandedVolumes = remember(state.book?.uuid) {
        mutableStateMapOf<Int, Boolean>().apply {
            currentVolume?.let { this[it] = true }
        }
    }
    val directoryRows = remember(state.chapters, expandedVolumes.toMap()) {
        buildDirectoryRows(state.chapters, expandedVolumes)
    }
    val currentRowIndex = directoryRows.indexOfFirst { row ->
        row is DirectoryRow.ChapterRow && row.index == currentIndex
    }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentRowIndex)
    LaunchedEffect(currentIndex, directoryView) {
        if (directoryView == DirectoryView.CHAPTERS && state.chapters.isNotEmpty()) {
            currentVolume?.let { expandedVolumes[it] = true }
            val targetRows = buildDirectoryRows(state.chapters, expandedVolumes)
            val target = targetRows.indexOfFirst { row ->
                row is DirectoryRow.ChapterRow && row.index == currentIndex
            }.coerceAtLeast(0)
            if (targetRows.isNotEmpty()) listState.scrollToItem(target)
        }
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
                            end = if (directoryRows.size >= FAST_SCROLLER_MIN_CHAPTERS) {
                                KixyuSize.directoryFastScrollerWidth
                            } else 0.dp,
                        ),
                        state = listState,
                    ) {
                        items(
                            count = directoryRows.size,
                            key = { rowIndex -> directoryRows[rowIndex].key },
                        ) { rowIndex ->
                            when (val row = directoryRows[rowIndex]) {
                                is DirectoryRow.Volume -> {
                                    val expanded = expandedVolumes[row.index] == true
                                    val hasBookmark = row.chapterIds.any { it in bookmarkedChapterIds }
                                    KixyuListRow(
                                        title = row.title,
                                        supportingText = "${row.chapterCount} 章",
                                        highlighted = hasBookmark,
                                        onClick = { expandedVolumes[row.index] = !expanded },
                                        leading = {
                                            Icon(
                                                if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                                if (expanded) "收起" else "展开",
                                            )
                                        },
                                        trailing = {
                                            if (hasBookmark) Icon(Icons.Filled.Bookmark, "本卷有书签", tint = MaterialTheme.colorScheme.primary)
                                        },
                                        modifier = if (isMiuix) Modifier.padding(
                                            horizontal = KixyuSpacing.medium,
                                            vertical = KixyuSpacing.extraSmall,
                                        ) else Modifier,
                                    )
                                }
                                is DirectoryRow.ChapterRow -> {
                                    val chapter = state.chapters[row.index]
                                    val current = row.index == state.chapterIndex
                                    val hasBookmark = chapter.id in bookmarkedChapterIds
                                    KixyuListRow(
                                        title = chapter.title,
                                        selected = current,
                                        highlighted = hasBookmark,
                                        onClick = { selectChapter(row.index) },
                                        leading = {
                                            Box(Modifier.size(KixyuSize.icon), contentAlignment = Alignment.Center) {
                                                if (current) Icon(
                                                    Icons.Outlined.PlayArrow,
                                                    null,
                                                    Modifier.size(KixyuSize.icon),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                        trailing = {
                                            if (hasBookmark) Icon(
                                                Icons.Filled.Bookmark,
                                                "本章有书签",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                        modifier = if (isMiuix) Modifier.padding(
                                            start = KixyuSpacing.extraLarge,
                                            end = KixyuSpacing.medium,
                                            top = KixyuSpacing.extraSmall,
                                            bottom = KixyuSpacing.extraSmall,
                                        ) else Modifier.padding(start = KixyuSpacing.large),
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                    }
                    if (directoryRows.size >= FAST_SCROLLER_MIN_CHAPTERS) {
                        DirectoryFastScroller(
                            itemCount = directoryRows.size,
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

private sealed interface DirectoryRow {
    val key: String

    data class Volume(
        val index: Int,
        val title: String,
        val chapterCount: Int,
        val chapterIds: Set<Long>,
    ) : DirectoryRow {
        override val key = "volume:$index:$title:${chapterIds.minOrNull()}"
    }

    data class ChapterRow(val index: Int, val id: Long) : DirectoryRow {
        override val key = "chapter:$id"
    }
}

private fun buildDirectoryRows(
    chapters: List<Chapter>,
    expandedVolumes: Map<Int, Boolean>,
): List<DirectoryRow> {
    if (chapters.none { !it.volumeTitle.isNullOrBlank() }) {
        return chapters.mapIndexed { index, chapter -> DirectoryRow.ChapterRow(index, chapter.id) }
    }
    // Older indexes may contain a standalone spine entry for a volume cover in addition to
    // the volume group read from NAV/NCX. Keep its source index intact, but do not render it
    // as a second identically named directory row.
    val normalizedVolumeTitles = chapters.mapNotNullTo(hashSetOf()) { chapter ->
        chapter.volumeTitle?.normalizedDirectoryTitle()
    }
    return buildList {
        var position = 0
        while (position < chapters.size) {
            val chapter = chapters[position]
            val volumeIndex = chapter.volumeIndex
            val volumeTitle = chapter.volumeTitle
            if (volumeIndex == null || volumeTitle.isNullOrBlank()) {
                if (chapter.title.normalizedDirectoryTitle() !in normalizedVolumeTitles) {
                    add(DirectoryRow.ChapterRow(position, chapter.id))
                }
                position++
                continue
            }
            val start = position
            while (position < chapters.size && chapters[position].volumeIndex == volumeIndex) position++
            val volumeChapters = chapters.subList(start, position)
            add(
                DirectoryRow.Volume(
                    index = volumeIndex,
                    title = volumeTitle,
                    chapterCount = volumeChapters.size,
                    chapterIds = volumeChapters.mapTo(hashSetOf(), Chapter::id),
                ),
            )
            if (expandedVolumes[volumeIndex] == true) {
                volumeChapters.forEachIndexed { offset, item ->
                    add(DirectoryRow.ChapterRow(start + offset, item.id))
                }
            }
        }
    }
}

private fun String.normalizedDirectoryTitle(): String =
    trim().replace(Regex("[\\s　]+"), "").trim('：', ':', '-', '—')

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
private fun ReaderSettingsSheet(
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

private fun String.colorOr(fallback: Color) = runCatching { Color(parseColor(this)) }.getOrDefault(fallback)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Pagination inputs are almost always unique remainders. Caching their TextLayoutResult objects
// retains large native buffers without producing useful hits, especially for malformed EPUB text.
private const val READER_TEXT_MEASURE_CACHE_SIZE = 0
private const val PAGER_NAVIGATION_RADIUS = 10
private const val CHAPTER_LOADING_INDICATOR_DELAY_MILLIS = 180L
private const val READER_OVERLAY_SETTLE_MILLIS = 320L
private const val SYSTEM_BAR_GESTURE_HIDE_MILLIS = 2_000L
private const val READER_CONTROL_ACCENT_MIX = .18f
private const val READER_CONTROL_DISABLED_ACCENT_MIX = .1f
private const val MIN_ICON_CONTRAST = 3f
