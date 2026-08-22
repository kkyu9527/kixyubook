package com.kixyu9527.kixyubook.feature.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.reader.engine.ReaderRenderPalette

internal enum class ReaderSheet { DIRECTORY, THEME, LAYOUT, INFORMATION }

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
    bookUuid: String,
    initialSettings: ReaderSettings = ReaderSettings(),
    onExit: () -> Unit,
    onManageCorrections: () -> Unit,
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
        LoadedReaderRoute(
            bookUuid = bookUuid,
            initialSettings = initialSettings,
            onExit = onExit,
            onManageCorrections = onManageCorrections,
        )
    }
}

@Composable
private fun LoadedReaderRoute(
    bookUuid: String,
    initialSettings: ReaderSettings,
    onExit: () -> Unit,
    onManageCorrections: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel<ReaderViewModel, ReaderViewModel.Factory>(
        key = bookUuid,
        creationCallback = { factory -> factory.create(bookUuid) },
    ),
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
        returnFromSearchResult = viewModel::returnFromSearchResult,
        clearSearch = viewModel::clearSearch,
        chapterRendered = viewModel::chapterRendered,
        setPageInteractionActive = viewModel::setPageInteractionActive,
        prioritizeNextChapter = viewModel::prioritizeNextChapter,
        addFont = {
            fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
        },
        deleteFont = viewModel::deleteFont,
        saveCorrection = viewModel::saveParagraphCorrection,
        deleteCorrection = viewModel::deleteCorrection,
        onManageCorrections = onManageCorrections,
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
