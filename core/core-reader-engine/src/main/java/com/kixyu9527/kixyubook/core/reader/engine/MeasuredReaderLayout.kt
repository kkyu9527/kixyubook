package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
): List<ReaderPage> {
    val measurer = rememberTextMeasurer(cacheSize = TEXT_MEASURE_CACHE_SIZE)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val family = rememberReaderFont(fontPath)
    val paginationMutex = remember(measurer) { Mutex() }
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
        mutableStateOf(MeasuredReaderPageCache[cacheKey].orEmpty())
    }
    LaunchedEffect(cacheKey, chapter, family) {
        if (pages.isNotEmpty()) return@LaunchedEffect
        val measuredPages = withContext(Dispatchers.Default) {
            paginationMutex.withLock {
                MeasuredReaderPaginator(measurer, density)
                    .paginate(chapter, spec, family, showRegularChapterTitle)
            }
        }
        MeasuredReaderPageCache[cacheKey] = measuredPages
        pages = measuredPages
    }
    return pages
}

private data class PaginationCacheKey(
    val chapterId: Long,
    val spec: ReaderLayoutSpec,
    val fontPath: String?,
    val showRegularChapterTitle: Boolean,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
)

private object MeasuredReaderPageCache {
    private val lock = Any()
    private val pages = object : LinkedHashMap<PaginationCacheKey, List<ReaderPage>>(
        PAGINATION_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PaginationCacheKey, List<ReaderPage>>) =
            size > PAGINATION_CACHE_SIZE
    }

    operator fun get(key: PaginationCacheKey): List<ReaderPage>? = synchronized(lock) { pages[key] }

    operator fun set(key: PaginationCacheKey, value: List<ReaderPage>) {
        synchronized(lock) { pages[key] = value }
    }
}

private class MeasuredReaderPaginator(
    private val measurer: TextMeasurer,
    private val density: androidx.compose.ui.unit.Density,
) {
    fun paginate(
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
                val availablePx = (bodyHeightPx() - usedHeightPx).coerceAtLeast(0f)
                val style = readerBodyTextStyle(spec, family, indent = !continuation)
                val remainingSpans = paragraph.spans.sliceForText(
                    remainingStart,
                    remainingStart + remaining.length,
                )
                val layout = measurer.measure(
                    text = readerAnnotatedText(remaining, remainingSpans),
                    style = style,
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                    maxLines = Int.MAX_VALUE,
                    constraints = Constraints(maxWidth = widthPx),
                )
                val textHeightPx = layout.size.height.toFloat()
                if (textHeightPx + spacingPx <= availablePx) {
                    blocks += DocumentBlock(
                        paragraph.index,
                        paragraph.text,
                        remaining,
                        continuation,
                        bottomSpacing = true,
                        spans = remainingSpans,
                    )
                    usedHeightPx += textHeightPx + spacingPx
                    remaining = ""
                    continue
                }
                if (textHeightPx <= availablePx) {
                    // At a page boundary paragraph spacing is unnecessary and would
                    // otherwise push the last baseline below the footer.
                    blocks += DocumentBlock(
                        paragraph.index,
                        paragraph.text,
                        remaining,
                        continuation,
                        bottomSpacing = false,
                        spans = remainingSpans,
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
                val end = layout.getLineEnd(fittingLine, visibleEnd = true).coerceIn(1, remaining.length)
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

private const val TEXT_MEASURE_CACHE_SIZE = 64
private const val PAGINATION_CACHE_SIZE = 8
private const val MIN_BODY_WIDTH_DP = 160f
private const val MIN_BODY_HEIGHT_DP = 120f
private const val PARAGRAPH_SPACING_EM = 0.9f
private const val OPENING_ORDINAL_LINE_HEIGHT_SP = 22f
private const val OPENING_TITLE_LINE_HEIGHT_SP = 36f
private const val REGULAR_TITLE_LINE_HEIGHT_SP = 20f
