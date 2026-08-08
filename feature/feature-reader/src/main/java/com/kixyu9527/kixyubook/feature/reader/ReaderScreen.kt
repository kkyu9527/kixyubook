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

internal enum class ReaderSheet { DIRECTORY, THEME, LAYOUT, SETTINGS }

/** All floating reader controls share one enter/exit clock and transform. */
@Composable
internal fun ReaderControlVisibility(
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
    LaunchedEffect(readerResumed) {
        viewModel.setReaderVisible(readerResumed)
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.setReaderVisible(false)
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
internal fun ReaderLoadingIndicator(palette: ReaderRenderPalette) {
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
    savePosition: (Int, Int, Boolean) -> Unit,
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
    val windowSizeClass = kixyuWindowSizeClass()
    val directoryAsSidePanel = windowSizeClass.supportsTwoPane
    val directoryPanelVisible = directoryAsSidePanel && sheet == ReaderSheet.DIRECTORY
    val directoryPanelProgress = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    var directoryPanelComposed by remember { mutableStateOf(false) }
    LaunchedEffect(directoryPanelVisible) {
        if (directoryPanelVisible) {
            directoryPanelComposed = true
            directoryPanelProgress.animateTo(1f, kixyuPopupSpring())
        } else if (directoryPanelComposed) {
            directoryPanelProgress.animateTo(0f, kixyuPopupSpring())
            directoryPanelComposed = false
        }
    }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = readerPalette(state.settings, systemDark)
    val overlayVisible = controls || menu || toolsMenu || searchVisible ||
        bookInfoVisible || sheet != null || directoryPanelComposed
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
    LaunchedEffect(searchVisible, sheet, bookInfoVisible) {
        if (!searchVisible && sheet == null && !bookInfoVisible) focusRequester.requestFocus()
    }
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
    PredictiveBackHandler(
        enabled = directoryAsSidePanel && sheet == ReaderSheet.DIRECTORY,
    ) { events ->
        try {
            events.collect { backProgress = it.progress }
            sheet = null
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
                    val isSearchShortcut = event.isCtrlPressed && event.key == Key.F
                    val isPageShortcut = event.key == Key.DirectionLeft ||
                        event.key == Key.DirectionRight ||
                        event.key == Key.PageUp ||
                        event.key == Key.PageDown ||
                        event.key == Key.Spacebar
                    val handled = when {
                        isSearchShortcut -> true
                        event.key == Key.Escape &&
                            (searchVisible || sheet != null || controls || menu || toolsMenu) -> true
                        isVolumeKey -> state.settings.volumeKeyPageTurn
                        isPageShortcut -> !searchVisible && sheet == null && !bookInfoVisible &&
                            !controls && !menu && !toolsMenu
                        else -> false
                    }
                    if (!handled) return@onPreviewKeyEvent false
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true

                    when {
                        isSearchShortcut -> {
                            searchVisible = true
                            controls = false
                            menu = false
                            toolsMenu = false
                        }
                        event.key == Key.Escape -> {
                            when {
                                searchVisible -> {
                                    searchVisible = false
                                    clearSearch()
                                }
                                sheet != null -> sheet = null
                                toolsMenu -> toolsMenu = false
                                menu -> menu = false
                                controls -> controls = false
                            }
                        }
                        else -> {
                            controls = false
                            menu = false
                            toolsMenu = false
                            val direction = when (event.key) {
                                Key.VolumeUp, Key.DirectionLeft, Key.PageUp -> -1
                                Key.Spacebar -> if (event.isShiftPressed) -1 else 1
                                else -> 1
                            }
                            volumeTurns.tryEmit(direction)
                        }
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
            show = sheet != null && !(directoryAsSidePanel && sheet == ReaderSheet.DIRECTORY),
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
        if (directoryPanelVisible || directoryPanelComposed) {
            val panelProgress = directoryPanelProgress.value
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            Color.Black.copy(
                                alpha = .28f * panelProgress * (1f - backProgress),
                            ),
                        )
                        .clickable(
                            enabled = directoryPanelVisible,
                            onClick = { sheet = null },
                        ),
                )
                Box(
                    Modifier.align(Alignment.CenterStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(KixyuSpacing.medium),
                ) {
                    KixyuInteractivePopupSurface(
                        modifier = Modifier
                            .widthIn(min = 360.dp, max = 480.dp)
                            .fillMaxHeight()
                            .graphicsLayer {
                                // MIUIX bottom sheets translate by their complete measured height.
                                // Apply the same progress horizontally and preserve predictive back.
                                translationX = -size.width * (
                                    (1f - panelProgress) + backProgress * panelProgress
                                )
                                alpha = 1f - backProgress * .35f
                            },
                    ) {
                        DirectorySheet(
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
                            expandedLayout = true,
                        )
                    }
                }
            }
        }
        BookInfoDialog(
            show = bookInfoVisible,
            book = state.book,
            dismiss = { bookInfoVisible = false },
        )
    }
}

@Composable
private fun rememberReaderFoldingFeature(): FoldingFeature? {
    val activity = LocalContext.current.findActivity()
    var foldingFeature by remember(activity) { mutableStateOf<FoldingFeature?>(null) }
    LaunchedEffect(activity) {
        if (activity == null) {
            foldingFeature = null
            return@LaunchedEffect
        }
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .collectLatest { layoutInfo ->
                foldingFeature = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull { feature ->
                        feature.orientation == FoldingFeature.Orientation.VERTICAL &&
                            (feature.isSeparating ||
                                feature.state == FoldingFeature.State.HALF_OPENED)
                    }
            }
    }
    return foldingFeature
}

@OptIn(kotlinx.coroutines.FlowPreview::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderContent(
    state: ReaderUiState,
    palette: ReaderRenderPalette,
    savePosition: (Int, Int, Boolean) -> Unit,
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
    val foldingFeature = rememberReaderFoldingFeature()
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
        val physicalViewportHeight = maxHeight.value
        val safeViewportHeight = (physicalViewportHeight - topInsetDp - bottomInsetDp).coerceAtLeast(1f)
        val twoPageSpread = maxWidth > maxHeight &&
            (maxWidth >= 840.dp || foldingFeature != null)
        val physicalHingeWidth = with(density) {
            foldingFeature?.bounds?.width()?.toDp() ?: 0.dp
        }
        val spreadGutter = maxOf(KixyuSize.readerSpreadGutter, physicalHingeWidth)
        val pageViewportWidth = if (twoPageSpread && state.settings.pageMode != PageMode.SCROLL) {
            ((maxWidth - spreadGutter) / 2).value
        } else {
            maxWidth.value
        }
        // Paged mode is measured against one physical leaf. Scroll mode always owns the complete
        // viewport; tablet spreads render two leaves in one Pager item. Very wide text still
        // retains a bounded, readable line measure.
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
                        savePosition(position, 0, complete)
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
                    fullPageViewportHeightDp = physicalViewportHeight,
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
            Box(Modifier.fillMaxSize()) {
                PagedReader(
                    state, chapter, spec, palette, savePosition, moveChapterFromPage,
                    middleTap, dismissControls, volumeTurns, paginationCoordinator, paginationMeasurer,
                    chapterRendered, setPageInteractionActive, resourcePriorityActive, twoPageSpread,
                    spreadGutter,
                    topInsetDp,
                    bottomInsetDp,
                    physicalViewportHeight,
                )
            }
        }
    }
}
