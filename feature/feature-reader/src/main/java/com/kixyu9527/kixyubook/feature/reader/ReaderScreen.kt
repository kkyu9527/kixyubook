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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationBackTransitionActive
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuSystemBarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSystemBarPolicy
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import com.kixyu9527.kixyubook.core.reader.engine.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReaderScreen(
    state: ReaderUiState,
    position: ReaderPositionState,
    contentState: ReaderContentState,
    readerContentReady: Boolean,
    onExit: () -> Unit,
    moveChapter: (Int, Boolean) -> Unit,
    moveChapterFromPage: (Int, Int, Boolean) -> Unit,
    jumpChapter: (Int) -> Unit,
    jumpPosition: (Int, Int) -> Unit,
    savePosition: (Int, Int, Boolean, Int) -> Unit,
    updateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    addBookmark: () -> Unit,
    deleteBookmark: (String) -> Unit,
    search: (String, ReaderSearchScope) -> Unit,
    selectSearchResult: (Int) -> Unit,
    moveSearchResult: (Int) -> Unit,
    returnFromSearchResult: () -> Unit,
    navigateHistoryBack: () -> Unit,
    navigateHistoryForward: () -> Unit,
    clearSearch: () -> Unit,
    clearSearchHistory: () -> Unit,
    chapterRendered: (Int) -> Unit,
    setPageInteractionActive: (Boolean) -> Unit,
    prioritizeAdjacentChapter: (Int, Int) -> Unit,
    addFont: () -> Unit,
    deleteFont: (UserFont) -> Unit,
    saveCorrection: (Int, Int, String, String) -> Unit,
    deleteCorrection: (String) -> Unit,
    saveHighlight: (Int, Int, String, Int, Int) -> Unit,
    saveUnderline: (Int, Int, String, Int, Int) -> Unit,
    saveNote: (Int, Int, String, Int, Int, String) -> Unit,
    updateAnnotationNote: (String, String) -> Unit,
    deleteAnnotation: (String) -> Unit,
    openDocumentLink: (String) -> Unit,
    closeFootnote: () -> Unit,
    onManageCorrections: () -> Unit,
) {
    val readerPaneTitle = stringResource(R.string.reader_content_pane)
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
    val chapterTurns = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val view = LocalView.current
    val textInteraction = rememberReaderTextInteractionState()
    var correctionEditorTarget by remember(state.chapter?.id) {
        mutableStateOf<ReaderTextActionTarget?>(null)
    }
    var noteEditorTarget by remember(state.chapter?.id) {
        mutableStateOf<ReaderTextActionTarget?>(null)
    }
    var exitRequested by remember { mutableStateOf(false) }
    var retainedSheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var pageInteractionActive by remember { mutableStateOf(false) }
    var showSlowFirstPageStatus by remember { mutableStateOf(false) }
    var brightnessPreview by remember { mutableStateOf<Float?>(null) }
    var overlayAnimationPriority by remember { mutableStateOf(false) }
    var overlayMotionObserved by remember { mutableStateOf(false) }
    val navigationBackTransitionActive = LocalKixyuNavigationBackTransitionActive.current
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
    val chromeState = ReaderChromeState(
        controlsVisible = controls,
        menuVisible = menu,
        toolsMenuVisible = toolsMenu,
        searchVisible = searchVisible,
        bookInfoVisible = bookInfoVisible,
        sheet = sheet,
        directoryPanelComposed = directoryPanelComposed,
        hasSearchResults = state.searchResults.isNotEmpty(),
    )
    val overlayVisible = chromeState.overlayVisible
    val systemBars = readerSystemBarVisibility(
        showStatusBar = state.settings.showStatusBar,
        hideNavigationBar = state.settings.hideNavigationBar,
        overlayVisible = overlayVisible,
    )
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
    val resourcePriorityActive = pageInteractionActive || overlayVisible ||
        overlayAnimationPriority || navigationBackTransitionActive
    LaunchedEffect(resourcePriorityActive) {
        setPageInteractionActive(resourcePriorityActive)
    }
    DisposableEffect(Unit) {
        onDispose { setPageInteractionActive(false) }
    }
    LaunchedEffect(sheet) { sheet?.let { retainedSheet = it } }
    LaunchedEffect(state.loadStage) {
        showSlowFirstPageStatus = false
        if (state.loadStage == ReaderLoadStage.PAGINATING_FIRST_PAGE) {
            delay(900L)
            showSlowFirstPageStatus = true
        }
    }
    LaunchedEffect(sheet) {
        if (sheet != ReaderSheet.THEME) brightnessPreview = null
    }
    val systemBarHost = LocalKixyuSystemBarHost.current
    val systemBarOwner = remember { Any() }
    SideEffect {
        systemBarHost?.update(
            owner = systemBarOwner,
            value = KixyuSystemBarPolicy(
                statusBarVisible = systemBars.statusBarVisible,
                navigationBarVisible = systemBars.navigationBarVisible,
                useDarkIcons = palette.background.luminance() > .5f,
            ),
        )
    }
    DisposableEffect(systemBarHost, systemBarOwner) {
        onDispose { systemBarHost?.clear(systemBarOwner) }
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
    val predictiveBackTarget = chromeState.predictiveBackTarget()
    ReaderPredictiveBackHandler(
        target = predictiveBackTarget,
        state = predictiveBackState,
        onBack = { target ->
            when (target) {
                ReaderPredictiveBackTarget.BOOK_INFO -> bookInfoVisible = false
                ReaderPredictiveBackTarget.SHEET -> {
                    if (sheet.returnsToSettingsMenu()) {
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
        currentVisiblePageBookmark(state.bookmarks, chapter.id, position)
    }
    val exitReader: () -> Unit = {
        if (!exitRequested) {
            exitRequested = true
            onExit()
        }
    }
    KixyuOverlayHost(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalKixyuGlassBackdrop provides readerBackdrop) {
        CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(palette.accent, palette.accent.copy(alpha = .32f))) {
            ReaderTextInteractionHost(
                state = textInteraction,
                dismissKey = state.chapterIndex to position.paragraphIndex,
                onCorrectText = {
                    textInteraction.target?.let { target ->
                        correctionEditorTarget = target
                        controls = false
                        menu = false
                        toolsMenu = false
                    }
                },
                onHighlightText = {
                    textInteraction.target?.let { target ->
                        saveHighlight(
                            target.chapterIndex, target.paragraphIndex, target.text,
                            target.selectedStart, target.selectedEnd,
                        )
                    }
                },
                onUnderlineText = {
                    textInteraction.target?.let { target ->
                        saveUnderline(
                            target.chapterIndex, target.paragraphIndex, target.text,
                            target.selectedStart, target.selectedEnd,
                        )
                    }
                },
                onNoteText = {
                    textInteraction.target?.let { target ->
                        noteEditorTarget = target
                        controls = false
                        menu = false
                        toolsMenu = false
                    }
                },
            ) {
            Box(
            Modifier.fillMaxSize()
                .background(palette.background)
                .semantics { paneTitle = readerPaneTitle }
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
                    state.chapter != null -> ReaderContent(
                        state = contentState,
                        palette = palette,
                        savePosition = savePosition,
                        moveChapterFromPage = moveChapterFromPage,
                        middleTap = { controls = !controls; if (!controls) { menu = false; toolsMenu = false } },
                        dismissControls = { controls = false; menu = false; toolsMenu = false },
                        volumeTurns = volumeTurns,
                        chapterTurns = chapterTurns,
                        chapterRendered = chapterRendered,
                        setPageInteractionActive = { pageInteractionActive = it },
                        prioritizeAdjacentChapter = prioritizeAdjacentChapter,
                        // A page drag needs the already-started previous/next page layouts. Only
                        // overlays may cancel pagination; the drag still pauses unrelated EPUB work
                        // through setPageInteractionActive above.
                        resourcePriorityActive = overlayAnimationPriority,
                        onTextActionTarget = textInteraction::publishTarget,
                        onDocumentLink = openDocumentLink,
                    )
                }
                AnimatedVisibility(
                    visible = showSlowFirstPageStatus &&
                        state.loadStage == ReaderLoadStage.PAGINATING_FIRST_PAGE,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reader_preparing_first_page),
                        color = palette.secondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
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
                onPreviousChapter = {
                    if (state.settings.pageMode == PageMode.SCROLL) moveChapter(-1, false)
                    else chapterTurns.tryEmit(-1)
                },
                onNextChapter = {
                    if (state.settings.pageMode == PageMode.SCROLL) moveChapter(1, false)
                    else chapterTurns.tryEmit(1)
                },
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
                canNavigateBack = state.canNavigateBack,
                canNavigateForward = state.canNavigateForward,
                onNavigateBack = {
                    navigateHistoryBack()
                    toolsMenu = false
                },
                onNavigateForward = {
                    navigateHistoryForward()
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

        noteEditorTarget?.let { target ->
            val existing = state.annotations.firstOrNull {
                it.chapterIndex == target.chapterIndex && it.paragraphIndex == target.paragraphIndex &&
                    it.startOffset == target.selectedStart && it.endOffset == target.selectedEnd
            }
            AnnotationNoteDialog(
                excerpt = target.selectedText,
                initialNote = existing?.note.orEmpty(),
                onDismiss = { noteEditorTarget = null },
                onSave = { note ->
                    saveNote(
                        target.chapterIndex, target.paragraphIndex, target.text,
                        target.selectedStart, target.selectedEnd, note,
                    )
                    noteEditorTarget = null
                },
                onDelete = existing?.let { annotation ->
                    {
                        deleteAnnotation(annotation.uuid)
                        noteEditorTarget = null
                    }
                },
            )
        }

        state.epubFootnote?.let { footnote ->
            EpubFootnoteDialog(footnote = footnote, onDismiss = closeFootnote)
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
            onClearHistory = clearSearchHistory,
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
                if (sheet.returnsToSettingsMenu()) {
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
                    selectAnnotation = { annotation ->
                        sheet = null
                        controls = false
                        menu = false
                        toolsMenu = false
                        jumpPosition(annotation.chapterIndex, annotation.paragraphIndex)
                    },
                    deleteBookmark = deleteBookmark,
                    updateAnnotationNote = updateAnnotationNote,
                    deleteAnnotation = deleteAnnotation,
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
                            selectAnnotation = { annotation ->
                                sheet = null
                                controls = false
                                menu = false
                                toolsMenu = false
                                jumpPosition(annotation.chapterIndex, annotation.paragraphIndex)
                            },
                            deleteBookmark = deleteBookmark,
                            updateAnnotationNote = updateAnnotationNote,
                            deleteAnnotation = deleteAnnotation,
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

internal fun currentVisiblePageBookmark(
    bookmarks: List<Bookmark>,
    chapterId: Long,
    position: ReaderPositionState,
): Bookmark? = bookmarks.firstOrNull { bookmark ->
    bookmark.chapterId == chapterId &&
        bookmark.position in position.paragraphIndex..position.visibleEndParagraphIndex
}
