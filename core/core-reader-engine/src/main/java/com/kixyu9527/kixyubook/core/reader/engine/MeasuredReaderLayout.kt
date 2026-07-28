package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Paginates with the same Compose text measurement used by the renderer.
 * This keeps custom fonts, font scale, line height and letter spacing inside
 * the real page bounds instead of estimating layout from character counts.
 */
@Composable
fun rememberMeasuredReaderPages(
    chapter: ReaderChapter,
    spec: ReaderLayoutSpec,
    fontPath: String?,
    showRegularChapterTitle: Boolean = true,
    coordinator: ReaderPaginationCoordinator,
    measurer: TextMeasurer,
    prefetch: Boolean = false,
): List<ReaderPage> {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val family = rememberReaderFont(fontPath)
    val cacheKey = remember(
        chapter.id,
        spec,
        fontPath,
        showRegularChapterTitle,
        density.density,
        density.fontScale,
        layoutDirection,
    ) {
        PaginationCacheKey(
            chapterId = chapter.id,
            spec = spec,
            fontPath = fontPath,
            showRegularChapterTitle = showRegularChapterTitle,
            density = density.density,
            fontScale = density.fontScale,
            layoutDirection = layoutDirection,
        )
    }
    var pages by remember(cacheKey) {
        mutableStateOf(coordinator.cached(cacheKey).orEmpty())
    }
    LaunchedEffect(cacheKey, chapter, family) {
        if (pages.isNotEmpty()) return@LaunchedEffect
        pages = coordinator.getOrLoad(cacheKey, prefetch) {
            MeasuredReaderPaginator(measurer, density)
                .paginate(chapter, spec, family, showRegularChapterTitle)
        }.await()
    }
    return pages
}

@Composable
fun rememberReaderPaginationCoordinator(): ReaderPaginationCoordinator {
    val coordinator = remember { ReaderPaginationCoordinator() }
    DisposableEffect(coordinator) { onDispose(coordinator::close) }
    return coordinator
}

internal data class PaginationCacheKey(
    val chapterId: Long,
    val spec: ReaderLayoutSpec,
    val fontPath: String?,
    val showRegularChapterTitle: Boolean,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
)

/** One bounded pagination owner per reader composition, never process-global. */
class ReaderPaginationCoordinator internal constructor() {
    private val lock = Any()
    private val sessionJob: Job = SupervisorJob()
    private val paginationScope = CoroutineScope(sessionJob + Dispatchers.Default.limitedParallelism(1))
    private val inFlight = mutableMapOf<PaginationCacheKey, PaginationLoad>()
    private val pages = object : LinkedHashMap<PaginationCacheKey, List<ReaderPage>>(
        PAGINATION_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PaginationCacheKey, List<ReaderPage>>) =
            size > PAGINATION_CACHE_SIZE
    }

    internal fun cached(key: PaginationCacheKey): List<ReaderPage>? = synchronized(lock) { pages[key] }

    /**
     * Pagination is shared across the current and prefetched chapter compositions. A caller
     * leaving composition only cancels its await; the expensive EPUB layout continues and is
     * reused when that chapter becomes current a moment later.
     */
    internal fun getOrLoad(
        key: PaginationCacheKey,
        prefetch: Boolean,
        loader: suspend () -> List<ReaderPage>,
    ): Deferred<List<ReaderPage>> = synchronized(lock) {
        pages[key]?.let { return@synchronized kotlinx.coroutines.CompletableDeferred(it) }
        if (!prefetch) {
            val stalePrefetches = inFlight.filter { (staleKey, load) ->
                staleKey != key && load.prefetch
            }
            stalePrefetches.forEach { (staleKey, load) ->
                load.deferred.cancel()
                inFlight.remove(staleKey)
            }
            inFlight[key]?.let { target ->
                inFlight[key] = target.copy(prefetch = false)
                return@synchronized target.deferred
            }
        }
        inFlight[key]?.let { return@synchronized it.deferred }
        lateinit var deferred: Deferred<List<ReaderPage>>
        deferred = paginationScope.async(start = CoroutineStart.LAZY) {
            try {
                val measured = loader()
                coroutineContext.ensureActive()
                synchronized(lock) { pages[key] = measured }
                // TextLayoutResult owns native paragraph objects. A full chapter pagination drops
                // all of those temporary wrappers at once, but ART otherwise waits until memory
                // pressure is already close to the process limit. Compact at this explicit batch
                // boundary so a directory jump cannot stack current/previous/next native layouts.
                Runtime.getRuntime().gc()
                measured
            } finally {
                synchronized(lock) {
                    if (inFlight[key]?.deferred === deferred) inFlight.remove(key)
                }
            }
        }
        inFlight[key] = PaginationLoad(deferred, prefetch)
        deferred.also { it.start() }
    }

    internal fun close() {
        sessionJob.cancel()
        synchronized(lock) {
            inFlight.clear()
            pages.clear()
        }
    }

    private data class PaginationLoad(
        val deferred: Deferred<List<ReaderPage>>,
        val prefetch: Boolean,
    )
}

private class MeasuredReaderPaginator(
    private val measurer: TextMeasurer,
    private val density: androidx.compose.ui.unit.Density,
) {
    suspend fun paginate(
        chapter: ReaderChapter,
        spec: ReaderLayoutSpec,
        family: androidx.compose.ui.text.font.FontFamily,
        showRegularChapterTitle: Boolean,
    ): List<ReaderPage> {
        val contentWidthDp = (spec.viewportWidthDp - spec.horizontalMarginDp * 2f)
            .coerceAtLeast(MIN_BODY_WIDTH_DP)
        val widthPx = with(density) { contentWidthDp.dp.roundToPx() }
        val spacingPx = with(density) { (spec.fontSizeSp * PARAGRAPH_SPACING_EM).dp.toPx() }
        val pages = mutableListOf<ReaderPage>()
        var blocks = mutableListOf<DocumentBlock>()
        var usedHeightPx = 0f
        var opening = true
        val openingHeading = splitReaderChapterHeading(chapter.title)

        fun bodyHeightPx(): Float = availableBodyHeightPx(spec, opening, openingHeading, showRegularChapterTitle)
        fun flush() {
            if (blocks.isEmpty()) return
            pages += ReaderPage(pages.size, chapter.index, chapter.title, opening, blocks.toList())
            blocks = mutableListOf()
            usedHeightPx = 0f
            opening = false
        }

        chapter.contentParagraphs().forEach { paragraph ->
            coroutineContext.ensureActive()
            if (paragraph.kind == ParagraphKind.IMAGE && paragraph.resourcePath != null) {
                var imageLayout = standardizedReaderImageLayout(
                    contentWidthDp,
                    paragraph.intrinsicWidth,
                    paragraph.intrinsicHeight,
                )
                var imageHeightPx = with(density) { imageLayout.heightDp.dp.toPx() }
                var availablePx = (bodyHeightPx() - usedHeightPx).coerceAtLeast(0f)
                if (imageHeightPx + spacingPx > availablePx && blocks.isNotEmpty()) {
                    flush()
                    availablePx = bodyHeightPx()
                }
                if (imageHeightPx + spacingPx > availablePx) {
                    val maxImageHeightPx = (availablePx - spacingPx).coerceAtLeast(availablePx * .75f)
                    val scale = (maxImageHeightPx / imageHeightPx).coerceIn(.1f, 1f)
                    imageLayout = imageLayout.copy(
                        widthDp = imageLayout.widthDp * scale,
                        heightDp = imageLayout.heightDp * scale,
                    )
                    imageHeightPx *= scale
                }
                blocks += DocumentBlock(
                    paragraphIndex = paragraph.index,
                    fullText = paragraph.text,
                    visibleText = "",
                    continuation = false,
                    bottomSpacing = true,
                    kind = ParagraphKind.IMAGE,
                    resourcePath = paragraph.resourcePath,
                    mediaType = paragraph.mediaType,
                    intrinsicWidth = paragraph.intrinsicWidth,
                    intrinsicHeight = paragraph.intrinsicHeight,
                    imageWidthDp = imageLayout.widthDp,
                    imageHeightDp = imageLayout.heightDp,
                )
                usedHeightPx += imageHeightPx + spacingPx
                if (bodyHeightPx() - usedHeightPx < spacingPx) flush()
                return@forEach
            }
            var remaining = paragraph.text
            var remainingStart = 0
            var continuation = false
            while (remaining.isNotEmpty()) {
                coroutineContext.ensureActive()
                val availablePx = (bodyHeightPx() - usedHeightPx).coerceAtLeast(0f)
                val style = readerBodyTextStyle(spec, family, indent = !continuation)
                val lineHeightPx = with(density) {
                    (spec.fontSizeSp * spec.lineHeightMultiplier).sp.toPx()
                }.coerceAtLeast(1f)
                val maxMeasuredLines = (availablePx / lineHeightPx).toInt().coerceAtLeast(1)
                // Some EPUBs put an entire chapter into one XHTML text node. Measuring the full
                // remainder once per page is O(n²) and can retain hundreds of MB of native text
                // layout data. Start with a bounded window and only grow it when that window still
                // cannot fill the available page.
                var measuredLength = remaining.length.coerceAtMost(MEASUREMENT_WINDOW_CHARS)
                var measuredSpans: List<ReaderTextSpan>
                var layout: TextLayoutResult
                while (true) {
                    coroutineContext.ensureActive()
                    measuredSpans = paragraph.spans.sliceForText(
                        remainingStart,
                        remainingStart + measuredLength,
                    )
                    layout = measurer.measure(
                        text = readerAnnotatedText(remaining.substring(0, measuredLength), measuredSpans),
                        style = style,
                        overflow = TextOverflow.Clip,
                        softWrap = true,
                        maxLines = maxMeasuredLines,
                        constraints = Constraints(maxWidth = widthPx),
                    )
                    if (
                        measuredLength == remaining.length ||
                        layout.didOverflowHeight ||
                        layout.size.height > availablePx
                    ) break
                    measuredLength = (measuredLength * 2).coerceAtMost(remaining.length)
                }
                val textHeightPx = layout.size.height.toFloat()
                val measuredWholeRemainder = measuredLength == remaining.length && !layout.didOverflowHeight
                if (measuredWholeRemainder && textHeightPx + spacingPx <= availablePx) {
                    blocks += DocumentBlock(
                        paragraph.index,
                        paragraph.text,
                        remaining,
                        continuation,
                        bottomSpacing = true,
                        spans = measuredSpans,
                    )
                    usedHeightPx += textHeightPx + spacingPx
                    remaining = ""
                    continue
                }
                if (measuredWholeRemainder && textHeightPx <= availablePx) {
                    // At a page boundary paragraph spacing is unnecessary and would
                    // otherwise push the last baseline below the footer.
                    blocks += DocumentBlock(
                        paragraph.index,
                        paragraph.text,
                        remaining,
                        continuation,
                        bottomSpacing = false,
                        spans = measuredSpans,
                    )
                    remaining = ""
                    flush()
                    continue
                }

                var fittingLine = -1
                for (line in 0 until layout.lineCount) {
                    if (layout.getLineBottom(line) <= availablePx) fittingLine = line else break
                }
                if (fittingLine < 0 && blocks.isNotEmpty()) {
                    flush()
                    continue
                }
                fittingLine = fittingLine.coerceAtLeast(0)
                val end = layout.getLineEnd(fittingLine, visibleEnd = true).coerceIn(1, measuredLength)
                val visible = remaining.substring(0, end).trimEnd().ifEmpty { remaining.substring(0, end) }
                blocks += DocumentBlock(
                    paragraph.index,
                    paragraph.text,
                    visible,
                    continuation,
                    bottomSpacing = false,
                    spans = paragraph.spans.sliceForText(remainingStart, remainingStart + visible.length),
                )
                usedHeightPx += ceil(layout.getLineBottom(fittingLine))
                val rawRemainder = remaining.substring(end)
                remaining = rawRemainder.trimStart()
                remainingStart += end + (rawRemainder.length - remaining.length)
                continuation = true
                flush()
            }
        }
        flush()
        return pages.ifEmpty { listOf(ReaderPage(0, chapter.index, chapter.title, true, emptyList())) }
    }

    private fun availableBodyHeightPx(
        spec: ReaderLayoutSpec,
        opening: Boolean,
        heading: ReaderChapterHeading,
        showRegularChapterTitle: Boolean,
    ): Float = with(density) {
        val hasOrdinalAndName = heading.ordinal != null && heading.name.isNotEmpty()
        val viewport = spec.viewportHeightDp.dp.toPx()
        val fixedDp = if (opening) {
            ReaderPageMetrics.topPaddingDp + ReaderPageMetrics.bottomPaddingDp +
                ReaderPageMetrics.openingTopDp + (if (hasOrdinalAndName) ReaderPageMetrics.openingOrdinalGapDp else 0f) +
                ReaderPageMetrics.openingGapDp + ReaderPageMetrics.footerGapDp +
                ReaderPageMetrics.footerHeightDp + ReaderPageMetrics.safetyDp
        } else if (showRegularChapterTitle) {
            ReaderPageMetrics.topPaddingDp + ReaderPageMetrics.bottomPaddingDp + ReaderPageMetrics.regularGapDp +
                ReaderPageMetrics.footerGapDp + ReaderPageMetrics.footerHeightDp + ReaderPageMetrics.safetyDp
        } else {
            ReaderPageMetrics.topPaddingDp + ReaderPageMetrics.bottomPaddingDp +
                ReaderPageMetrics.footerGapDp + ReaderPageMetrics.footerHeightDp + ReaderPageMetrics.safetyDp
        }
        val headerSp = if (opening) {
            (if (heading.ordinal != null) OPENING_ORDINAL_LINE_HEIGHT_SP else 0f) +
                (if (heading.name.isNotEmpty()) OPENING_TITLE_LINE_HEIGHT_SP else 0f)
        } else if (showRegularChapterTitle) {
            REGULAR_TITLE_LINE_HEIGHT_SP
        } else {
            0f
        }
        (viewport - fixedDp.dp.toPx() - headerSp.sp.toPx()).coerceAtLeast(MIN_BODY_HEIGHT_DP.dp.toPx())
    }
}

private const val PAGINATION_CACHE_SIZE = 6
private const val MEASUREMENT_WINDOW_CHARS = 512
private const val MIN_BODY_WIDTH_DP = 160f
private const val MIN_BODY_HEIGHT_DP = 120f
private const val PARAGRAPH_SPACING_EM = 0.9f
private const val OPENING_ORDINAL_LINE_HEIGHT_SP = 22f
private const val OPENING_TITLE_LINE_HEIGHT_SP = 36f
private const val REGULAR_TITLE_LINE_HEIGHT_SP = 20f
