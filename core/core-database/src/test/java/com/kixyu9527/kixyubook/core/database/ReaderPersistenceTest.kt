package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.model.ReaderAnnotationStyle
import com.kixyu9527.kixyubook.core.common.repository.*
import com.kixyu9527.kixyubook.core.database.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
class ReaderPersistenceTest {
    @get:Rule val folder = TemporaryFolder()
    private val context get() = RuntimeEnvironment.getApplication() as Context
    private val mutations = mutableListOf<Triple<SyncEntityType, String, SyncMutationOperation>>()
    private val recorder = object : SyncMutationRecorder {
        override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) {
            mutations += Triple(type, entityId, operation)
        }
    }

    @Test fun annotationsBookmarksSearchAndProgressSurviveClosingAndReopeningDatabase() = runBlocking(Dispatchers.IO) {
        val path = folder.root.resolve("reader.db").absolutePath
        var db = Room.databaseBuilder(context, KixyuDatabase::class.java, path).build()
        try {
            val dao = db.bookDao()
            dao.insertBook(BookEntity("book", "测试", "", "", null, "TXT", "", "", 1, "hash", ""))
            dao.insertChapter(ChapterEntity(12, "book", "正文", 4, chapterKey = "stable-key"))
            dao.insertParagraphsChunked(12, listOf("searchable 黄金正文", "第二段"))
            val paragraphs = dao.getParagraphs(12)
            val bookmark = BookmarkEntity("mark", "book", 12, 0, "黄金正文", 1)
            dao.insertBookmark(bookmark)
            val progress = ReadingProgressEntity("book", 12, 0, 11, 200, .4f, "stable-key", quoteAnchor = "黄金")
            assertTrue(dao.saveProgressIfNewer(progress))
            val annotations = LocalReaderAnnotationRepository(context, db.readerAnnotationDao(), dao, recorder, db)
            val highlight = annotations.createAnnotation("book", "stable-key", 4, 0, paragraphs[0].text, 11, 13, ReaderAnnotationStyle.HIGHLIGHT)
            val note = annotations.createAnnotation("book", "stable-key", 4, 1, "第二段", 0, 2, ReaderAnnotationStyle.UNDERLINE, "这是笔记")
            db.close()
            db = Room.databaseBuilder(context, KixyuDatabase::class.java, path).build()
            val reopened = db.bookDao()
            assertEquals(paragraphs, reopened.getParagraphs(12))
            assertEquals(progress, reopened.getProgress("book"))
            assertEquals(listOf(bookmark), reopened.getAllBookmarkEntities())
            val repository = LocalReaderAnnotationRepository(context, db.readerAnnotationDao(), reopened, recorder, db)
            assertEquals(listOf(highlight, note), repository.getBookAnnotations("book"))
            val result = reopened.searchBook("book", "searchable").single()
            assertEquals(12L, result.chapterId)
            assertEquals(0, result.paragraphIndex)
            assertEquals(result, reopened.searchBookLiteralChapters("book", "黄金", listOf(4), 10).single())
            assertFalse(reopened.saveProgressIfNewer(progress.copy(position = 1, updatedTime = 100)))
            assertEquals(progress, reopened.getProgress("book"))
            reopened.deleteBookmark("mark")
            assertTrue(reopened.getAllBookmarkEntities().isEmpty())
            assertEquals(2, repository.getBookAnnotations("book").size)
        } finally { db.close() }
    }

    @Test fun editingSameRangePreservesNoteAndDeletingDoesNotChangeSourceOrSearch() = runBlocking(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = db.bookDao()
            dao.insertBook(BookEntity("book", "测试", "", "", null, "EPUB", "", "", 1, "hash", ""))
            dao.insertChapter(ChapterEntity(12, "book", "正文", 4, chapterKey = "stable-key"))
            dao.insertParagraphsChunked(12, listOf("searchable 黄金正文"))
            val paragraphs = dao.getParagraphs(12)
            val repository = LocalReaderAnnotationRepository(context, db.readerAnnotationDao(), dao, recorder, db)
            val original = repository.createAnnotation("book", "stable-key", 4, 0, paragraphs[0].text, 11, 13, ReaderAnnotationStyle.HIGHLIGHT, "不能丢失")
            val restyled = repository.createAnnotation("book", "stable-key", 4, 0, paragraphs[0].text, 11, 13, ReaderAnnotationStyle.UNDERLINE)
            assertEquals(original.uuid, restyled.uuid)
            assertEquals(original.createdTime, restyled.createdTime)
            assertEquals("不能丢失", restyled.note)
            assertEquals(1, repository.getBookAnnotations("book").size)
            assertEquals("修改笔记", repository.updateNote(original.uuid, " 修改笔记 ")?.note)
            repository.deleteAnnotation(original.uuid)
            assertTrue(repository.getBookAnnotations("book").isEmpty())
            assertEquals(SyncMutationOperation.DELETE, mutations.last().third)
            assertEquals(paragraphs, dao.getParagraphs(12))
            assertEquals(1, dao.searchBook("book", "searchable").size)
            assertNotNull(dao.getBook("book"))
        } finally { db.close() }
    }
}
