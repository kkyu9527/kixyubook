package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.database.entity.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookSearchScannerTest {
    @Test fun allMatchesSurviveGiantChapterAndLaterChapterWithoutFullTextCopies() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val db = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = db.bookDao()
            dao.insertBook(BookEntity("book", "title", "", "", null, "TXT", "", "", 1, "hash", ""))
            dao.insertChapter(ChapterEntity(1, "book", "large", 0, indexed = true))
            dao.insertChapter(ChapterEntity(2, "book", "last", 1, indexed = true))
            dao.insertParagraphsChunked(1, List(1_200) { "x".repeat(800) + "needle $it" })
            dao.insertParagraphsChunked(2, listOf("last needle"))
            val batches = mutableListOf<List<BookSearchResult>>()
            val progress = mutableListOf<BookSearchProgress>()
            val results = BookSearchScanner(dao).search(dao.getChapters("book"), "needle", emptyList(),
                { error("already indexed") }, { progress += it }, { batches += it })
            assertEquals(1_201, results.size)
            assertEquals(1_201, batches.sumOf { it.size })
            assertTrue(batches.all { it.size <= 128 })
            assertEquals(2L, results.last().chapterId)
            assertTrue(results.all { it.text.length < 200 && it.text.contains("needle") })
            assertEquals(1f, progress.last().fraction)
            var delivered = 0
            try {
                BookSearchScanner(dao).search(dao.getChapters("book"), "needle", emptyList(),
                    { error("already indexed") }, {}, { delivered += it.size; throw CancellationException("cancel search") })
                fail("cancellation must propagate")
            } catch (_: CancellationException) { assertEquals(128, delivered) }
        } finally { db.close() }
    }

    @Test fun aParagraphReportsEveryOccurrenceAndItsCharacterRange() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val db = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = db.bookDao()
            dao.insertBook(BookEntity("book", "title", "", "", null, "TXT", "", "", 1, "hash", ""))
            dao.insertChapter(ChapterEntity(1, "book", "chapter", 0, indexed = true))
            dao.insertParagraphsChunked(1, listOf("NEEDLE 和 needle，还有一个 needle"))
            val results = BookSearchScanner(dao).search(
                dao.getChapters("book"), "needle", emptyList(),
                { error("already indexed") }, {}, {},
            )
            assertEquals(1, results.size)
            val result = results.single()
            assertEquals(3, result.matches.size)
            result.matches.forEach { match ->
                assertEquals("needle", result.text.substring(match.start, match.start + match.length).lowercase())
            }
        } finally { db.close() }
    }

    @Test fun excerptKeepsSupplementaryCharactersIntact() {
        val text = "a".repeat(47) + "😀" + "b".repeat(47) + "needle" + "c".repeat(111) + "😀end"
        val excerpt = searchExcerpt(text, text.indexOf("needle"), 6)
        assertTrue(excerpt.contains("needle"))
        assertEquals(excerpt, excerpt.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }
}
