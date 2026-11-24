package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.model.ReaderInlineStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan
import com.kixyu9527.kixyubook.core.reader.engine.DocumentChapter
import com.kixyu9527.kixyubook.core.reader.engine.DocumentImage
import org.junit.Assert.*
import org.junit.Test

class EpubAnchorProjectionTest {
    @Test fun whitespaceAndBlockChangesDoNotMoveExistingBookmarkOrAnnotationCoordinates() {
        val oldText = listOf("　Alpha  beta", "Gamma", "Legacy footnote text")
        val parsed = DocumentChapter("Canonical title", listOf("Alpha betaGamma"),
            paragraphSpans = listOf(listOf(ReaderTextSpan(6, 15, setOf(ReaderInlineStyle.BOLD)))))
        val projected = parsed.withPersistedTextAnchors(oldText)
        assertEquals(oldText, projected.paragraphs)
        assertEquals("Canonical title", projected.title)
        assertEquals(ReaderTextSpan(8, 12, setOf(ReaderInlineStyle.BOLD)), projected.paragraphSpans[0].single())
        assertEquals(ReaderTextSpan(0, 5, setOf(ReaderInlineStyle.BOLD)), projected.paragraphSpans[1].single())
        assertTrue(projected.paragraphSpans[2].isEmpty())
    }

    @Test fun imagePositionsProjectOntoTheOriginalTextWithoutReplacingIt() {
        val parsed = DocumentChapter("A", listOf("one", "two"), images = listOf(
            DocumentImage(0, "plate.png", "image/png"),
            DocumentImage(2, "art.png", "image/png"),
        ))
        val projected = parsed.withPersistedTextAnchors(listOf("one", "old decoration", "two"))
        assertEquals(listOf("one", "old decoration", "two"), projected.paragraphs)
        assertEquals(listOf(0, 3), projected.images.map { it.contentIndex })
    }

    @Test fun unmatchedAndRepeatedTextNeverCreatesOutOfRangeSpansOrDiscardsOldText() {
        val parsed = DocumentChapter("A", listOf("missing marker†", "same", "same"),
            paragraphSpans = List(3) { listOf(ReaderTextSpan(0, 4, setOf(ReaderInlineStyle.BOLD))) })
        val projected = parsed.withPersistedTextAnchors(listOf("", "same", "", "same", "tail"))
        assertEquals(listOf("", "same", "", "same", "tail"), projected.paragraphs)
        assertEquals(2, projected.paragraphSpans.sumOf { it.size })
        projected.paragraphSpans.forEachIndexed { i, spans -> spans.forEach {
            assertTrue(it.start >= 0 && it.start < it.end && it.end <= projected.paragraphs[i].length)
        } }
    }
}
