package com.kixyu9527.kixyubook.feature.library

import com.kixyu9527.kixyubook.core.common.model.Book
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import org.junit.Assert.assertEquals
import org.junit.Test

class BookExportFileNameTest {
    @Test
    fun `uses edited title and format extension`() {
        assertEquals("正确的书名-纠错版.txt", exportFileName(book("正确的书名", BookFormat.EPUB)))
    }

    @Test
    fun `does not duplicate an existing extension`() {
        assertEquals("小说-纠错版.txt", exportFileName(book("小说.EPUB", BookFormat.EPUB)))
    }

    @Test
    fun `replaces characters forbidden by document providers`() {
        assertEquals("卷一_开始_-纠错版.txt", exportFileName(book("卷一/开始?", BookFormat.TXT)))
    }

    private fun book(title: String, format: BookFormat) = LibraryBook(
        book = Book(
            uuid = "book",
            title = title,
            author = "作者",
            description = "",
            coverPath = null,
            format = format,
            originalPath = "source",
            storagePath = "stored",
            createdTime = 0,
            contentHash = "hash",
        ),
        progress = null,
    )
}
