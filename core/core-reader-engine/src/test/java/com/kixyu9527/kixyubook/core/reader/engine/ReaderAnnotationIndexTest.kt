package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.*
import org.junit.Assert.*
import org.junit.Test

class ReaderAnnotationIndexTest {
    @Test fun lookupsDoNotRescanThousandsOfUnrelatedAnnotations() {
        var visits = 0
        val source = object : AbstractList<ReaderAnnotation>() {
            override val size = 10_000
            override fun get(index: Int): ReaderAnnotation {
                visits++
                return annotation(index / 100, index % 100)
            }
        }
        val index = ReaderAnnotationIndex(source)
        visits = 0
        repeat(100) { assertEquals(1, index.spans(55, 36, "abcdef", 0, 6).size) }
        assertEquals("The render lookup must not iterate the book-wide source again", 0, visits)
    }

    @Test fun clippedSpansPreserveNotesUnderlineAndSourceCoordinates() {
        val highlight = annotation(2, 3).copy(startOffset = 1, endOffset = 5, exactText = "bcde", note = "note")
        val underline = highlight.copy(uuid = "underline", style = ReaderAnnotationStyle.UNDERLINE, note = "")
        val index = ReaderAnnotationIndex(listOf(highlight, underline, annotation(3, 3)))
        val spans = index.spans(2, 3, "abcdef", 3, 2)
        assertEquals(2, spans.size)
        assertTrue(spans.all { it.start == 0 && it.end == 2 })
        assertTrue(ReaderInlineStyle.NOTE in spans.first().styles)
        assertTrue(ReaderInlineStyle.HIGHLIGHT in spans.first().styles)
        assertTrue(ReaderInlineStyle.UNDERLINE in spans.last().styles)
        assertNull(spans.last().background)
    }

    @Test fun correctedTextInvalidRangesAndDifferentParagraphsNeverReceiveStaleDecorations() {
        val good = annotation(0, 0)
        val index = ReaderAnnotationIndex(listOf(good, good.copy(startOffset = -1), good.copy(endOffset = 99)))
        assertEquals(1, index.spans(0, 0, "abcdef", 0, 6).size)
        assertTrue(index.spans(0, 0, "uvwxyz", 0, 6).isEmpty())
        assertTrue(index.spans(0, 1, "abcdef", 0, 6).isEmpty())
        assertTrue(index.spans(0, 0, "abcdef", 4, 2).isEmpty())
    }

    private fun annotation(chapter: Int, paragraph: Int) = ReaderAnnotation(
        "$chapter:$paragraph", "book", "hash", "chapter-$chapter", chapter, paragraph,
        1, 3, "bc", ReaderAnnotationStyle.HIGHLIGHT, createdTime = 0, updatedTime = 0,
    )
}
