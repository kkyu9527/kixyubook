package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

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
): List<ReaderPage> {
    val measurer = rememberTextMeasurer(cacheSize = TEXT_MEASURE_CACHE_SIZE)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val family = rememberReaderFont(fontPath)
    return remember(chapter, spec, fontPath, family, density.density, density.fontScale, layoutDirection) {
        MeasuredReaderPaginator(measurer, density).paginate(chapter, spec, family)
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
    ): List<ReaderPage> {
        val widthPx = with(density) {
            (spec.viewportWidthDp - spec.horizontalMarginDp * 2f).coerceAtLeast(MIN_BODY_WIDTH_DP).dp.roundToPx()
        }
        val spacingPx = with(density) { (spec.fontSizeSp * PARAGRAPH_SPACING_EM).dp.toPx() }
        val pages = mutableListOf<ReaderPage>()
        var blocks = mutableListOf<DocumentBlock>()
        var usedHeightPx = 0f
        var opening = true

        fun bodyHeightPx(): Float = availableBodyHeightPx(spec, opening)
        fun flush() {
            if (blocks.isEmpty()) return
            pages += ReaderPage(pages.size, chapter.index, chapter.title, opening, blocks.toList())
            blocks = mutableListOf()
            usedHeightPx = 0f
            opening = false
        }

        chapter.contentParagraphs().forEach { paragraph ->
            var remaining = paragraph.text
            var continuation = false
            while (remaining.isNotEmpty()) {
                val availablePx = (bodyHeightPx() - usedHeightPx).coerceAtLeast(0f)
                val style = readerBodyTextStyle(spec, family, indent = !continuation)
                val layout = measurer.measure(
                    text = AnnotatedString(remaining),
                    style = style,
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                    maxLines = Int.MAX_VALUE,
                    constraints = Constraints(maxWidth = widthPx),
                )
                val textHeightPx = layout.size.height.toFloat()
                if (textHeightPx + spacingPx <= availablePx) {
                    blocks += DocumentBlock(paragraph.index, paragraph.text, remaining, continuation, bottomSpacing = true)
                    usedHeightPx += textHeightPx + spacingPx
                    remaining = ""
                    continue
                }
                if (textHeightPx <= availablePx) {
                    // At a page boundary paragraph spacing is unnecessary and would
                    // otherwise push the last baseline below the footer.
                    blocks += DocumentBlock(paragraph.index, paragraph.text, remaining, continuation, bottomSpacing = false)
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
                blocks += DocumentBlock(paragraph.index, paragraph.text, visible, continuation, bottomSpacing = false)
                usedHeightPx += ceil(layout.getLineBottom(fittingLine))
                remaining = remaining.substring(end).trimStart()
                continuation = true
                flush()
            }
        }
        flush()
        return pages.ifEmpty { listOf(ReaderPage(0, chapter.index, chapter.title, true, emptyList())) }
    }

    private fun availableBodyHeightPx(spec: ReaderLayoutSpec, opening: Boolean): Float = with(density) {
        val viewport = spec.viewportHeightDp.dp.toPx()
        val fixedDp = if (opening) {
            ReaderPageMetrics.verticalPaddingDp * 2 + ReaderPageMetrics.openingTopDp +
                ReaderPageMetrics.openingGapDp + ReaderPageMetrics.footerGapDp +
                ReaderPageMetrics.footerHeightDp + ReaderPageMetrics.safetyDp
        } else {
            ReaderPageMetrics.verticalPaddingDp * 2 + ReaderPageMetrics.regularGapDp +
                ReaderPageMetrics.footerGapDp + ReaderPageMetrics.footerHeightDp + ReaderPageMetrics.safetyDp
        }
        val headerSp = if (opening) {
            OPENING_TITLE_LINE_HEIGHT_SP * OPENING_TITLE_MAX_LINES
        } else {
            REGULAR_TITLE_LINE_HEIGHT_SP
        }
        (viewport - fixedDp.dp.toPx() - headerSp.sp.toPx()).coerceAtLeast(MIN_BODY_HEIGHT_DP.dp.toPx())
    }
}

private const val TEXT_MEASURE_CACHE_SIZE = 64
private const val MIN_BODY_WIDTH_DP = 160f
private const val MIN_BODY_HEIGHT_DP = 120f
private const val PARAGRAPH_SPACING_EM = 0.9f
private const val OPENING_TITLE_LINE_HEIGHT_SP = 44f
private const val OPENING_TITLE_MAX_LINES = 2f
private const val REGULAR_TITLE_LINE_HEIGHT_SP = 20f
