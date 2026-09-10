package com.kixyu9527.kixyubook.core.reader.engine
import com.kixyu9527.kixyubook.core.common.cache.ReaderCacheBudget
import com.kixyu9527.kixyubook.core.common.cache.WeightedLruCache

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.diagnostics.toDiagnosticFailure
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureLevel
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureListener
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureRegistry
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/**
 * Paginates with the same Compose text measurement used by the renderer.
 * This keeps custom fonts, font scale, line height and letter spacing inside
 * the real page bounds instead of estimating layout from character counts.
 */
@Composable
fun rememberMeasuredReaderPages(
    chapter: ReaderChapter,
    contentHash: String,
    spec: ReaderLayoutSpec,
    fontPath: String?,
    showRegularChapterTitle: Boolean = true,
    coordinator: ReaderPaginationCoordinator,
    measurer: TextMeasurer,
    prefetch: Boolean = false,
    paused: Boolean = false,
    allowPartialResults: Boolean = true,
    minimumVisibleParagraphIndex: Int? = null,
    retainedSnapshot: ReaderPaginationSnapshot? = null,
    minimumVisibleCharOffset: Int = 0,
): ReaderPaginationSnapshot {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val family = rememberReaderFont(fontPath)
    // Imported fonts use immutable app-private paths. Avoid filesystem stat calls in the reader's
    // first composition; the path itself is a stable cache identity.
    val fontIdentity = fontPath
    // Corrections change effective text while the immutable source hash stays the same. Include
    // the effective chapter text in the cache identity so stale page boundaries can never be
    // reused after a correction, regardless of layout or screen size.
    val effectiveTextRevision = remember(chapter) {
        chapter.layoutRevision()
    }
    val cacheKey = remember(
        chapter.id,
        chapter.bookUuid,
        chapter.title,
        contentHash,
        effectiveTextRevision,
        spec,
        fontIdentity,
        showRegularChapterTitle,
        density.density,
        density.fontScale,
        layoutDirection,
    ) {
        PaginationCacheKey(
            bookUuid = chapter.bookUuid,
            contentHash = "$contentHash-$effectiveTextRevision",
            chapterId = chapter.id,
            chapterTitle = chapter.title,
            spec = spec,
            fontIdentity = fontIdentity,
            showRegularChapterTitle = showRegularChapterTitle,
            density = density.density,
            fontScale = density.fontScale,
            layoutDirection = layoutDirection,
        )
    }
    var snapshot by remember(cacheKey) {
        mutableStateOf(
            coordinator.currentSnapshot(cacheKey)
                ?: retainedSnapshot
                ?: ReaderPaginationSnapshot(),
        )
    }
    LaunchedEffect(cacheKey, chapter, family, prefetch, paused) {
        if (snapshot.isComplete) return@LaunchedEffect
        if (paused) return@LaunchedEffect
        coordinator.getOrLoad(cacheKey, chapter, prefetch) { publishPartial, awaitPermit ->
            MeasuredReaderPaginator(measurer, density)
                .paginate(
                    chapter = chapter,
                    spec = spec,
                    family = family,
                    showRegularChapterTitle = showRegularChapterTitle,
                    publishPartial = publishPartial,
                    awaitPermit = awaitPermit,
                )
        }.first { update ->
            val anchorReady = update.readyForReadingAnchor(minimumVisibleParagraphIndex, minimumVisibleCharOffset)
            if (update.isComplete || (allowPartialResults && anchorReady)) snapshot = update
            update.isComplete
        }
    }
    // A cached partial layout needs the same gate as a newly published one. Merely finding the
    // paragraph's beginning can otherwise display/save an earlier page of a long paragraph.
    return if (snapshot.readyForReadingAnchor(minimumVisibleParagraphIndex, minimumVisibleCharOffset)) {
        snapshot
    } else ReaderPaginationSnapshot()
}

internal fun ReaderPaginationSnapshot.readyForReadingAnchor(paragraphIndex: Int?, charOffset: Int): Boolean =
    isComplete || paragraphIndex == null || pages.any { page ->
        page.blocks.any { block ->
            block.paragraphIndex == paragraphIndex &&
                (charOffset <= 0 || (block.kind == ParagraphKind.TEXT &&
                    block.textStart.toLong() + block.visibleText.length.coerceAtLeast(1) > charOffset))
        }
    }

@Composable
fun rememberReaderPaginationCoordinator(): ReaderPaginationCoordinator {
    val context = LocalContext.current.applicationContext
    val coordinator = remember(context) {
        ReaderPaginationCoordinator(
            ReaderPaginationDiskCache(readerPaginationCacheRoot(context.noBackupFilesDir)),
        )
    }
    DisposableEffect(coordinator) { onDispose(coordinator::close) }
    return coordinator
}

internal data class PaginationCacheKey(
    val bookUuid: String,
    val contentHash: String,
    val chapterId: Long,
    val chapterTitle: String,
    val spec: ReaderLayoutSpec,
    val fontIdentity: String?,
    val showRegularChapterTitle: Boolean,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
)

data class ReaderPaginationSnapshot(
    val pages: List<ReaderPage> = emptyList(),
    val isComplete: Boolean = false,
)

/** One bounded pagination owner per reader composition, never process-global. */
class ReaderPaginationCoordinator internal constructor(
    private val diskCache: ReaderPaginationDiskCache? = null,
) : MemoryPressureListener {
    private val lock = Any()
    private val sessionJob: Job = SupervisorJob()
    // Text layout is CPU-heavy and can overlap a 120 Hz page drag. A dedicated Android
    // background-priority thread keeps speculative pagination from taking a main/render-thread
    // time slice while still letting the current chapter complete independently from Room/IO.
    private val paginationDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { task ->
            Thread({
                // Local JVM tests use android.jar stubs; devices apply the real scheduler hint.
                runCatching {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                }
                task.run()
            }, "reader-pagination").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    private val paginationScope = CoroutineScope(sessionJob + paginationDispatcher)
    private val cacheScope = CoroutineScope(sessionJob + Dispatchers.IO.limitedParallelism(1))
    private val paused = MutableStateFlow(false)
    private val inFlight = mutableMapOf<PaginationCacheKey, PaginationLoad>()
    private val pages = WeightedLruCache<PaginationCacheKey, List<ReaderPage>>(
        ReaderCacheBudget.PAGINATION_MEMORY_BYTES,
        ReaderCacheBudget.MAX_MEMORY_CHAPTERS,
    ) { chapterPages ->
        // fullText is shared between page fragments; count each paragraph once.
        val seen = hashSetOf<Int>()
        chapterPages.sumOf { page ->
            128L + page.blocks.sumOf { block ->
                160L + block.visibleText.length * 2L + block.spans.size * 96L +
                    if (seen.add(block.paragraphIndex)) block.fullText.length * 2L else 0L
            }
        }
    }

    init {
        MemoryPressureRegistry.register(this)
    }

    internal fun cached(key: PaginationCacheKey): List<ReaderPage>? = synchronized(lock) { pages[key] }

    /**
     * Returns the best page set already owned by this reader session.
     *
     * A prefetched chapter can become current while its pagination is still publishing readable
     * batches. Returning only the completed LRU entry made the new current composition briefly
     * start from an empty list, even though the same coordinator already held visible pages. That
     * empty frame covered the Pager at chapter boundaries and looked like a full-screen flash.
     */
    internal fun currentSnapshot(key: PaginationCacheKey): ReaderPaginationSnapshot? =
        synchronized(lock) {
            pages[key]?.let { ReaderPaginationSnapshot(it, isComplete = true) }
                ?: inFlight[key]?.snapshots?.value?.takeIf { it.pages.isNotEmpty() }
        }

    /** Pauses current pagination at page boundaries and cancels disposable speculative layouts. */
    fun setPaused(value: Boolean) {
        paused.value = value
        if (!value) return
        synchronized(lock) {
            val speculative = inFlight.filterValues { it.prefetch }
            speculative.forEach { (key, load) ->
                load.deferred.cancel()
                inFlight.remove(key)
            }
        }
    }

    private suspend fun awaitPermit() {
        paused.first { value -> !value }
    }

    override fun onMemoryPressure(level: MemoryPressureLevel) {
        synchronized(lock) {
            inFlight.values.forEach { it.deferred.cancel() }
            inFlight.clear()
            val retainedPages = if (level == MemoryPressureLevel.MODERATE) 1 else 0
            pages.trimToSize(retainedPages)
        }
    }

    /**
     * Pagination is shared across the current and prefetched chapter compositions. A caller
     * leaving composition only cancels its await; the expensive EPUB layout continues and is
     * reused when that chapter becomes current a moment later.
     */
    internal fun getOrLoad(
        key: PaginationCacheKey,
        chapter: ReaderChapter,
        prefetch: Boolean,
        loader: suspend (
            publishPartial: (List<ReaderPage>) -> Unit,
            awaitPermit: suspend () -> Unit,
        ) -> List<ReaderPage>,
    ): StateFlow<ReaderPaginationSnapshot> = synchronized(lock) {
        pages[key]?.let {
            return@synchronized MutableStateFlow(
                ReaderPaginationSnapshot(it, isComplete = true),
            ).asStateFlow()
        }
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
                return@synchronized target.snapshots
            }
        }
        inFlight[key]?.let { return@synchronized it.snapshots }
        val snapshots = MutableStateFlow(ReaderPaginationSnapshot())
        lateinit var deferred: Deferred<List<ReaderPage>>
        deferred = paginationScope.async(start = CoroutineStart.LAZY) {
            val startedAt = System.nanoTime()
            try {
                withContext(Dispatchers.IO) { diskCache?.read(key, chapter) }?.let { restored ->
                    synchronized(lock) { pages[key] = restored }
                    snapshots.value = ReaderPaginationSnapshot(restored, isComplete = true)
                    DiagnosticLog.record(
                        DiagnosticLog.Category.PAGINATION,
                        "restore",
                        elapsedMs = startedAt.elapsedMilliseconds(),
                        outcome = "disk_cache",
                        details = mapOf(
                            "book" to key.bookUuid.take(8),
                            "chapter" to key.chapterId,
                            "pages" to restored.size,
                            "prefetch" to prefetch,
                        ),
                    )
                    return@async restored
                }
                val measured = loader(
                    { partial ->
                        if (partial.isNotEmpty()) {
                            snapshots.value = ReaderPaginationSnapshot(partial, isComplete = false)
                        }
                    },
                    ::awaitPermit,
                )
                coroutineContext.ensureActive()
                synchronized(lock) { pages[key] = measured }
                snapshots.value = ReaderPaginationSnapshot(measured, isComplete = true)
                diskCache?.let { cache -> cacheScope.launch { cache.write(key, measured) } }
                DiagnosticLog.record(
                    DiagnosticLog.Category.PAGINATION,
                    "measure",
                    elapsedMs = startedAt.elapsedMilliseconds(),
                    outcome = "success",
                    details = mapOf(
                        "book" to key.bookUuid.take(8),
                        "chapter" to key.chapterId,
                        "paragraphs" to chapter.paragraphs.size,
                        "pages" to measured.size,
                        "prefetch" to prefetch,
                    ),
                )
                measured
            } catch (throwable: Throwable) {
                if (throwable !is CancellationException) {
                    val failure = throwable.toDiagnosticFailure()
                    DiagnosticLog.record(
                        DiagnosticLog.Category.PAGINATION,
                        "failed",
                        elapsedMs = startedAt.elapsedMilliseconds(),
                        outcome = failure.outcome,
                        details = mapOf(
                            "book" to key.bookUuid.take(8),
                            "chapter" to key.chapterId,
                            "paragraphs" to chapter.paragraphs.size,
                            "prefetch" to prefetch,
                            "reason" to failure.reason,
                        ),
                    )
                }
                throw throwable
            } finally {
                synchronized(lock) {
                    if (inFlight[key]?.deferred === deferred) inFlight.remove(key)
                }
            }
        }
        val state = snapshots.asStateFlow()
        inFlight[key] = PaginationLoad(deferred, prefetch, state)
        deferred.start()
        state
    }

    internal fun close() {
        MemoryPressureRegistry.unregister(this)
        sessionJob.cancel()
        paginationDispatcher.close()
        synchronized(lock) {
            inFlight.clear()
            pages.clear()
        }
    }

    private data class PaginationLoad(
        val deferred: Deferred<List<ReaderPage>>,
        val prefetch: Boolean,
        val snapshots: StateFlow<ReaderPaginationSnapshot>,
    )
}

private fun Long.elapsedMilliseconds(): Long = (System.nanoTime() - this) / 1_000_000L

private class MeasuredReaderPaginator(
    private val measurer: TextMeasurer,
    private val density: androidx.compose.ui.unit.Density,
) {
    suspend fun paginate(
        chapter: ReaderChapter,
        spec: ReaderLayoutSpec,
        family: androidx.compose.ui.text.font.FontFamily,
        showRegularChapterTitle: Boolean,
        publishPartial: (List<ReaderPage>) -> Unit = {},
        awaitPermit: suspend () -> Unit = {},
    ): List<ReaderPage> {
        chapter.fullPageImageParagraph()?.let { image ->
            val block = DocumentBlock(
                paragraphIndex = image.index,
                fullText = image.text,
                visibleText = "",
                continuation = false,
                bottomSpacing = false,
                kind = ParagraphKind.IMAGE,
                resourcePath = image.resourcePath,
                mediaType = image.mediaType,
                intrinsicWidth = image.intrinsicWidth,
                intrinsicHeight = image.intrinsicHeight,
                imageWidthDp = spec.viewportWidthDp,
                imageHeightDp = spec.viewportHeightDp,
                isFullPageImage = true,
                cropImageToFill = image.cropImageToFill,
            )
            return listOf(ReaderPage(0, chapter.index, chapter.title, false, listOf(block)))
        }
        val contentWidthDp = (spec.viewportWidthDp - spec.horizontalMarginDp * 2f)
            .coerceAtLeast(MIN_BODY_WIDTH_DP)
        val widthPx = with(density) { contentWidthDp.dp.roundToPx() }
        val spacingPx = with(density) { (spec.fontSizeSp * PARAGRAPH_SPACING_EM).dp.toPx() }
        val pages = mutableListOf<ReaderPage>()
        var blocks = mutableListOf<DocumentBlock>()
        var usedHeightPx = 0f
        var opening = true
        val openingHeading = splitReaderChapterHeading(chapter.title)
        val openingTitleLineCount = if (openingHeading.name.isEmpty()) {
            0
        } else {
            measurer.measure(
                text = openingHeading.name,
                style = TextStyle(
                    fontSize = OPENING_TITLE_FONT_SIZE_SP.sp,
                    lineHeight = OPENING_TITLE_LINE_HEIGHT_SP.sp,
                    fontFamily = family,
                ),
                softWrap = true,
                maxLines = OPENING_TITLE_MAX_LINES,
                constraints = Constraints(maxWidth = widthPx),
            ).lineCount
        }

        fun bodyHeightPx(): Float = availableBodyHeightPx(
            spec = spec,
            opening = opening,
            heading = openingHeading,
            openingTitleLineCount = openingTitleLineCount,
            showRegularChapterTitle = showRegularChapterTitle,
        )
        fun flush() {
            if (blocks.isEmpty()) return
            pages += ReaderPage(pages.size, chapter.index, chapter.title, opening, blocks.toList())
            blocks = mutableListOf()
            usedHeightPx = 0f
            opening = false
            if (
                pages.size == FIRST_READABLE_PAGE_BATCH_SIZE ||
                pages.size % SUBSEQUENT_READABLE_PAGE_BATCH_SIZE == 0
            ) {
                publishPartial(pages.toList())
            }
        }

        chapter.contentParagraphs().forEach { paragraph ->
            coroutineContext.ensureActive()
            awaitPermit()
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
                    isFullPageImage = paragraph.isFullPageImage,
                    cropImageToFill = paragraph.cropImageToFill,
                )
                usedHeightPx += imageHeightPx + spacingPx
                if (bodyHeightPx() - usedHeightPx < spacingPx) flush()
                return@forEach
            }
            val sourceText = paragraph.text
            var remainingStart = 0
            var continuation = false
            while (remainingStart < sourceText.length) {
                coroutineContext.ensureActive()
                awaitPermit()
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
                val remainingLength = sourceText.length - remainingStart
                var measuredLength = remainingLength.coerceAtMost(MEASUREMENT_WINDOW_CHARS)
                var measuredSpans: List<ReaderTextSpan>
                var layout: TextLayoutResult
                while (true) {
                    coroutineContext.ensureActive()
                    measuredSpans = paragraph.spans.sliceForText(
                        remainingStart,
                        remainingStart + measuredLength,
                    )
                    layout = measurer.measure(
                        // Only allocate the bounded measurement window. Keeping a shrinking
                        // `remaining` String copied the complete tail after every page and made a
                        // one-paragraph chapter O(n²) in both allocations and copied characters.
                        text = readerAnnotatedText(
                            sourceText.substring(remainingStart, remainingStart + measuredLength),
                            measuredSpans,
                        ),
                        style = style,
                        overflow = TextOverflow.Clip,
                        softWrap = true,
                        maxLines = maxMeasuredLines,
                        constraints = Constraints(maxWidth = widthPx),
                    )
                    if (
                        measuredLength == remainingLength ||
                        layout.didOverflowHeight ||
                        layout.size.height > availablePx
                    ) break
                    measuredLength = (measuredLength * 2).coerceAtMost(remainingLength)
                }
                val textHeightPx = layout.size.height.toFloat()
                val measuredWholeRemainder = measuredLength == remainingLength && !layout.didOverflowHeight
                if (measuredWholeRemainder && textHeightPx + spacingPx <= availablePx) {
                    blocks += DocumentBlock(
                        paragraph.index,
                        paragraph.text,
                        sourceText.substring(remainingStart),
                        continuation,
                        bottomSpacing = true,
                        spans = measuredSpans,
                        textStart = remainingStart,
                    )
                    usedHeightPx += textHeightPx + spacingPx
                    remainingStart = sourceText.length
                    continue
                }
                if (measuredWholeRemainder && textHeightPx <= availablePx) {
                    // At a page boundary paragraph spacing is unnecessary and would
                    // otherwise push the last baseline below the footer.
                    blocks += DocumentBlock(
                        paragraph.index,
                        paragraph.text,
                        sourceText.substring(remainingStart),
                        continuation,
                        bottomSpacing = false,
                        spans = measuredSpans,
                        textStart = remainingStart,
                    )
                    remainingStart = sourceText.length
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
                val rawVisible = sourceText.substring(remainingStart, remainingStart + end)
                val visible = rawVisible.trimEnd().ifEmpty { rawVisible }
                blocks += DocumentBlock(
                    paragraph.index,
                    paragraph.text,
                    visible,
                    continuation,
                    bottomSpacing = false,
                    spans = paragraph.spans.sliceForText(remainingStart, remainingStart + visible.length),
                    textStart = remainingStart,
                )
                usedHeightPx += ceil(layout.getLineBottom(fittingLine))
                remainingStart += end
                while (remainingStart < sourceText.length && sourceText[remainingStart].isWhitespace()) {
                    remainingStart++
                }
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
        openingTitleLineCount: Int,
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
                OPENING_TITLE_LINE_HEIGHT_SP * openingTitleLineCount
        } else if (showRegularChapterTitle) {
            REGULAR_TITLE_LINE_HEIGHT_SP
        } else {
            0f
        }
        (viewport - fixedDp.dp.toPx() - headerSp.sp.toPx()).coerceAtLeast(MIN_BODY_HEIGHT_DP.dp.toPx())
    }
}

private const val FIRST_READABLE_PAGE_BATCH_SIZE = 4
private const val SUBSEQUENT_READABLE_PAGE_BATCH_SIZE = 8
private const val MEASUREMENT_WINDOW_CHARS = 512
private const val MIN_BODY_WIDTH_DP = 160f
private const val MIN_BODY_HEIGHT_DP = 120f
private const val PARAGRAPH_SPACING_EM = 0.9f
private const val OPENING_ORDINAL_LINE_HEIGHT_SP = 22f
private const val OPENING_TITLE_FONT_SIZE_SP = 28f
private const val OPENING_TITLE_LINE_HEIGHT_SP = 36f
private const val OPENING_TITLE_MAX_LINES = 2
private const val REGULAR_TITLE_LINE_HEIGHT_SP = 20f
