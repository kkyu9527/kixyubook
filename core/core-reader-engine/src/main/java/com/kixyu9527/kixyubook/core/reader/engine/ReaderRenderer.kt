package com.kixyu9527.kixyubook.core.reader.engine

import android.graphics.Typeface
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind

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
    editable: Boolean,
    onEditParagraph: (Int, String) -> Unit,
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
    LazyColumn(
        state = listState,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { onTapFraction(it.x / size.width.coerceAtLeast(1)) }
        },
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
                        onTapFraction = onTapFraction,
                    )
                }
            } else {
                ReaderBodyText(paragraph.text, spec, palette.body, family, editable, { onEditParagraph(paragraph.index, paragraph.text) }, onTapFraction, highlightQuery = highlightQuery, highlightColor = palette.accent)
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

@Composable
fun ReaderPageRenderer(
    page: ReaderPage,
    spec: ReaderLayoutSpec,
    palette: ReaderRenderPalette,
    fontPath: String?,
    editable: Boolean,
    onEditParagraph: (Int, String) -> Unit,
    onTapFraction: (Float) -> Unit,
    epubPath: String? = null,
    modifier: Modifier = Modifier,
    showRegularChapterTitle: Boolean = true,
    highlightQuery: String = "",
) {
    val family = rememberReaderFont(fontPath)
    Column(
        modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { onTapFraction(it.x / size.width.coerceAtLeast(1)) }
            }
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
                        onTapFraction = onTapFraction,
                    )
                }
            } else {
                ReaderBodyText(
                    block.visibleText,
                    spec,
                    palette.body,
                    family,
                    editable,
                    { onEditParagraph(block.paragraphIndex, block.fullText) },
                    onTapFraction,
                    indent = !block.continuation,
                    bottomSpacing = block.bottomSpacing,
                    highlightQuery = highlightQuery,
                    highlightColor = palette.accent,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(ReaderPageMetrics.footerHeightDp.dp))
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
    editable: Boolean,
    onEdit: () -> Unit,
    onTapFraction: (Float) -> Unit,
    indent: Boolean = true,
    bottomSpacing: Boolean = true,
    highlightQuery: String = "",
    highlightColor: Color = Color.Transparent,
) {
    Text(
        text = text.highlighted(highlightQuery, highlightColor),
        color = color,
        style = readerBodyTextStyle(spec, family, indent),
        modifier = Modifier.fillMaxWidth()
            .pointerInput(editable) {
                detectTapGestures(
                    onTap = { onTapFraction(it.x / size.width.coerceAtLeast(1)) },
                    onLongPress = { if (editable) onEdit() },
                )
            }
            .padding(bottom = if (bottomSpacing) (spec.fontSizeSp * 0.9f).dp else 0.dp),
    )
}

internal fun String.highlighted(query: String, color: Color) = buildAnnotatedString {
    val source = this@highlighted
    if (query.isBlank()) {
        append(source)
        return@buildAnnotatedString
    }
    var cursor = 0
    while (cursor < source.length) {
        val match = source.indexOf(query, cursor, ignoreCase = true)
        if (match < 0) {
            append(source.substring(cursor))
            break
        }
        append(source.substring(cursor, match))
        withStyle(SpanStyle(background = color.copy(alpha = .28f))) {
            append(source.substring(match, match + query.length))
        }
        cursor = match + query.length
    }
}

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
