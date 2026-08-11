package com.kixyu9527.kixyubook.feature.home

import com.kixyu9527.kixyubook.core.common.model.Book
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinueReadingSelectionTest {
    @Test
    fun continueReadingSelectsFirstProgressedVisibleBook() {
        val library = listOf(book("first", "小说"), book("second", "小说"))

        assertEquals("first", selectContinueReadingBook(library)?.book?.uuid)
        assertNull(selectContinueReadingBook(library.map { it.copy(progress = null) }))
    }

    @Test
    fun recentReadingExcludesCurrentBook() {
        val library = listOf(
            book("current", "小说"),
            book("recent-1", "小说"),
            book("recent-2", "小说"),
        )

        assertEquals(
            listOf("recent-1", "recent-2"),
            selectRecentReadingBooks(
                library = library,
                currentBookUuid = "current",
            ).map { it.book.uuid },
        )
    }

    private fun book(uuid: String, category: String) = LibraryBook(
        book = Book(
            uuid = uuid,
            title = uuid,
            author = "",
            description = "",
            coverPath = null,
            format = BookFormat.EPUB,
            originalPath = "",
            storagePath = "",
            createdTime = 0,
            contentHash = uuid,
            category = category,
        ),
        progress = ReadingProgress(uuid, 1, 0, updatedTime = 1),
    )
}
