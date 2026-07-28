package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.kixyu9527.kixyubook.core.common.model.ReaderInlineStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSemanticColor
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan

internal fun readerAnnotatedText(
    text: String,
    spans: List<ReaderTextSpan>,
    accentColor: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified,
    highlightQuery: String = "",
    highlightColor: Color = Color.Unspecified,
): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    spans.forEach { span ->
        val start = span.start.coerceIn(0, text.length)
        val end = span.end.coerceIn(start, text.length)
        if (start == end || (span.styles.isEmpty() && span.foreground == null && span.background == null)) {
            return@forEach
        }
        val decorations = buildList {
            if (ReaderInlineStyle.UNDERLINE in span.styles) add(TextDecoration.Underline)
            if (ReaderInlineStyle.STRIKETHROUGH in span.styles) add(TextDecoration.LineThrough)
        }
        val elevated = ReaderInlineStyle.SUPERSCRIPT in span.styles
        val lowered = ReaderInlineStyle.SUBSCRIPT in span.styles
        builder.addStyle(
            SpanStyle(
                color = span.foreground?.readerColor(accentColor, backgroundColor)
                    ?: accentColor.takeIf {
                        ReaderInlineStyle.ACCENT in span.styles && it != Color.Unspecified
                    }
                    ?: Color.Unspecified,
                background = (span.background?.readerHighlightColor(accentColor, backgroundColor)
                    ?: accentColor.takeIf {
                        ReaderInlineStyle.HIGHLIGHT in span.styles && it != Color.Unspecified
                    })?.takeUnless { it == Color.Unspecified }?.copy(alpha = .18f) ?: Color.Unspecified,
                fontWeight = FontWeight.Bold.takeIf { ReaderInlineStyle.BOLD in span.styles },
                fontStyle = FontStyle.Italic.takeIf { ReaderInlineStyle.ITALIC in span.styles },
                fontFamily = FontFamily.Monospace.takeIf { ReaderInlineStyle.MONOSPACE in span.styles },
                fontFeatureSettings = "smcp".takeIf { ReaderInlineStyle.SMALL_CAPS in span.styles },
                textDecoration = decorations.takeIf(List<TextDecoration>::isNotEmpty)
                    ?.let(TextDecoration::combine),
                baselineShift = when {
                    elevated -> BaselineShift.Superscript
                    lowered -> BaselineShift.Subscript
                    else -> null
                },
                fontSize = if (elevated || lowered) .82.em else TextUnit.Unspecified,
            ),
            start,
            end,
        )
    }
    if (highlightQuery.isNotBlank() && highlightColor != Color.Unspecified) {
        var cursor = 0
        while (cursor < text.length) {
            val match = text.indexOf(highlightQuery, cursor, ignoreCase = true)
            if (match < 0) break
            builder.addStyle(
                SpanStyle(background = highlightColor.copy(alpha = .28f)),
                match,
                match + highlightQuery.length,
            )
            cursor = match + highlightQuery.length
        }
    }
    return builder.toAnnotatedString()
}

internal fun List<ReaderTextSpan>.sliceForText(start: Int, end: Int): List<ReaderTextSpan> {
    if (isEmpty() || start >= end) return emptyList()
    return mapNotNull { span ->
        val overlapStart = maxOf(span.start, start)
        val overlapEnd = minOf(span.end, end)
        if (overlapStart >= overlapEnd) null else ReaderTextSpan(
            start = overlapStart - start,
            end = overlapEnd - start,
            styles = span.styles,
            foreground = span.foreground,
            background = span.background,
        )
    }
}

private fun ReaderSemanticColor.readerHighlightColor(accent: Color, background: Color): Color =
    if (this == ReaderSemanticColor.NEUTRAL) {
        if (background != Color.Unspecified && background.luminance() < .45f) Color.White else Color.Black
    } else {
        readerColor(accent, background)
    }

/** High-contrast, stable tones used across every EPUB regardless of publisher shade. */
private fun ReaderSemanticColor.readerColor(accent: Color, background: Color): Color {
    if (this == ReaderSemanticColor.ACCENT) return accent
    if (this == ReaderSemanticColor.NEUTRAL) return Color.Unspecified
    val dark = background != Color.Unspecified && background.luminance() < .45f
    return if (dark) {
        when (this) {
            ReaderSemanticColor.RED -> Color(0xFFF2B8B5)
            ReaderSemanticColor.ORANGE -> Color(0xFFFFB77D)
            ReaderSemanticColor.YELLOW -> Color(0xFFE5C44F)
            ReaderSemanticColor.GREEN -> Color(0xFFA9D18E)
            ReaderSemanticColor.CYAN -> Color(0xFF4FD8EB)
            ReaderSemanticColor.BLUE -> Color(0xFF9ECAFF)
            ReaderSemanticColor.PURPLE -> Color(0xFFD0BCFF)
            ReaderSemanticColor.MAGENTA -> Color(0xFFFFB0C8)
            ReaderSemanticColor.ACCENT, ReaderSemanticColor.NEUTRAL -> Color.Unspecified
        }
    } else {
        when (this) {
            ReaderSemanticColor.RED -> Color(0xFFB3261E)
            ReaderSemanticColor.ORANGE -> Color(0xFF8B5000)
            ReaderSemanticColor.YELLOW -> Color(0xFF6D5E00)
            ReaderSemanticColor.GREEN -> Color(0xFF386A20)
            ReaderSemanticColor.CYAN -> Color(0xFF006874)
            ReaderSemanticColor.BLUE -> Color(0xFF0061A4)
            ReaderSemanticColor.PURPLE -> Color(0xFF6750A4)
            ReaderSemanticColor.MAGENTA -> Color(0xFF984061)
            ReaderSemanticColor.ACCENT, ReaderSemanticColor.NEUTRAL -> Color.Unspecified
        }
    }
}
