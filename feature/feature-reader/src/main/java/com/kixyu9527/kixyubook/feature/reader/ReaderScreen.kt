package com.kixyu9527.kixyubook.feature.reader

import android.content.Context
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.focusable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowSizeClass
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuGlassSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuOverlayHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPopupSpring
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuGlassBackdrop
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import com.kixyu9527.kixyubook.core.reader.engine.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReaderScreen(
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
    returnFromSearchResult: () -> Unit,
    clearSearch: () -> Unit,
    chapterRendered: (Int) -> Unit,
    setPageInteractionActive: (Boolean) -> Unit,
    prioritizeNextChapter: (Int) -> Unit,
    addFont: () -> Unit,
    deleteFont: (UserFont) -> Unit,
    saveCorrection: (Int, Int, String, String) -> Unit,
    deleteCorrection: (String) -> Unit,
    onManageCorrections: () -> Unit,
) {
    var controls by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var toolsMenu by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var bookInfoVisible by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    val returnFromSettingsSheet: () -> Unit = {
        sheet = null
        controls = true
        menu = true
        toolsMenu = false
    }
    val predictiveBackState = rememberReaderPredictiveBackState()
    val controlsBackProgress = predictiveBackState.progressFor(ReaderPredictiveBackTarget.CONTROLS)
    val popupBackProgress = predictiveBackState.progressFor(ReaderPredictiveBackTarget.POPUP_MENU)
    val searchBackProgress = predictiveBackState.progressFor(ReaderPredictiveBackTarget.SEARCH)
    val sheetBackProgress = predictiveBackState.progressFor(ReaderPredictiveBackTarget.SHEET)
    val bookInfoBackProgress = predictiveBackState.progressFor(ReaderPredictiveBackTarget.BOOK_INFO)
    val volumeTurns = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var textActionTarget by remember(state.chapter?.id) {
        mutableStateOf<ReaderTextActionTarget?>(null)
    }
    var correctionEditorTarget by remember(state.chapter?.id) {
        mutableStateOf<ReaderTextActionTarget?>(null)
    }
    var exitRequested by remember { mutableStateOf(false) }
    var retainedSheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var showChapterLoading by remember { mutableStateOf(false) }
    var pageInteractionActive by remember { mutableStateOf(false) }
    var brightnessPreview by remember { mutableStateOf<Float?>(null) }
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
    val readerBackdrop = rememberKixyuNavigationBackdrop(palette.background)
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
    LaunchedEffect(sheet) {
        if (sheet != ReaderSheet.THEME) brightnessPreview = null
    }
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
    val readerWindow = context.findActivity()?.window
    DisposableEffect(readerWindow) {
        val previousBrightness = readerWindow?.attributes?.screenBrightness
        onDispose {
            if (readerWindow != null && previousBrightness != null) {
                readerWindow.attributes = readerWindow.attributes.apply {
                    screenBrightness = previousBrightness
                }
            }
        }
    }
    SideEffect {
        readerWindow?.let { window ->
            window.attributes = window.attributes.apply {
                screenBrightness = brightnessPreview?.coerceIn(.05f, 1f)
                    ?: if (state.settings.brightnessMode == ReaderBrightnessMode.MANUAL) {
                        state.settings.brightness.coerceIn(.05f, 1f)
                    } else {
                        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }
        }
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
    val predictiveBackTarget = when {
        bookInfoVisible -> ReaderPredictiveBackTarget.BOOK_INFO
        sheet != null -> ReaderPredictiveBackTarget.SHEET
        searchVisible -> ReaderPredictiveBackTarget.SEARCH
        menu || toolsMenu -> ReaderPredictiveBackTarget.POPUP_MENU
        controls -> ReaderPredictiveBackTarget.CONTROLS
        state.searchResults.isNotEmpty() -> ReaderPredictiveBackTarget.SEARCH_RESULTS
        else -> null
    }
    ReaderPredictiveBackHandler(
        target = predictiveBackTarget,
        state = predictiveBackState,
        onBack = { target ->
            when (target) {
                ReaderPredictiveBackTarget.BOOK_INFO -> bookInfoVisible = false
                ReaderPredictiveBackTarget.SHEET -> {
                    if (sheet in READER_SETTINGS_SHEETS) {
                        returnFromSettingsSheet()
                    } else {
                        sheet = null
                    }
                }
                ReaderPredictiveBackTarget.SEARCH -> {
                    searchVisible = false
                    clearSearch()
                }
                ReaderPredictiveBackTarget.POPUP_MENU -> {
                    toolsMenu = false
                    menu = false
                }
                ReaderPredictiveBackTarget.CONTROLS -> controls = false
                ReaderPredictiveBackTarget.SEARCH_RESULTS -> clearSearch()
            }
        },
    )

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
        CompositionLocalProvider(LocalKixyuGlassBackdrop provides readerBackdrop) {
        CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(palette.accent, palette.accent.copy(alpha = .32f))) {
            ReaderSelectionToolbar(
                dismissKey = state.chapterIndex to state.currentPosition,
                onCorrectParagraph = {
                    textActionTarget?.let { target ->
                        correctionEditorTarget = target
                        controls = false
                        menu = false
                        toolsMenu = false
                    }
                },
            ) {
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
                            (searchVisible || sheet != null || bookInfoVisible || controls || menu || toolsMenu) -> true
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
                                bookInfoVisible -> bookInfoVisible = false
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
            Box(
                modifier = Modifier.fillMaxSize().then(
                    if (state.settings.glassEffectEnabled) {
                        Modifier.kixyuNavigationBackdrop(readerBackdrop)
                    } else {
                        Modifier
                    },
                ),
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
                    state.error != null -> Text(
                        state.error,
                        color = palette.body,
                        modifier = Modifier.align(Alignment.Center),
                    )
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
                        prioritizeNextChapter = prioritizeNextChapter,
                        // A page drag needs the already-started previous/next page layouts. Only
                        // overlays may cancel pagination; the drag still pauses unrelated EPUB work
                        // through setPageInteractionActive above.
                        resourcePriorityActive = overlayAnimationPriority,
                        onTextActionTarget = { textActionTarget = it },
                    )
                }
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
                visible = controls,
                menuVisible = menu,
                toolsMenuVisible = toolsMenu,
                controlsBackProgress = controlsBackProgress,
                popupBackProgress = popupBackProgress,
                bookTitle = state.book?.title.orEmpty().takeIf { state.searchResults.isEmpty() }.orEmpty(),
                accentColor = palette.accent,
                backgroundColor = palette.background,
                backdrop = readerBackdrop,
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
        }

        correctionEditorTarget?.let { target ->
            val chapterKey = state.chapters.firstOrNull { it.index == target.chapterIndex }?.chapterKey
            val existing = state.corrections.firstOrNull {
                it.chapterKey == chapterKey && it.paragraphIndex == target.paragraphIndex &&
                    it.status != TextCorrectionStatus.UNRESOLVED
            }
            CorrectionEditDialog(
                original = existing?.exactText ?: target.text,
                initialReplacement = existing?.replacementText ?: target.text,
                existing = existing,
                onDismiss = { correctionEditorTarget = null },
                onSave = { replacement ->
                    saveCorrection(target.chapterIndex, target.paragraphIndex, target.text, replacement)
                    correctionEditorTarget = null
                },
                onDelete = existing?.let { correction ->
                    {
                        deleteCorrection(correction.uuid)
                        correctionEditorTarget = null
                    }
                },
                onManageAll = {
                    correctionEditorTarget = null
                    onManageCorrections()
                },
            )
        }

        ReaderSearchOverlay(
            visible = searchVisible,
            progress = searchBackProgress,
            state = state,
            onDismiss = {
                searchVisible = false
                clearSearch()
            },
            onSearch = search,
            onMove = moveSearchResult,
            onReturn = returnFromSearchResult,
            onSelect = { index ->
                selectSearchResult(index)
                controls = false
                menu = false
                toolsMenu = false
            },
        )

        val activeSheet = sheet ?: retainedSheet
        ReaderFloatingSheet(
            show = sheet != null && !(directoryAsSidePanel && sheet == ReaderSheet.DIRECTORY),
            progress = sheetBackProgress,
            onDismissRequest = {
                if (sheet in READER_SETTINGS_SHEETS) {
                    returnFromSettingsSheet()
                } else {
                    sheet = null
                }
            },
            backdrop = readerBackdrop,
            maxContentWidth = if (activeSheet == ReaderSheet.DIRECTORY) {
                com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize.sheetContentMaxWidth
            } else {
                com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize.readerSettingsSheetMaxWidth
            },
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
                ReaderSheet.THEME -> ThemeSheet(
                    state.settings,
                    updateSettings,
                    previewBrightness = { brightnessPreview = it },
                    onBack = returnFromSettingsSheet,
                )
                ReaderSheet.LAYOUT -> LayoutSheet(
                    state,
                    updateSettings,
                    addFont,
                    deleteFont,
                    onBack = returnFromSettingsSheet,
                )
                ReaderSheet.INFORMATION -> ReaderInformationSheet(
                    state.settings,
                    updateSettings,
                    onBack = returnFromSettingsSheet,
                )
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
                                alpha = .28f * panelProgress * (1f - sheetBackProgress),
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
                    KixyuGlassSurface(
                        backdrop = readerBackdrop,
                        modifier = Modifier
                            .widthIn(min = 360.dp, max = 480.dp)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent()
                                }
                            }
                            .graphicsLayer {
                                // MIUIX bottom sheets translate by their complete measured height.
                                // Apply the same progress horizontally and preserve predictive back.
                                translationX = -size.width * (
                                    (1f - panelProgress) + sheetBackProgress * panelProgress
                                )
                                alpha = 1f - sheetBackProgress * .35f
                            },
                        fallbackContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
            progress = bookInfoBackProgress,
            backdrop = readerBackdrop,
            dismiss = { bookInfoVisible = false },
        )
        }
    }
}

private val READER_SETTINGS_SHEETS = setOf(
    ReaderSheet.THEME,
    ReaderSheet.LAYOUT,
    ReaderSheet.INFORMATION,
)
