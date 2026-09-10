package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.model.ReaderAnnotationStyle
import com.kixyu9527.kixyubook.core.common.repository.*
import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AtomicReaderMutationTest {
    @Test fun bookmarkProgressAndMetadataCannotCommitWithoutOutbox() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val db = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            val dao = db.bookDao()
            dao.insertBook(BookEntity("book", "original", "", "", null, "TXT", "", "", 1, "hash", "category"))
            dao.insertChapters(listOf(com.kixyu9527.kixyubook.core.database.entity.ChapterEntity(1, "book", "chapter", 0)))
            var fail = false
            var calls = 0
            var failAtCall = Int.MAX_VALUE
            val recorder = object : SyncMutationRecorder {
                override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) {
                    db.syncDao().upsertOutbox(SyncOutboxEntity(UUID.randomUUID().toString(), type.name, entityId, operation.name, 1, 1, "test"))
                    if (++calls == failAtCall || fail) throw IOException("injected outbox failure")
                }
            }
            val store = BookMutationStore(context, db, dao, recorder)
            val bookmark = com.kixyu9527.kixyubook.core.common.model.Bookmark("mark", "book", 1, "chapter", 0, 3, "preview", 1)
            store.addBookmark(bookmark)
            val outbox = db.syncDao().allPending()
            fail = true
            assertTrue(runCatching { store.deleteBookmark("mark") }.isFailure)
            assertNotNull(dao.getBookmarkEntity("mark"))
            assertTrue(runCatching { store.addBookmark(bookmark.copy(uuid = "new")) }.isFailure)
            assertNull(dao.getBookmarkEntity("new"))
            assertTrue(runCatching { store.saveProgress(com.kixyu9527.kixyubook.core.common.model.ReadingProgress("book", 1, 3, updatedTime = 1)) }.isFailure)
            assertNull(dao.getProgress("book"))
            assertTrue(runCatching { store.updateBookMetadata("book", "changed", "author", "description") }.isFailure)
            assertEquals("original", dao.getBook("book")?.title)
            assertTrue(runCatching { store.setCategory("book", "changed") }.isFailure)
            assertEquals("category", dao.getBook("book")?.category)
            assertEquals(outbox, db.syncDao().allPending())
            fail = false
            failAtCall = calls + 2 // metadata succeeds, category fails: the whole editor must roll back.
            assertTrue(runCatching { store.updateBookDetails("book", "changed", "author", "description", "changed") }.isFailure)
            assertEquals("original", dao.getBook("book")?.title)
            assertEquals("category", dao.getBook("book")?.category)
            assertEquals(outbox, db.syncDao().allPending())
        } finally { db.close() }
    }
    @Test fun annotationAndOutboxRollbackTogetherForCreateUpdateAndDelete() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val db = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        try {
            db.bookDao().insertBook(BookEntity("book", "title", "", "", null, "TXT", "", "", 1, "hash", ""))
            var fail = false
            val recorder = object : SyncMutationRecorder {
                override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) {
                    db.syncDao().upsertOutbox(SyncOutboxEntity(UUID.randomUUID().toString(), type.name, entityId, operation.name, 1, 1, "test"))
                    if (fail) throw IOException("injected outbox failure")
                }
            }
            val repository = LocalReaderAnnotationRepository(context, db.readerAnnotationDao(), db.bookDao(), recorder, db)
            fail = true
            assertTrue(runCatching { repository.createAnnotation("book", "chapter", 0, 0, "text", 0, 4, ReaderAnnotationStyle.HIGHLIGHT, "draft") }.isFailure)
            assertTrue(repository.getBookAnnotations("book").isEmpty())
            assertTrue(db.syncDao().allPending().isEmpty())
            fail = false
            val saved = repository.createAnnotation("book", "chapter", 0, 0, "text", 0, 4, ReaderAnnotationStyle.HIGHLIGHT, "original")
            val originalOutbox = db.syncDao().allPending()
            fail = true
            assertTrue(runCatching { repository.updateNote(saved.uuid, "changed") }.isFailure)
            assertEquals(saved, repository.getBookAnnotations("book").single())
            assertEquals(originalOutbox, db.syncDao().allPending())
            assertTrue(runCatching { repository.deleteAnnotation(saved.uuid) }.isFailure)
            assertEquals(saved, repository.getBookAnnotations("book").single())
            assertEquals(originalOutbox, db.syncDao().allPending())
            fail = false
            repository.deleteAnnotation(saved.uuid)
            assertTrue(repository.getBookAnnotations("book").isEmpty())
            assertEquals("DELETE", db.syncDao().allPending().single().operation)
        } finally { db.close() }
    }
}
