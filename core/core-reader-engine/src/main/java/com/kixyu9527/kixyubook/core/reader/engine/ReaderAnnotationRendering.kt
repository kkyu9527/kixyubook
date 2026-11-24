package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.kixyu9527.kixyubook.core.common.model.ReaderAnnotation
import com.kixyu9527.kixyubook.core.common.model.ReaderAnnotationStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderInlineStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSemanticColor
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan

/** Immutable per-snapshot lookup shared by visible and neighbouring pages, never process-global. */
internal class ReaderAnnotationIndex(val source: List<ReaderAnnotation>) {
    private val paragraphs = source.groupBy { it.chapterIndex to it.paragraphIndex }

    fun spans(
        chapterIndex: Int,
        paragraphIndex: Int,
        sourceText: String,
        textStart: Int,
        visibleLength: Int,
    ): List<ReaderTextSpan> {
        val annotations = paragraphs[chapterIndex to paragraphIndex] ?: return emptyList()
        val visibleEnd = textStart + visibleLength
        return annotations.mapNotNull { annotation ->
            if (annotation.startOffset < 0 || annotation.endOffset > sourceText.length ||
                annotation.startOffset >= annotation.endOffset ||
                annotation.endOffset - annotation.startOffset != annotation.exactText.length ||
                !sourceText.regionMatches(annotation.startOffset, annotation.exactText, 0, annotation.exactText.length)
            ) return@mapNotNull null
            val start = maxOf(annotation.startOffset, textStart)
            val end = minOf(annotation.endOffset, visibleEnd)
            if (start >= end) return@mapNotNull null
            ReaderTextSpan(
                start = start - textStart,
                end = end - textStart,
                styles = buildSet {
                    add(when (annotation.style) {
                        ReaderAnnotationStyle.HIGHLIGHT -> ReaderInlineStyle.HIGHLIGHT
                        ReaderAnnotationStyle.UNDERLINE -> ReaderInlineStyle.UNDERLINE
                    })
                    if (annotation.note.isNotBlank()) add(ReaderInlineStyle.NOTE)
                },
                background = ReaderSemanticColor.YELLOW.takeIf { annotation.style == ReaderAnnotationStyle.HIGHLIGHT },
            )
        }
    }
}

private val LocalReaderAnnotationIndex = staticCompositionLocalOf<ReaderAnnotationIndex?> { null }

/** One index per annotation update, not per paragraph or per page-turn frame. */
@Composable
fun ReaderAnnotationRendering(annotations: List<ReaderAnnotation>, content: @Composable () -> Unit) {
    val index = remember(annotations) { ReaderAnnotationIndex(annotations) }
    CompositionLocalProvider(LocalReaderAnnotationIndex provides index, content = content)
}

@Composable
internal fun rememberReaderAnnotationIndex(annotations: List<ReaderAnnotation>): ReaderAnnotationIndex {
    val shared = LocalReaderAnnotationIndex.current
    return if (shared?.source === annotations) shared else remember(annotations) { ReaderAnnotationIndex(annotations) }
}
