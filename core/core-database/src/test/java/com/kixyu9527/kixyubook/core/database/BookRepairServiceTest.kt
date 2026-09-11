package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.*
import com.kixyu9527.kixyubook.core.database.entity.*
import com.kixyu9527.kixyubook.core.reader.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookRepairServiceTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun reparsePreservesEveryUserRecordAndChapterIdentity() = exercise(false)
    @Test fun changedAnchorKeepsOriginalDataInsteadOfDestroyingAnnotation() = exercise(true)

    private fun exercise(changeSource: Boolean) = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication() as Context
        val source = folder.newFile("book.txt").apply { writeText("第一章 开始\nsearchable 黄金正文\n第二段文字") }
        val parsed = mutableListOf<DocumentChapter>()
        TxtBookParser().readChapters(source) { parsed += it }
        val first = parsed.first()
        val db = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        val recorder = object : SyncMutationRecorder {
            override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) = Unit
        }
        try {
            val dao = db.bookDao()
            dao.insertBook(BookEntity("book", "Book", "", "", null, "TXT", "", source.path, 1, "hash", ""))
            dao.insertChapter(ChapterEntity(12, "book", first.title, 0, chapterKey = "stable-key"))
            dao.insertParagraphsChunked(12, first.paragraphs)
            val chapter = dao.getChapters("book").single()
            val mark = BookmarkEntity("mark", "book", 12, 0, first.paragraphs.first(), 1)
            dao.insertBookmark(mark)
            val position = ReadingProgressEntity("book", 12, 0, 1, 3, .3f, "stable-key")
            dao.saveProgress(position)
            val notes = LocalReaderAnnotationRepository(context, db.readerAnnotationDao(), dao, recorder, db)
            val edits = LocalTextCorrectionRepository(context, db.textCorrectionDao(), dao, recorder, db)
            val note = notes.createAnnotation("book", "stable-key", 0, 0, first.paragraphs.first(), 0, 3, ReaderAnnotationStyle.UNDERLINE, "keep note")
            val edit = edits.createParagraphCorrection("book", "stable-key", 0, 1, first.paragraphs[1], "personal correction")
            val originalRows = dao.getParagraphs(12)
            var cleared = 0
            val service = BookRepairService(context, db, dao, notes, edits, Mutex()) { cleared++ }
            dao.deleteBookParagraphFts(setOf("book"))
            assertTrue(dao.searchBook("book", "searchable").isEmpty())
            service.repair("book", BookRepairMode.SEARCH_INDEX) {}
            assertEquals(1, dao.searchBook("book", "searchable").size)
            if (changeSource) source.writeText(source.readText().replace("searchable", "changed"))
            val outcome = service.repair("book", BookRepairMode.REPARSE) {}
            assertEquals(changeSource, outcome.originalPreserved)
            assertEquals(chapter, dao.getChapters("book").single())
            assertEquals(position, dao.getProgress("book"))
            assertEquals(listOf(mark), dao.getAllBookmarkEntities())
            assertEquals(listOf(note), notes.getBookAnnotations("book"))
            assertEquals(listOf(edit), edits.getBookCorrections("book"))
            assertEquals(originalRows.map { it.text }, dao.getParagraphs(12).map { it.text })
            if (changeSource) { assertEquals(originalRows, dao.getParagraphs(12)); assertEquals(0, cleared) }
            else assertEquals(1, cleared)
        } finally { db.close() }
    }
}
