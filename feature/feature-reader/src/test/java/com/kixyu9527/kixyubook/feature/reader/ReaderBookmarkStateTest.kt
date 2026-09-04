package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderBookmarkStateTest {
    private val bookmark = Bookmark(
        uuid = "bookmark",
        bookUuid = "book",
        chapterId = 7L,
        chapterTitle = "Chapter",
        chapterIndex = 2,
        position = 14,
        preview = "Text",
        createdTime = 1L,
    )

    @Test
    fun bookmarkInsideVisiblePageIsRecognized() {
        val result = currentVisiblePageBookmark(
            listOf(bookmark),
            chapterId = 7L,
            position = ReaderPositionState(paragraphIndex = 11, visibleEndParagraphIndex = 16),
        )

        assertEquals(bookmark, result)
    }

    @Test
    fun bookmarkOutsideVisiblePageIsNotRecognized() {
        val result = currentVisiblePageBookmark(
            listOf(bookmark),
            chapterId = 7L,
            position = ReaderPositionState(paragraphIndex = 15, visibleEndParagraphIndex = 18),
        )

        assertNull(result)
    }
}
