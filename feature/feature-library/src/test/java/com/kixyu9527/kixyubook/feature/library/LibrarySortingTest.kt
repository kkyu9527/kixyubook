package com.kixyu9527.kixyubook.feature.library

import com.kixyu9527.kixyubook.core.common.model.Book
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.LibraryPreferences
import com.kixyu9527.kixyubook.core.common.model.LibrarySortMode
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortingTest {
    @Test
    fun recentSortPreservesRepositoryActivityOrder() {
        val books = listOf(book("newer", 1), book("older", 2))

        val result = sortLibraryBooks(books, LibraryPreferences(LibrarySortMode.RECENT))

        assertEquals(listOf("newer", "older"), result.map { it.book.uuid })
    }

    @Test
    fun customSortKeepsNewImportsAbovePersistedOrder() {
        val books = listOf(book("new"), book("first"), book("second"))
        val preferences = LibraryPreferences(
            sortMode = LibrarySortMode.CUSTOM,
            customOrder = listOf("second", "first"),
        )

        val result = sortLibraryBooks(books, preferences)

        assertEquals(listOf("new", "second", "first"), result.map { it.book.uuid })
    }

    @Test
    fun progressSortPlacesUnreadBooksLast() {
        val books = listOf(book("unread"), book("half", progress = .5f), book("done", progress = 1f))

        val result = sortLibraryBooks(books, LibraryPreferences(LibrarySortMode.PROGRESS))

        assertEquals(listOf("done", "half", "unread"), result.map { it.book.uuid })
    }

    private fun book(uuid: String, createdTime: Long = 0, progress: Float? = null): LibraryBook = LibraryBook(
        book = Book(
            uuid = uuid,
            title = uuid,
            author = uuid,
            description = "",
            coverPath = null,
            format = BookFormat.TXT,
            originalPath = "",
            storagePath = "",
            createdTime = createdTime,
            contentHash = uuid,
        ),
        progress = progress?.let {
            ReadingProgress(
                bookUuid = uuid,
                chapterId = 1,
                position = 0,
                updatedTime = 0,
                fraction = it,
            )
        },
    )
}
