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
    fun hiddenBooksAreNeverSelectedForContinueReading() {
        val library = listOf(book("hidden", "私密"), book("visible", "小说"))

        assertEquals("visible", selectContinueReadingBook(library, setOf("私密"))?.book?.uuid)
        assertNull(selectContinueReadingBook(listOf(library.first()), setOf("私密")))
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
