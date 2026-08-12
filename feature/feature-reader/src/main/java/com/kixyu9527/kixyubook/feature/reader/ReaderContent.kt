package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.PageMode
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.reader.engine.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

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
internal fun ReaderContent(
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
    onTextActionTarget: (ReaderTextActionTarget) -> Unit,
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
                    onTextActionTarget = onTextActionTarget,
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
                    onTextActionTarget,
                )
            }
        }
    }
}
