package com.kixyu9527.kixyubook.feature.reader
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuOperationHost

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupBackdropEffect
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import kotlinx.coroutines.delay

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
        ReaderEntrySurface(initialSettings, stage = null, format = null)
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
    val position by viewModel.positionState.collectAsStateWithLifecycle()
    val contentState by viewModel.contentState.collectAsStateWithLifecycle()
    // A chapter-navigation failure keeps the current chapter on screen, so the full-screen failure
    // surface never shows. Surface it transiently instead, then clear it.
    val errorContext = LocalContext.current
    LaunchedEffect(state.error, state.chapter) {
        val message = state.error
        if (message != null && state.chapter != null) {
            Toast.makeText(errorContext, message, Toast.LENGTH_SHORT).show()
            viewModel.clearTransientError()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var readerResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFont(it.toString()) }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> readerResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    readerResumed = false
                    viewModel.checkpointReadingProgress()
                }
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
            viewModel.checkpointReadingProgress()
            viewModel.setReaderVisible(false)
            viewModel.setReadingActive(false)
            viewModel.finishSession()
        }
    }
    val renderedState = if (state.settingsLoaded) state else state.copy(settings = initialSettings)
    if (state.loading) {
        ReaderEntrySurface(
            settings = renderedState.settings,
            stage = state.loadStage,
            format = state.book?.format,
        )
        return
    }
    if (state.chapter == null && state.error != null) {
        ReaderLoadFailureSurface(
            settings = renderedState.settings,
            message = state.error.orEmpty(),
            onRetry = viewModel::retryInitialLoad,
            onExit = onExit,
        )
        return
    }
    KixyuOperationHost(viewModel.operations) {
        ReaderScreen(
            state = renderedState,
            position = position,
            contentState = if (state.settingsLoaded) contentState else {
                contentState.copy(settings = initialSettings)
            },
            readerContentReady = state.settingsLoaded,
            onExit = onExit,
            moveChapter = viewModel::moveChapter,
            moveChapterFromPage = viewModel::moveChapterFromPage,
            settlePage = viewModel::settlePage,
            jumpChapter = viewModel::jumpToChapter,
            jumpPosition = viewModel::requestLocation,
            savePosition = viewModel::savePosition,
            updateSettings = viewModel::updateSettings,
            setBookSettingsEnabled = viewModel::setBookSettingsEnabled,
            addBookmark = viewModel::addBookmark,
            deleteBookmark = viewModel::deleteBookmark,
            search = viewModel::search,
            selectSearchResult = viewModel::selectSearchResult,
            moveSearchResult = viewModel::moveSearchResult,
            moveSearchMatch = viewModel::moveSearchMatch,
            moveSearchResultPage = viewModel::moveSearchResultPage,
            returnFromSearchResult = viewModel::returnFromSearchResult,
            navigateHistoryBack = viewModel::navigateHistoryBack,
            navigateHistoryForward = viewModel::navigateHistoryForward,
            clearSearch = viewModel::clearSearch,
            clearSearchHistory = viewModel::clearSearchHistory,
            chapterRendered = viewModel::chapterRendered,
            setPageInteractionActive = viewModel::setPageInteractionActive,
            prioritizeAdjacentChapter = viewModel::prioritizeAdjacentChapter,
            addFont = {
                fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
            },
            deleteFont = viewModel::deleteFont,
            saveCorrection = viewModel::saveParagraphCorrection,
            deleteCorrection = viewModel::deleteCorrection,
            saveHighlight = viewModel::saveParagraphHighlight,
            saveUnderline = viewModel::saveParagraphUnderline,
            saveNote = viewModel::saveParagraphNote,
            updateAnnotationNote = viewModel::updateAnnotationNote,
            deleteAnnotation = viewModel::deleteAnnotation,
            openDocumentLink = viewModel::openEpubLink,
            closeFootnote = viewModel::closeEpubFootnote,
            onManageCorrections = onManageCorrections,
        )
    }
}

/**
 * The navigation transition only needs an opaque destination surface. Keeping this deliberately
 * free of Scaffold, overlay hosts, focus, insets mutation and text measurement prevents Reader's
 * first composition from consuming the same frames as the horizontal route animation. The
 * ViewModel continues loading the current chapter while this surface is visible.
 */
@Composable
private fun ReaderEntrySurface(
    settings: ReaderSettings,
    stage: ReaderLoadStage?,
    format: BookFormat?,
) {
    val palette = readerPalette(settings, androidx.compose.foundation.isSystemInDarkTheme())
    var showSlowStatus by remember(stage) { mutableStateOf(false) }
    LaunchedEffect(stage) {
        if (stage == null) return@LaunchedEffect
        delay(READER_SLOW_OPEN_STATUS_DELAY_MILLIS)
        showSlowStatus = true
    }
    Box(
        modifier = Modifier.fillMaxSize().background(palette.background),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = showSlowStatus && stage != null,
            enter = fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)),
            exit = fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)),
            modifier = Modifier.padding(bottom = 32.dp),
        ) {
            Text(
                text = readerLoadStageLabel(stage, format),
                color = palette.secondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun readerLoadStageLabel(stage: ReaderLoadStage?, format: BookFormat?): String = when (stage) {
    ReaderLoadStage.OPENING_BOOK -> stringResource(R.string.reader_opening_book)
    ReaderLoadStage.READING_CONTENT -> if (format == BookFormat.EPUB) {
        stringResource(R.string.reader_parsing_epub)
    } else {
        stringResource(R.string.reader_preparing_text)
    }
    ReaderLoadStage.PAGINATING_FIRST_PAGE -> stringResource(R.string.reader_preparing_first_page)
    null -> ""
}

@Composable
private fun ReaderLoadFailureSurface(
    settings: ReaderSettings,
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val palette = readerPalette(settings, androidx.compose.foundation.isSystemInDarkTheme())
    Box(
        modifier = Modifier.fillMaxSize().background(palette.background).padding(KixyuSpacing.large),
        contentAlignment = Alignment.Center,
    ) {
        KixyuPopupSurface(
            modifier = Modifier.widthIn(max = 420.dp),
            shadowElevation = KixyuSpacing.extraSmall,
            backdropEffect = KixyuPopupBackdropEffect.SURFACE_ONLY,
        ) {
            Column(
                modifier = Modifier.padding(KixyuSpacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                Text(
                    text = stringResource(R.string.reader_open_failed),
                    color = palette.title,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = message,
                    color = palette.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                    KixyuTextButton(
                        text = stringResource(R.string.reader_return_to_book),
                        onClick = onExit,
                    )
                    KixyuButton(
                        text = stringResource(R.string.reader_retry),
                        onClick = onRetry,
                    )
                }
            }
        }
    }
}

private const val READER_SLOW_OPEN_STATUS_DELAY_MILLIS = 900L
