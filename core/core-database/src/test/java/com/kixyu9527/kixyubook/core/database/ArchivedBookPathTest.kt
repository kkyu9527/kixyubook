package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ArchivedBookPathTest {
    @Test
    fun rejectsTraversalUnknownFormatAndMissingFile() {
        val assets = File(System.getProperty("java.io.tmpdir"), "assets-${System.nanoTime()}").apply { mkdirs() }
        try {
            File(assets, "books").mkdirs()
            File(assets, "manifest.properties").writeText("x")

            assertNull(resolveArchivedBook(assets, "../../manifest.properties", ""))
            assertNull(resolveArchivedBook(assets, "book", "EXE"))
            assertNull(resolveArchivedBook(assets, "book", "EPUB"))

            File(assets, "books/book.epub").writeText("y")
            val resolved = resolveArchivedBook(assets, "book", "epub")
            assertEquals(BookFormat.EPUB, resolved?.format)
            assertEquals("book.epub", resolved?.file?.name)
        } finally {
            assets.deleteRecursively()
        }
    }
}
