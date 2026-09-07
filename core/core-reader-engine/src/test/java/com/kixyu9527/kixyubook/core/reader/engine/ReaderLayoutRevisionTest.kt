package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.*
import org.junit.Assert.*
import org.junit.Test

class ReaderLayoutRevisionTest {
    private val paragraph = Paragraph(1, 10, 0, "正文")
    private fun revision(value: Paragraph) = ReaderChapter(10, "book", "章节", 0, listOf(value)).layoutRevision()

    @Test fun databaseIdentityChangesDoNotInvalidateLayoutButImagesAndStylesDo() {
        val original = revision(paragraph)
        assertEquals(original, revision(paragraph.copy(id = 99)))
        assertNotEquals(original, revision(paragraph.copy(kind = ParagraphKind.IMAGE, resourcePath = "page.png")))
        assertNotEquals(original, revision(paragraph.copy(isFullPageImage = true)))
        assertNotEquals(original, revision(paragraph.copy(intrinsicHeight = 800)))
        val span = ReaderTextSpan(0, 2, linkedSetOf(ReaderInlineStyle.BOLD, ReaderInlineStyle.ITALIC))
        assertNotEquals(original, revision(paragraph.copy(spans = listOf(span))))
        assertEquals(revision(paragraph.copy(spans = listOf(span))),
            revision(paragraph.copy(spans = listOf(span.copy(styles = span.styles.reversed().toSet())))))
    }
}
