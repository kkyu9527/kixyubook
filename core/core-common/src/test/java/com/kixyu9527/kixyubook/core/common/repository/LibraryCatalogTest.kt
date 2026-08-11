package com.kixyu9527.kixyubook.core.common.repository

import com.kixyu9527.kixyubook.core.common.model.Book
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.LibraryPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogTest {
    @Test
    fun `hidden categories never enter visible library`() {
        val catalog = partitionLibraryCatalog(
            books = listOf(book("visible", "公开"), book("hidden", "私密")),
            preferences = LibraryPreferences(hiddenCategories = setOf("私密")),
        )

        assertEquals(listOf("visible"), catalog.visibleBooks.map { it.book.uuid })
        assertEquals(listOf("hidden"), catalog.hiddenBooks.map { it.book.uuid })
    }

    @Test
    fun `partition keeps every book exactly once`() {
        val books = listOf(book("a", "公开"), book("b", "私密"), book("c", "公开"))
        val catalog = partitionLibraryCatalog(
            books = books,
            preferences = LibraryPreferences(hiddenCategories = setOf("私密")),
        )
        val partitioned = catalog.visibleBooks + catalog.hiddenBooks

        assertEquals(books.size, partitioned.size)
        assertEquals(books.map { it.book.uuid }.toSet(), partitioned.map { it.book.uuid }.toSet())
        assertTrue(catalog.visibleBooks.none { it.book.category in catalog.hiddenCategories })
    }

    private fun book(uuid: String, category: String) = LibraryBook(
        book = Book(
            uuid = uuid,
            title = uuid,
            author = "",
            description = "",
            coverPath = null,
            format = BookFormat.TXT,
            originalPath = "content://$uuid",
            storagePath = "/books/$uuid.txt",
            createdTime = 0,
            contentHash = uuid,
            category = category,
        ),
        progress = null,
    )
}
