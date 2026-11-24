package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapter

/**
 * A renderer upgrade may normalize whitespace or hide publisher decorations differently. Existing
 * text rows are the coordinate system of bookmarks, notes, corrections and search results: never
 * replace that system on a cache miss. Project new visual semantics onto those original strings.
 * Unmatched text keeps its old, readable representation rather than guessing an annotation offset.
 */
internal fun DocumentChapter.withPersistedTextAnchors(text: List<String>): DocumentChapter {
    if (text.isEmpty() || text == paragraphs) return this
    fun Char.isAnchorSpace() = isWhitespace() || this == '\u00a0'
    val normalized = text.map { value -> value.filterNot { it.isAnchorSpace() } }
    val starts = IntArray(text.size + 1)
    normalized.forEachIndexed { index, value -> starts[index + 1] = starts[index] + value.length }
    val joined = normalized.joinToString("")
    val projected = List(text.size) { mutableListOf<ReaderTextSpan>() }
    val locations = mutableMapOf<Int, Int>()
    val rawOffsets = mutableMapOf<Int, List<Int>>()
    var cursor = 0
    paragraphs.forEachIndexed { index, value ->
        val plain = value.filterNot { it.isAnchorSpace() }
        if (plain.isEmpty()) return@forEachIndexed
        val found = joined.indexOf(plain, cursor)
        if (found < 0) return@forEachIndexed
        cursor = found + plain.length
        val firstParagraph = (starts.binarySearch(found).let { if (it >= 0) it else -it - 2 })
            .coerceIn(text.indices)
        locations[index] = firstParagraph
        paragraphSpans.getOrNull(index).orEmpty().forEach { span ->
            val start = found + value.take(span.start.coerceIn(0, value.length)).count { !it.isAnchorSpace() }
            val end = found + value.take(span.end.coerceIn(0, value.length)).count { !it.isAnchorSpace() }
            for (paragraph in firstParagraph until text.size) {
                if (starts[paragraph] >= end) break
                val from = maxOf(start, starts[paragraph]) - starts[paragraph]
                val to = minOf(end, starts[paragraph + 1]) - starts[paragraph]
                if (to <= from) continue
                val offsets = rawOffsets.getOrPut(paragraph) {
                    text[paragraph].indices.filter { !text[paragraph][it].isAnchorSpace() }
                }
                projected[paragraph] += span.copy(start = offsets[from], end = offsets[to - 1] + 1)
            }
        }
    }
    val presentedImages = images.sortedBy { it.contentIndex }.mapIndexed { imageIndex, image ->
        val nextText = image.contentIndex - imageIndex
        val paragraph = locations.entries.firstOrNull { it.key >= nextText }?.value ?: text.size
        image.copy(contentIndex = paragraph + imageIndex)
    }
    return copy(paragraphs = text, paragraphSpans = projected, images = presentedImages)
}
