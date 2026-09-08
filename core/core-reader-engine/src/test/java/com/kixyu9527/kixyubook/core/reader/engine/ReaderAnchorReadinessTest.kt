package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import org.junit.Assert.*
import org.junit.Test

class ReaderAnchorReadinessTest {
    private fun page(start: Int, kind: ParagraphKind = ParagraphKind.TEXT) = ReaderPage(
        0, 0, "正文", false,
        listOf(DocumentBlock(7, "字".repeat(300), "字".repeat(100), start > 0, kind = kind, textStart = start)),
    )

    @Test fun partialParagraphMustReachSavedCharacterBeforeItCanBeDisplayed() {
        val partial = ReaderPaginationSnapshot(listOf(page(0)))
        assertFalse(partial.readyForReadingAnchor(7, 150))
        assertFalse(partial.readyForReadingAnchor(7, 100)) // exclusive fragment end
        assertTrue(partial.readyForReadingAnchor(7, 99))
        val ready = partial.copy(pages = partial.pages + page(100))
        assertTrue(ready.readyForReadingAnchor(7, 150))
        assertEquals(1, ReaderPositionManager().pageFor(ready.pages, 7, charOffset = 150))
    }

    @Test fun imageWithSameParagraphIndexCannotSatisfyATextOffset() {
        val image = ReaderPaginationSnapshot(listOf(page(0, ParagraphKind.IMAGE)))
        assertFalse(image.readyForReadingAnchor(7, 10))
        assertTrue(image.readyForReadingAnchor(7, 0)) // retain ordinary image-opening behaviour
        assertTrue(image.readyForReadingAnchor(null, 0)) // neighbour prefetch has no restore target
    }

    @Test fun whitespaceGapsAndStaleAnchorsCannotBlockOpeningForever() {
        assertTrue(ReaderPaginationSnapshot(listOf(page(101))).readyForReadingAnchor(7, 100))
        val complete = ReaderPaginationSnapshot(listOf(page(0)), isComplete = true)
        assertTrue(complete.readyForReadingAnchor(7, 500))
        assertTrue(complete.readyForReadingAnchor(99, 0))
    }
}
