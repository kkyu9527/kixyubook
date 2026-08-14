package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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


@Composable
internal fun PagedReader(
    state: ReaderUiState,
    chapter: ReaderChapter,
    spec: ReaderLayoutSpec,
    palette: ReaderRenderPalette,
    savePosition: (Int, Int, Boolean) -> Unit,
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
    spreadGutter: Dp,
    topInsetDp: Float,
    bottomInsetDp: Float,
    physicalViewportHeightDp: Float,
    onTextActionTarget: (ReaderTextActionTarget) -> Unit,
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
                modifier = Modifier.readerPageViewportModifier(retained.page, topInsetDp, bottomInsetDp),
                fullPageViewportHeightDp = physicalViewportHeightDp,
                onTextActionTarget = onTextActionTarget,
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
        state.restoreCharOffset,
        state.chapters.size,
        pages,
        previousPages,
        nextPages,
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
    val pager = rememberPagerState(
        initialPage = desiredSpreadIndex,
        pageCount = { pagerSpreads.size },
    )
    val turnRequests = remember { Channel<Int>(Channel.UNLIMITED) }
    var lastWheelTurnAt by remember { mutableLongStateOf(0L) }
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
                    val anchor = item.page.blocks.firstOrNull { block ->
                        block.kind == ParagraphKind.TEXT
                    }
                    savePosition(
                        anchor?.paragraphIndex ?: item.page.startParagraph,
                        anchor?.textStart ?: 0,
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
        modifier = Modifier.fillMaxSize()
            .pointerInput(turnRequests) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val delta = change.scrollDelta
                        val dominantDelta = if (kotlin.math.abs(delta.y) >= kotlin.math.abs(delta.x)) {
                            delta.y
                        } else {
                            delta.x
                        }
                        if (dominantDelta != 0f && change.uptimeMillis - lastWheelTurnAt >= 180L) {
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
                        topInsetDp = topInsetDp,
                        bottomInsetDp = bottomInsetDp,
                        physicalViewportHeightDp = physicalViewportHeightDp,
                        onTextActionTarget = onTextActionTarget,
                    )
                }
                if (index < spread.items.lastIndex) {
                    Spacer(Modifier.width(spreadGutter))
                }
            }
            if (twoPageSpread && spread.items.size == 1) {
                Spacer(Modifier.width(spreadGutter))
                Spacer(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
internal fun ReaderPagerLeaf(
    item: ReaderPagerItem,
    state: ReaderUiState,
    spec: ReaderLayoutSpec,
    palette: ReaderRenderPalette,
    middleTap: () -> Unit,
    selectionEnabled: Boolean,
    onSelectionActiveChange: (Boolean) -> Unit,
    topInsetDp: Float,
    bottomInsetDp: Float,
    physicalViewportHeightDp: Float,
    onTextActionTarget: (ReaderTextActionTarget) -> Unit,
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
            modifier = Modifier.readerPageViewportModifier(renderedPage, topInsetDp, bottomInsetDp),
            fullPageViewportHeightDp = physicalViewportHeightDp,
            showRegularChapterTitle = state.settings.showChapterTitle,
            highlightQuery = state.searchQuery,
            pageNumber = item.page?.let {
                readerPageNumber(state, item.pageIndex, item.pageCount)
            },
            selectionEnabled = selectionEnabled,
            onSelectionActiveChange = onSelectionActiveChange,
            onTextActionTarget = onTextActionTarget,
        )
    }
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
internal fun readerPagerSpread(items: List<ReaderPagerItem>): ReaderPagerSpread {
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

internal data class RetainedReaderPage(
    val page: ReaderPage,
    val pageNumber: String?,
)

internal fun readerPageNumber(state: ReaderUiState, pageIndex: Int, pageCount: Int): String? =
    if (state.settings.showPageNumber && state.searchResults.isEmpty() && pageCount > 0) {
        "${pageIndex + 1}/$pageCount"
    } else {
        null
    }
