package com.kixyu9527.kixyubook.core.reader.engine

import android.graphics.Typeface
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan

data class ReaderRenderPalette(
    val background: Color,
    val body: Color,
    val title: Color,
    val accent: Color,
    val secondary: Color,
)

@Composable
fun ReaderScrollRenderer(
    chapter: ReaderChapter,
    listState: LazyListState,
    spec: ReaderLayoutSpec,
    palette: ReaderRenderPalette,
    fontPath: String?,
    onTapFraction: (Float) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
    topInsetDp: Float,
    bottomInsetDp: Float,
    epubPath: String? = null,
    modifier: Modifier = Modifier,
    highlightQuery: String = "",
) {
    val family = rememberReaderFont(fontPath)
    val contentParagraphs = remember(chapter) { chapter.contentParagraphs() }
    var selectionVersion by remember(chapter.id) { mutableIntStateOf(0) }
    var selectionActive by remember(chapter.id) { androidx.compose.runtime.mutableStateOf(false) }
    val handleTap: (Float) -> Unit = { fraction ->
        if (selectionActive) {
            selectionActive = false
            selectionVersion++
        } else {
            onTapFraction(fraction)
        }
    }
    key(selectionVersion) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = modifier.readerTapInput(handleTap) { selectionActive = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = spec.horizontalMarginDp.dp,
                    end = spec.horizontalMarginDp.dp,
                    top = (topInsetDp + 20f).dp,
                    bottom = (bottomInsetDp + 8f).dp,
                ),
            ) {
                item {
                    ReaderChapterOpeningTitle(chapter.title, palette, family)
                    Spacer(Modifier.height(24.dp))
                }
                itemsIndexed(contentParagraphs, key = { _, paragraph -> paragraph.id }) { _, paragraph ->
                    if (paragraph.kind == ParagraphKind.IMAGE) {
                        val layout = standardizedReaderImageLayout(
                            availableWidthDp = spec.viewportWidthDp - spec.horizontalMarginDp * 2f,
                            intrinsicWidth = paragraph.intrinsicWidth,
                            intrinsicHeight = paragraph.intrinsicHeight,
                        )
                        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ReaderEpubImage(
                                epubPath = epubPath,
                                resourcePath = paragraph.resourcePath,
                                altText = paragraph.text,
                                layout = layout,
                                placeholderColor = palette.secondary,
                                onTapFraction = handleTap,
                            )
                        }
                    } else {
                        ReaderBodyText(
                            paragraph.text,
                            spec,
                            palette.body,
                            family,
                            spans = paragraph.spans,
                            accentColor = palette.accent,
                            backgroundColor = palette.background,
                            highlightQuery = highlightQuery,
                            highlightColor = palette.accent,
                        )
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("· 本章完 ·", color = palette.secondary)
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (hasPrevious) androidx.compose.material3.TextButton(onClick = onPreviousChapter) { Text("上一章", color = palette.accent) }
                            if (hasNext) androidx.compose.material3.TextButton(onClick = onNextChapter) { Text("下一章", color = palette.accent) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderPageRenderer(
    page: ReaderPage,
    spec: ReaderLayoutSpec,
    palette: ReaderRenderPalette,
    fontPath: String?,
    onTapFraction: (Float) -> Unit,
    epubPath: String? = null,
    modifier: Modifier = Modifier,
    showRegularChapterTitle: Boolean = true,
    highlightQuery: String = "",
    pageNumber: String? = null,
) {
    val family = rememberReaderFont(fontPath)
    var selectionVersion by remember(page) { mutableIntStateOf(0) }
    var selectionActive by remember(page) { androidx.compose.runtime.mutableStateOf(false) }
    val handleTap: (Float) -> Unit = { fraction ->
        if (selectionActive) {
            selectionActive = false
            selectionVersion++
        } else {
            onTapFraction(fraction)
        }
    }
    key(selectionVersion) {
        SelectionContainer {
            Column(
                modifier.fillMaxSize()
                    .readerTapInput(handleTap) { selectionActive = true }
                    .padding(
                        start = spec.horizontalMarginDp.dp,
                        top = ReaderPageMetrics.topPaddingDp.dp,
                        end = spec.horizontalMarginDp.dp,
                        bottom = ReaderPageMetrics.bottomPaddingDp.dp,
                    ),
            ) {
                if (page.isChapterOpening) {
                    Spacer(Modifier.height(ReaderPageMetrics.openingTopDp.dp))
                    ReaderChapterOpeningTitle(page.chapterTitle, palette, family)
                    Spacer(Modifier.height(ReaderPageMetrics.openingGapDp.dp))
                } else if (showRegularChapterTitle) {
                    Text(page.chapterTitle, color = palette.secondary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Spacer(Modifier.height(ReaderPageMetrics.regularGapDp.dp))
                }
                page.blocks.forEach { block ->
                    if (block.kind == ParagraphKind.IMAGE) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ReaderEpubImage(
                                epubPath = epubPath,
                                resourcePath = block.resourcePath,
                                altText = block.fullText,
                                layout = ReaderImageLayout(
                                    block.imageWidthDp,
                                    block.imageHeightDp,
                                    standardizedReaderImageLayout(
                                        spec.viewportWidthDp - spec.horizontalMarginDp * 2f,
                                        block.intrinsicWidth,
                                        block.intrinsicHeight,
                                    ).sizeClass,
                                ),
                                placeholderColor = palette.secondary,
                                onTapFraction = handleTap,
                            )
                        }
                    } else {
                        ReaderBodyText(
                            block.visibleText,
                            spec,
                            palette.body,
                            family,
                            spans = block.spans,
                            accentColor = palette.accent,
                            backgroundColor = palette.background,
                            indent = !block.continuation,
                            bottomSpacing = block.bottomSpacing,
                            highlightQuery = highlightQuery,
                            highlightColor = palette.accent,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.fillMaxWidth().height(ReaderPageMetrics.footerHeightDp.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    pageNumber?.let { value ->
                        Text(
                            text = value,
                            color = palette.secondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderChapterOpeningTitle(
    title: String,
    palette: ReaderRenderPalette,
    family: FontFamily,
) {
    val heading = remember(title) { splitReaderChapterHeading(title) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        heading.ordinal?.let { ordinal ->
            Text(
                ordinal,
                color = palette.secondary,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = family,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            if (heading.name.isNotEmpty()) {
                Spacer(Modifier.height(ReaderPageMetrics.openingOrdinalGapDp.dp))
            }
        }
        if (heading.name.isNotEmpty()) {
            Text(
                heading.name,
                color = palette.title,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = family,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReaderBodyText(
    text: String,
    spec: ReaderLayoutSpec,
    color: Color,
    family: FontFamily,
    spans: List<ReaderTextSpan> = emptyList(),
    accentColor: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified,
    indent: Boolean = true,
    bottomSpacing: Boolean = true,
    highlightQuery: String = "",
    highlightColor: Color = Color.Transparent,
) = Text(
    text = readerAnnotatedText(
        text = text,
        spans = spans,
        accentColor = accentColor,
        backgroundColor = backgroundColor,
        highlightQuery = highlightQuery,
        highlightColor = highlightColor,
    ),
    color = color,
    style = readerBodyTextStyle(spec, family, indent),
    modifier = Modifier.fillMaxWidth()
        .padding(bottom = if (bottomSpacing) (spec.fontSizeSp * 0.9f).dp else 0.dp),
)

/** Observes short taps without consuming long presses used by text selection. */
private fun Modifier.readerTapInput(
    onTapFraction: (Float) -> Unit,
    onSelectionGestureFinished: () -> Unit,
): Modifier = pointerInput(onTapFraction, onSelectionGestureFinished) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
            val delta = up.position - down.position
            val isShortTap = up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
            val stayedInPlace = delta.x * delta.x + delta.y * delta.y <=
                viewConfiguration.touchSlop * viewConfiguration.touchSlop
            if (isShortTap && stayedInPlace) {
                onTapFraction(up.position.x / size.width.coerceAtLeast(1))
            } else if (!isShortTap && stayedInPlace) {
                onSelectionGestureFinished()
            }
        }
    }

internal fun String.highlighted(query: String, color: Color) =
    readerAnnotatedText(this, emptyList(), highlightQuery = query, highlightColor = color)

@Composable
internal fun rememberReaderFont(path: String?): FontFamily = remember(path) {
    path?.let { runCatching { FontFamily(Typeface.createFromFile(it)) }.getOrNull() } ?: FontFamily.Default
}

internal fun readerBodyTextStyle(spec: ReaderLayoutSpec, family: FontFamily, indent: Boolean) = TextStyle(
    fontFamily = family,
    fontSize = spec.fontSizeSp.sp,
    lineHeight = (spec.fontSizeSp * spec.lineHeightMultiplier).sp,
    letterSpacing = spec.letterSpacingEm.em,
    textIndent = TextIndent(firstLine = if (indent) 2.em else 0.em),
    textAlign = TextAlign.Justify,
)
