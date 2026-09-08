package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.reader.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.math.abs


@Composable
internal fun PagedReader(
    state: ReaderContentState,
    chapter: ReaderChapter,
    spec: ReaderLayoutSpec,
    palette: ReaderRenderPalette,
    savePosition: (Int, Int, Boolean, Int) -> Unit,
    settlePage: (ReaderPageDestination) -> Unit,
    middleTap: () -> Unit,
    dismissControls: () -> Unit,
    volumeTurns: SharedFlow<Int>,
    chapterTurns: SharedFlow<Int>,
    paginationCoordinator: ReaderPaginationCoordinator,
    paginationMeasurer: androidx.compose.ui.text.TextMeasurer,
    chapterRendered: (Int) -> Unit,
    setPageInteractionActive: (Boolean) -> Unit,
    resourcePriorityActive: Boolean,
    twoPageSpread: Boolean,
    prioritizeAdjacentChapter: (Int, Int) -> Unit,
    spreadGutter: Dp,
    topInsetDp: Float,
    bottomInsetDp: Float,
    physicalViewportHeightDp: Float,
    onTextActionTarget: (ReaderTextActionTarget) -> Unit,
    onDocumentLink: (String) -> Unit,
) {
    var retainedPage by remember(
        spec,
        state.fontPath,
        state.settings.showChapterTitle,
    ) { mutableStateOf<RetainedReaderPage?>(null) }
    var textSelectionActive by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // These are references to the three displayed chapter layouts, not another global cache.
    // A cache eviction must not withdraw a leaf already handed to Pager during role rotation.
    val measuredWindow = remember(
        spec, state.fontPath, state.settings.showChapterTitle, state.book?.contentHash,
        density.density, density.fontScale, layoutDirection,
    ) { java.util.IdentityHashMap<ReaderChapter, ReaderPaginationSnapshot>() }
    // Always finish the requested chapter first. EPUB pagination includes rich spans and image
    // blocks, so starting three layouts together made the visible chapter compete with prefetch.
    val pagination = rememberMeasuredReaderPages(
        chapter = chapter,
        contentHash = state.book?.contentHash.orEmpty(),
        spec = spec,
        fontPath = state.fontPath,
        showRegularChapterTitle = state.settings.showChapterTitle,
        coordinator = paginationCoordinator,
        measurer = paginationMeasurer,
        paused = resourcePriorityActive,
        minimumVisibleParagraphIndex = state.restorePosition,
        minimumVisibleCharOffset = state.restoreCharOffset,
        retainedSnapshot = measuredWindow[chapter],
    )
    val pages = pagination.pages
    LaunchedEffect(resourcePriorityActive) {
        paginationCoordinator.setPaused(resourcePriorityActive)
    }
    DisposableEffect(paginationCoordinator) {
        onDispose { paginationCoordinator.setPaused(false) }
    }
    // Opening the reader is content-first: the themed reading surface remains stable until the
    // first measured page arrives. A transient spinner made every cached book feel like a cold
    // start and competed visually with the navigation animation.
    if (pages.isEmpty() && retainedPage == null) {
        Box(Modifier.fillMaxSize().background(palette.background))
        return
    }
    LaunchedEffect(chapter.id, state.navigationVersion, pages) {
        if (pages.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        chapterRendered(state.navigationVersion)
    }
    val hasPrevious = state.chapterIndex > 0
    val hasNext = state.chapterIndex < state.chapters.lastIndex
    var criticalNeighbours by remember(chapter.id) { mutableStateOf(false) }
    LaunchedEffect(chapter.id, pages.isNotEmpty(), hasPrevious, hasNext) {
        if (pages.isNotEmpty()) {
            // Cross-chapter navigation is part of the ordinary page stream. Promote both sides
            // after the visible leaf is ready so opening at a saved location can move backward as
            // reliably as normal forward reading.
            criticalNeighbours = true
            if (hasPrevious) prioritizeAdjacentChapter(state.chapterIndex, -1)
            if (hasNext) prioritizeAdjacentChapter(state.chapterIndex, 1)
        }
    }
    val nextChapter = state.prefetchedChapters[state.chapterIndex + 1]
    val nextPagination = nextChapter?.let {
        rememberMeasuredReaderPages(
            chapter = it,
            contentHash = state.book?.contentHash.orEmpty(),
            spec = spec,
            fontPath = state.fontPath,
            showRegularChapterTitle = state.settings.showChapterTitle,
            coordinator = paginationCoordinator,
            measurer = paginationMeasurer,
            // Once parsing publishes the next chapter its first measured leaves are foreground
            // reader work, not a late speculative task. Reuse any in-flight layout in place.
            prefetch = !criticalNeighbours,
            paused = resourcePriorityActive,
            allowPartialResults = false,
            retainedSnapshot = measuredWindow[it],
        )
    } ?: ReaderPaginationSnapshot()
    val nextPages = nextPagination.pages
    val previousChapter = state.prefetchedChapters[state.chapterIndex - 1]
    val previousPagination = previousChapter?.let {
        rememberMeasuredReaderPages(
            chapter = it,
            contentHash = state.book?.contentHash.orEmpty(),
            spec = spec,
            fontPath = state.fontPath,
            showRegularChapterTitle = state.settings.showChapterTitle,
            coordinator = paginationCoordinator,
            measurer = paginationMeasurer,
            prefetch = !criticalNeighbours,
            paused = resourcePriorityActive,
            allowPartialResults = false,
            retainedSnapshot = measuredWindow[it],
        )
    } ?: ReaderPaginationSnapshot()
    val previousPages = previousPagination.pages
    SideEffect {
        // Identity lookup avoids hashing every paragraph on each reader recomposition.
        measuredWindow.keys.removeAll { it !== chapter && it !== previousChapter && it !== nextChapter }
        if (pagination.isComplete) measuredWindow[chapter] = pagination
        if (previousChapter != null && previousPagination.isComplete) measuredWindow[previousChapter] = previousPagination
        if (nextChapter != null && nextPagination.isComplete) measuredWindow[nextChapter] = nextPagination
    }
    val positions = remember { ReaderPositionManager() }
    // Keep one physical Pager alive across chapter changes. Its stable page keys let Compose retain
    // the page that crossed the boundary while the three-chapter window is recentered around it.
    // Recreating PagerState per chapter cancels a second gesture that starts immediately after the
    // first one settles, which is the root cause of rapid-swipe spring-back.
    val pagerWindow = remember(
        state.chapterIndex,
        state.restorePosition,
        state.restoreCharOffset,
        state.chapters.size,
        pages,
        previousPages,
        nextPages,
        pagination.isComplete,
        hasPrevious,
        hasNext,
        twoPageSpread,
        spreadGutter,
    ) {
        buildReaderPagerWindow(
            currentChapterIndex = state.chapterIndex,
            currentPages = pages,
            previousPages = previousPages,
            nextPages = nextPages,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            currentPagesComplete = pagination.isComplete,
            currentPlaceholderPageIndex = if (state.restorePosition > 0) Int.MIN_VALUE else 0,
            chapterCount = state.chapters.size,
        )
    }
    val pagerSpreads = remember(pagerWindow, twoPageSpread) {
        buildReaderPagerSpreads(pagerWindow, twoPageSpread)
    }
    val currentStart = pagerWindow.indexOfFirst {
        it.chapterIndex == state.chapterIndex
    }.coerceAtLeast(0)
    val selectedSearchResult = state.searchResults.getOrNull(state.selectedSearchIndex)
    val layoutIdentity = listOf(spec, state.fontPath, state.settings.showChapterTitle,
        density.density, density.fontScale, layoutDirection)
    val entryLayoutIdentity = remember(state.navigationVersion) { layoutIdentity }
    val acknowledgingVisibleLeaf = state.settledPageIndex != null && layoutIdentity == entryLayoutIdentity
    val targetSearchQuery = state.searchQuery.takeIf {
        selectedSearchResult?.chapterId == chapter.id &&
            selectedSearchResult.paragraphIndex == state.restorePosition
    }
    val initialActual = if (pages.isEmpty()) {
        if (state.restorePosition > 0) Int.MIN_VALUE else 0
    } else if (acknowledgingVisibleLeaf) {
        checkNotNull(state.settledPageIndex).coerceIn(pages.indices)
    } else {
        positions.pageFor(
            pages,
            state.restorePosition,
            searchQuery = targetSearchQuery,
            charOffset = state.restoreCharOffset,
        ).coerceIn(pages.indices)
    }
    val desiredItemKey = pagerWindow.firstOrNull {
        it.chapterIndex == state.chapterIndex && it.pageIndex == initialActual
    }?.key ?: pagerWindow[currentStart.coerceIn(pagerWindow.indices)].key
    val desiredSpreadIndex = pagerSpreads.indexOfFirst { spread ->
        spread.items.any { it.key == desiredItemKey }
    }.takeIf { it >= 0 } ?: 0
    val desiredSpreadKey = pagerSpreads[desiredSpreadIndex].key
    val pager = rememberReaderPagerState(
        sessionId = state.sessionId,
        initialPage = desiredSpreadIndex,
        pageCount = { pagerSpreads.size },
    )
    // Programmatic turns intentionally have no backlog. Mature readers keep finger dragging
    // interruptible and ignore repeated tap/key turns while an accepted animation is running;
    // replaying an unlimited queue after the finger has taken over makes the page feel sticky.
    val turnRequests = remember { Channel<Int>(Channel.RENDEZVOUS) }
    var lastWheelTurnAt by remember { mutableLongStateOf(0L) }
    var settledSpreadKey by remember(pager) { mutableStateOf(desiredSpreadKey) }
    // Initial measure/restoration must be applied before any leaf can be saved as user progress.
    var appliedNavigationVersion by remember(pager) { mutableIntStateOf(-1) }
    var pendingDirectChapterTurn by remember { mutableStateOf<Int?>(null) }
    val latestPagerSpreads by rememberUpdatedState(pagerSpreads)
    val latestReaderState by rememberUpdatedState(state)
    val latestPaginationComplete by rememberUpdatedState(pagination.isComplete)
    val prefetchDensity = LocalDensity.current.density
    val epubPath = state.book?.takeIf { it.format == BookFormat.EPUB }?.storagePath
    val visualCurrentPage = readerPagerVisualCurrentIndex(
        pagerSpreads = pagerSpreads,
        settledSpreadKey = settledSpreadKey,
        pagerCurrentPage = pager.currentPage,
        scrolling = pager.isScrollInProgress,
    )

    LaunchedEffect(pager, pagerSpreads, epubPath, resourcePriorityActive, prefetchDensity) {
        if (epubPath == null || resourcePriorityActive) return@LaunchedEffect
        snapshotFlow { pager.settledPage to pager.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { (settledPage, scrolling) ->
                if (scrolling) return@collectLatest
                val nearbyPages = buildList {
                    pagerSpreads.getOrNull(settledPage + 1)?.items?.mapNotNullTo(this) { it.page }
                    pagerSpreads.getOrNull(settledPage + 2)?.items?.mapNotNullTo(this) { it.page }
                    pagerSpreads.getOrNull(settledPage - 1)?.items?.mapNotNullTo(this) { it.page }
                }
                prefetchReaderEpubImages(
                    epubPath = epubPath,
                    pages = nearbyPages,
                    density = prefetchDensity,
                )
            }
    }

    // Directory/search jumps intentionally select another logical page. Boundary navigation does
    // not scroll here: the key recorded by the completed gesture is already the desired page and
    // Compose keeps it anchored while the surrounding window changes.
    // Overlay visibility changes the amount of neighbouring content retained for rendering. It
    // must not replay a chapter-entry restore against the Pager: doing so sent an already-read
    // page back to the chapter opening whenever the controls appeared. Only an explicit logical
    // destination change may drive this positioning effect.
    LaunchedEffect(state.navigationVersion, desiredSpreadKey) {
        if (acknowledgingVisibleLeaf) {
            appliedNavigationVersion = state.navigationVersion
            return@LaunchedEffect
        }
        if (settledSpreadKey != desiredSpreadKey) {
            val target = pagerSpreads.indexOfFirst { it.key == desiredSpreadKey }
            if (target >= 0) {
                pager.scrollToPage(target)
                settledSpreadKey = desiredSpreadKey
            }
        }
        appliedNavigationVersion = state.navigationVersion
    }

    LaunchedEffect(pager) {
        pager.settledReaderLeaves(
            navigationVersion = { latestReaderState.navigationVersion },
            appliedNavigationVersion = { appliedNavigationVersion },
        ).collect { settled ->
            val key = settled?.first ?: return@collect
            val spreads = latestPagerSpreads
            val readerState = latestReaderState
            // Resolve the measured leaf's stable identity, never an index from a different
            // chapter-window generation. Remeasure alone is not a navigation command.
            val spread = spreads.firstOrNull { it.key == key } ?: return@collect
            val item = spread.items.firstOrNull() ?: return@collect
            settledSpreadKey = spread.key
            when {
                item.chapterIndex != readerState.chapterIndex && item.page != null -> {
                    val anchor = item.page.blocks.firstOrNull { it.kind == ParagraphKind.TEXT }
                    retainedPage = RetainedReaderPage(
                        item.page, readerPageNumber(readerState, item.pageIndex, item.pageCount),
                    )
                    settlePage(
                        ReaderPageDestination(
                            sourceChapterIndex = readerState.chapterIndex,
                            chapterIndex = item.chapterIndex,
                            pageIndex = item.pageIndex,
                            paragraphIndex = anchor?.paragraphIndex ?: item.page.startParagraph,
                            charOffset = anchor?.textStart ?: 0,
                        ),
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
                    val anchor = item.page.blocks.firstOrNull { block ->
                        block.kind == ParagraphKind.TEXT
                    }
                    val visibleEnd = spread.items
                        .filter { visible -> visible.chapterIndex == item.chapterIndex }
                        .flatMap { visible -> visible.page?.blocks.orEmpty() }
                        .filter { block -> block.kind == ParagraphKind.TEXT }
                        .maxOfOrNull(DocumentBlock::paragraphIndex)
                        ?: anchor?.paragraphIndex
                        ?: item.page.startParagraph
                    savePosition(
                        anchor?.paragraphIndex ?: item.page.startParagraph,
                        anchor?.textStart ?: 0,
                        latestPaginationComplete &&
                            lastVisible.pageCount > 0 &&
                            lastVisible.pageIndex == lastVisible.pageCount - 1,
                        visibleEnd,
                    )
                }
            }
        }
    }
    LaunchedEffect(chapterTurns) {
        chapterTurns.collectLatest { direction ->
            if (direction != 0) pendingDirectChapterTurn = if (direction < 0) -1 else 1
        }
    }
    LaunchedEffect(
        pendingDirectChapterTurn,
        pagerSpreads,
        state.chapterIndex,
        pagination.isComplete,
    ) {
        val direction = pendingDirectChapterTurn ?: return@LaunchedEffect
        if (pager.isScrollInProgress || !pagination.isComplete) return@LaunchedEffect
        val target = directChapterTargetSpreadIndex(
            pagerSpreads = pagerSpreads,
            currentChapterIndex = state.chapterIndex,
            direction = direction,
        )
        if (target >= 0) {
            // Chapter buttons use the same physical Pager path as a boundary turn. The already
            // measured neighbour becomes visible first; snapshotFlow then commits the logical
            // chapter exactly once. This avoids rebuilding the window and correcting its anchor
            // in a second frame.
            pager.scrollToPage(target)
            pendingDirectChapterTurn = null
        } else {
            // Stay on the current page until the real next page joins this Pager. Never replace
            // readable content with a loading surface or let cache state become a visible target.
            prioritizeAdjacentChapter(state.chapterIndex, direction)
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
            volumeTurns.collect { direction -> turnRequests.trySend(direction) }
        }
        for (direction in turnRequests) {
            dismissControls()
            val spreads = latestPagerSpreads
            val readerState = latestReaderState
            val target = (pager.settledPage + direction).coerceIn(0, spreads.lastIndex)
            when {
                target != pager.settledPage -> {
                    try {
                        pager.animateScrollToPage(target)
                    } catch (cancellation: CancellationException) {
                        if (!currentCoroutineContext().isActive) throw cancellation
                        // A direct finger gesture has higher priority than a tap/key animation.
                        // Leave the Pager at the user's gesture state instead of retrying a stale
                        // destination and fighting the next rapid swipe.
                    }
                }
                direction < 0 && readerState.chapterIndex > 0 -> {
                    prioritizeAdjacentChapter(readerState.chapterIndex, -1)
                }
                direction > 0 &&
                    latestPaginationComplete &&
                    readerState.chapterIndex < readerState.chapters.lastIndex -> {
                    // A chapter boundary is never a loading command. The read-ahead task will add
                    // the real neighbouring page to this same Pager; until then the settled page
                    // remains unchanged without a placeholder, spinner or partial transition.
                    prioritizeAdjacentChapter(readerState.chapterIndex, 1)
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
            fraction > .67f && hasNext && pagination.isComplete -> turnRequests.trySend(1)
            fraction in .33f..67f -> middleTap()
        }
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize()
                .pointerInput(turnRequests) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.firstOrNull() ?: continue
                            val delta = change.scrollDelta
                            val dominantDelta = if (
                                kotlin.math.abs(delta.y) >= kotlin.math.abs(delta.x)
                            ) {
                                delta.y
                            } else {
                                delta.x
                            }
                            if (
                                dominantDelta != 0f &&
                                change.uptimeMillis - lastWheelTurnAt >= 180L
                            ) {
                                lastWheelTurnAt = change.uptimeMillis
                                turnRequests.trySend(if (dominantDelta > 0f) 1 else -1)
                            }
                            change.consume()
                        }
                    }
                }
                .observePagerTap(
                    onTapFraction = { fraction -> pagerTap(fraction) },
                ),
            // Keep exactly one measured neighbour attached even while a turn is running. Dropping
            // this to zero in response to isScrollInProgress changes the Pager's composition
            // window during the gesture and makes a fast follow-up turn stick or spring back.
            // Parsing and distant prefetch are paused separately; this leaf is already prepared.
            beyondViewportPageCount = 1,
            key = { virtualPage -> pagerSpreads[virtualPage].key },
        ) { virtualPage ->
            val spread = pagerSpreads[virtualPage]
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.background)
                    .zIndex(
                        if (state.settings.pageTurnAnimation == PageTurnAnimation.HORIZONTAL_SLIDE) {
                            0f
                        } else {
                            // A physical book always keeps the lower-numbered leaf above the later
                            // leaf. Forward turns remove that upper leaf; backward turns bring the
                            // previous upper leaf back over the current page.
                            (pagerSpreads.size - virtualPage).toFloat()
                        },
                    )
                    .readerPageTurnEffect(
                        animation = state.settings.pageTurnAnimation,
                        pageOffset = {
                            (visualCurrentPage - virtualPage) + pager.currentPageOffsetFraction
                        },
                    ),
            ) {
                ReaderPagerSpreadContent(
                    spread = spread,
                    twoPageSpread = twoPageSpread,
                    spreadGutter = spreadGutter,
                    state = state,
                    spec = spec,
                    palette = palette,
                    middleTap = middleTap,
                    selectionEnabled = !pager.isScrollInProgress && virtualPage == pager.settledPage,
                    onSelectionActiveChange = { active -> textSelectionActive = active },
                    topInsetDp = topInsetDp,
                    bottomInsetDp = bottomInsetDp,
                    physicalViewportHeightDp = physicalViewportHeightDp,
                    onTextActionTarget = onTextActionTarget,
                    onDocumentLink = onDocumentLink,
                )
            }
        }
        if (pages.isEmpty()) {
            retainedPage?.let { retained ->
                Box(Modifier.fillMaxSize().background(palette.background)) {
                    ReaderPageRenderer(
                        page = retained.page,
                        spec = spec,
                        palette = palette,
                        fontPath = state.fontPath,
                        onTapFraction = { fraction ->
                            if (fraction in .33f..67f) middleTap() else dismissControls()
                        },
                        epubPath = state.book?.takeIf {
                            it.format == BookFormat.EPUB
                        }?.storagePath,
                        showRegularChapterTitle = state.settings.showChapterTitle,
                        highlightQuery = state.searchQuery,
                        pageNumber = retained.pageNumber,
                        showReadingTime = state.settings.showReadingTime,
                        showBatteryLevel = state.settings.showBatteryLevel,
                        modifier = Modifier.readerPageViewportModifier(
                            retained.page,
                            topInsetDp,
                            bottomInsetDp,
                        ),
                        fullPageViewportHeightDp = physicalViewportHeightDp,
                        readerAnnotations = state.annotations,
                        onTextActionTarget = onTextActionTarget,
                        onDocumentLink = onDocumentLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderPagerSpreadContent(
    spread: ReaderPagerSpread,
    twoPageSpread: Boolean,
    spreadGutter: Dp,
    state: ReaderContentState,
    spec: ReaderLayoutSpec,
    palette: ReaderRenderPalette,
    middleTap: () -> Unit,
    selectionEnabled: Boolean,
    onSelectionActiveChange: (Boolean) -> Unit,
    topInsetDp: Float,
    bottomInsetDp: Float,
    physicalViewportHeightDp: Float,
    onTextActionTarget: (ReaderTextActionTarget) -> Unit,
    onDocumentLink: (String) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        spread.items.forEachIndexed { index, item ->
            Box(Modifier.weight(1f).fillMaxHeight()) {
                ReaderPagerLeaf(
                    item = item,
                    state = state,
                    spec = spec,
                    palette = palette,
                    middleTap = middleTap,
                    selectionEnabled = selectionEnabled && item.page != null,
                    onSelectionActiveChange = onSelectionActiveChange,
                    topInsetDp = topInsetDp,
                    bottomInsetDp = bottomInsetDp,
                    physicalViewportHeightDp = physicalViewportHeightDp,
                    onTextActionTarget = onTextActionTarget,
                    onDocumentLink = onDocumentLink,
                )
            }
            if (index < spread.items.lastIndex) {
                ReaderSpreadSpine(spreadGutter, palette.body)
            }
        }
        if (twoPageSpread && spread.items.size == 1) {
            Spacer(Modifier.width(spreadGutter))
            Spacer(Modifier.weight(1f).fillMaxHeight())
        }
    }
}


/**
 * Keeps the reader's page content independent from its transition. Horizontal sliding is the
 * Pager default; cover pins the incoming sheet below the outgoing one.
 */
private fun Modifier.readerPageTurnEffect(
    animation: PageTurnAnimation,
    pageOffset: () -> Float,
): Modifier {
    return when (animation) {
        PageTurnAnimation.HORIZONTAL_SLIDE -> this
        PageTurnAnimation.COVER -> graphicsLayer {
            val clampedOffset = pageOffset().coerceIn(-1f, 1f)
            alpha = if (abs(pageOffset()) <= 1.001f) 1f else 0f
            if (clampedOffset <= 0f) {
                // Later pages are pinned below the current leaf. This same rule makes the current
                // page stay below the previous leaf while a backward gesture brings it in from
                // the left, fixing the formerly inverted backward-cover animation.
                translationX = size.width * clampedOffset
            }
            clip = true
        }
    }
}

/**
 * Pager reconciles a stable key with its new numeric index during measure. Chapter-window
 * recentering can therefore expose the old numeric index to one composition before measure runs.
 * Keep active drags fully controlled by Pager, but use the last settled stable key while idle so
 * custom cover alpha never hides the newly active leaf during that reconciliation frame.
 */
internal fun readerPagerVisualCurrentIndex(
    pagerSpreads: List<ReaderPagerSpread>,
    settledSpreadKey: String,
    pagerCurrentPage: Int,
    scrolling: Boolean,
): Int {
    if (scrolling) return pagerCurrentPage.coerceIn(0, pagerSpreads.lastIndex.coerceAtLeast(0))
    return pagerSpreads.indexOfFirst { it.key == settledSpreadKey }
        .takeIf { it >= 0 }
        ?: pagerCurrentPage.coerceIn(0, pagerSpreads.lastIndex.coerceAtLeast(0))
}

@Composable
private fun RowScope.ReaderSpreadSpine(width: Dp, color: Color) {
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(color.copy(alpha = .025f)),
        contentAlignment = Alignment.Center,
    ) {
        // A book needs a visible center separation, not a simulated drop shadow.
        Box(Modifier.width(1.dp).fillMaxHeight().background(color.copy(alpha = .14f)))
    }
}

@Composable
internal fun ReaderPagerLeaf(
    item: ReaderPagerItem,
    state: ReaderContentState,
    spec: ReaderLayoutSpec,
    palette: ReaderRenderPalette,
    middleTap: () -> Unit,
    selectionEnabled: Boolean,
    onSelectionActiveChange: (Boolean) -> Unit,
    topInsetDp: Float,
    bottomInsetDp: Float,
    physicalViewportHeightDp: Float,
    onTextActionTarget: (ReaderTextActionTarget) -> Unit,
    onDocumentLink: (String) -> Unit,
) {
    val renderedPage = item.page
    if (renderedPage == null) {
        Box(modifier = Modifier.fillMaxSize().background(palette.background))
        return
    }
    ReaderPageRenderer(
        page = renderedPage,
        spec = spec,
        palette = palette,
        fontPath = state.fontPath,
        onTapFraction = { fraction -> if (fraction in .33f..67f) middleTap() },
        epubPath = state.book?.takeIf { it.format == BookFormat.EPUB }?.storagePath,
        modifier = Modifier.readerPageViewportModifier(renderedPage, topInsetDp, bottomInsetDp)
            .testTag("reader-leaf:${item.key}"),
        fullPageViewportHeightDp = physicalViewportHeightDp,
        showRegularChapterTitle = state.settings.showChapterTitle,
        highlightQuery = state.searchQuery,
        readerAnnotations = state.annotations,
        pageNumber = readerPageNumber(state, item.pageIndex, item.pageCount),
        showReadingTime = state.settings.showReadingTime,
        showBatteryLevel = state.settings.showBatteryLevel,
        selectionEnabled = selectionEnabled,
        onSelectionActiveChange = onSelectionActiveChange,
        onTextActionTarget = onTextActionTarget,
        onDocumentLink = onDocumentLink,
    )
}

internal fun Modifier.readerPageViewportModifier(
    page: ReaderPage,
    topInsetDp: Float,
    bottomInsetDp: Float,
): Modifier = fillMaxSize().then(
    if (page.isFullPageImage) Modifier else Modifier.padding(
        top = topInsetDp.dp,
        bottom = bottomInsetDp.dp,
    ),
)

/**
 * Page taps belong to the stable Pager node instead of page-local content. The current page can
 * be replaced by a lightweight chapter placeholder in the same frame that a rapid tap arrives;
 * keeping this observer above those pages prevents that tap from being discarded. It also makes
 * the tablet spread gutter and an empty companion leaf participate in the center-tap interaction.
 */
internal fun Modifier.observePagerTap(
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
    val key = "${page?.chapterIndex ?: chapterIndex}:$pageIndex"
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
internal fun readerPagerSpread(items: List<ReaderPagerItem>): ReaderPagerSpread {
    val first = items.first()
    val spreadIndex = if (first.pageIndex >= 0) first.pageIndex / 2 else first.pageIndex
    return ReaderPagerSpread(
        items = items,
        key = "${first.page?.chapterIndex ?: first.chapterIndex}:spread:$spreadIndex",
    )
}

internal fun buildReaderPagerWindow(
    currentChapterIndex: Int,
    currentPages: List<ReaderPage>,
    previousPages: List<ReaderPage>,
    nextPages: List<ReaderPage>,
    hasPrevious: Boolean,
    hasNext: Boolean,
    currentPagesComplete: Boolean = true,
    currentPlaceholderPageIndex: Int,
    chapterCount: Int,
): List<ReaderPagerItem> = buildList {
    if (hasPrevious && currentChapterIndex > 0) {
        // Keep the previous chapter's complete lightweight page index in the stable Pager. Only
        // beyondViewportPageCount pages are composed, but the chapter button can now target its
        // first page without rebuilding the Pager around a new ViewModel chapter first.
        if (previousPages.isNotEmpty()) {
            previousPages.forEach { page ->
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
            add(
                ReaderPagerItem(
                    currentChapterIndex,
                    page.index,
                    if (currentPagesComplete) currentPages.size else 0,
                    page,
                ),
            )
        }
    }
    if (hasNext && currentChapterIndex < chapterCount - 1 && currentPagesComplete) {
        // Keep the complete next chapter index in the same Pager. HorizontalPager still composes
        // only its configured viewport neighbour, but a rapid second gesture can now target page
        // two (or later) without waiting for the ViewModel's chapter-window recentering frame.
        if (nextPages.isNotEmpty()) {
            nextPages.forEach { page ->
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
}

/** Returns the first page of the adjacent chapter for an explicit chapter-skip action. */
internal fun directChapterTargetSpreadIndex(
    pagerSpreads: List<ReaderPagerSpread>,
    currentChapterIndex: Int,
    direction: Int,
): Int {
    if (direction == 0) return -1
    val targetChapterIndex = currentChapterIndex + if (direction < 0) -1 else 1
    return pagerSpreads.indexOfFirst { spread ->
        spread.items.any { item ->
            item.chapterIndex == targetChapterIndex && item.pageIndex == 0 && item.page != null
        }
    }
}

/** A backward page-boundary gesture continues from the prior chapter's end; a skip does not. */
internal fun chapterTransitionOpensAtEnd(direction: Int, directChapterTurn: Boolean): Boolean =
    direction < 0 && !directChapterTurn

internal data class RetainedReaderPage(
    val page: ReaderPage,
    val pageNumber: String?,
)

internal fun readerPageNumber(state: ReaderContentState, pageIndex: Int, pageCount: Int): String? =
    if (state.settings.showPageNumber && state.searchResults.isEmpty() && pageCount > 0) {
        "${pageIndex + 1}/$pageCount"
    } else {
        null
    }
