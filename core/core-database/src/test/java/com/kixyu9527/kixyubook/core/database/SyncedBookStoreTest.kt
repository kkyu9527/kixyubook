package com.kixyu9527.kixyubook.core.database

import androidx.room.Room
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.database.entity.*
import com.kixyu9527.kixyubook.core.reader.engine.TxtBookParser
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncedBookStoreTest {
    private suspend fun fixture(test: suspend (KixyuDatabase, SyncedBookStore, SyncedBook, File) -> Unit) {
        val context = RuntimeEnvironment.getApplication() as android.content.Context
        val database = Room.inMemoryDatabaseBuilder(context, KixyuDatabase::class.java).build()
        val source = File.createTempFile("synced-book-", ".txt", context.cacheDir).apply {
            writeText("第一章 开始\n正文用于验证云端下载。\n第二章 继续\n下一章正文。")
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        val book = SyncedBook(UUID.randomUUID().toString(), "云端书名", "作者", "简介", BookFormat.TXT, 1, hash, "分类")
        val store = SyncedBookStore(context, database, { TxtBookParser() }) { value, file, parser ->
            var index = 0
            parser.readChapters(file) { chapter ->
                val id = database.bookDao().insertChapter(ChapterEntity(bookUuid = value.uuid,
                    title = chapter.title, chapterIndex = index, chapterKey = stableChapterKey(value.uuid, index++, chapter.title)))
                database.bookDao().insertParagraphsChunked(id, chapter.paragraphs)
            }
        }
        try { test(database, store, book, source) } finally {
            database.close(); source.delete()
            File(context.filesDir, "books/${book.uuid}.txt").delete()
        }
    }

    @Test fun registrationDoesNotReadOrParseABookAndHydrationKeepsAllUserData() = runBlocking<Unit>(Dispatchers.IO) {
        fixture { db, store, book, source ->
            assertTrue(store.register(book))
            assertTrue(db.bookDao().getBook(book.uuid)!!.toModel().isCloudOnly)
            assertTrue(db.bookDao().getChapters(book.uuid).isEmpty())
            db.bookDao().insertPendingBookmark(PendingBookmarkEntity("bookmark", book.uuid, "anchor", "正文", 1))
            db.bookDao().insertSession(ReadingSessionEntity(bookUuid = book.uuid, startedTime = 1, durationMillis = 2, epochDay = 1, syncUuid = "session"))
            db.readerAnnotationDao().upsert(ReaderAnnotationEntity("note", book.uuid, book.contentHash, "first", 0, 0,
                0, 2, "正文", "HIGHLIGHT", "笔记", 1, 1, "device"))
            db.textCorrectionDao().upsert(TextCorrectionEntity("correction", book.uuid, book.contentHash, "first", 0, 0,
                0, 2, "正文", "", "", "修正", "ACTIVE", 1, 1, "device"))
            // Edits made after catalog arrival, but before download completes, are not replaced.
            db.bookDao().updateBookMetadata(book.uuid, "我的书名", "我的作者", "我的简介")
            assertTrue(store.install(book, source.path, requireExisting = true))
            val ready = db.bookDao().getBook(book.uuid)!!
            assertFalse(ready.toModel().isCloudOnly)
            assertEquals("我的书名", ready.title)
            assertEquals(source.readText(), File(ready.storagePath).readText())
            assertEquals(2, db.bookDao().getChapters(book.uuid).size)
            assertEquals(1, db.bookDao().getPendingBookmarks(book.uuid).size)
            assertNotNull(db.bookDao().getSessionBySyncUuid("session"))
            assertEquals("笔记", db.readerAnnotationDao().get("note")!!.note)
            assertNotNull(db.textCorrectionDao().get("correction"))
        }
    }

    @Test fun aFailedParsePreservesTheCloudEntryAndCanBeRetried() = runBlocking<Unit>(Dispatchers.IO) {
        fixture { db, store, book, source ->
            store.register(book)
            db.bookDao().insertPendingBookmark(PendingBookmarkEntity("bookmark", book.uuid, "anchor", "正文", 1))
            val failing = SyncedBookStore(RuntimeEnvironment.getApplication(), db, { TxtBookParser() }) { value, _, _ ->
                db.bookDao().insertChapter(ChapterEntity(bookUuid = value.uuid, title = "partial", chapterIndex = 0))
                throw IllegalStateException("parse failed")
            }
            assertTrue(runCatching { failing.install(book, source.path, true) }.isFailure)
            assertTrue(db.bookDao().getBook(book.uuid)!!.toModel().isCloudOnly)
            assertEquals(1, db.bookDao().getPendingBookmarks(book.uuid).size)
            assertTrue(db.bookDao().getChapters(book.uuid).isEmpty())
            assertTrue(store.install(book, source.path, true))
        }
    }

    @Test fun deletionWhileDownloadingNeverResurrectsTheBook() = runBlocking<Unit>(Dispatchers.IO) {
        fixture { db, store, book, source ->
            store.register(book)
            db.bookDao().deleteBook(book.uuid)
            assertFalse(store.install(book, source.path, requireExisting = true))
            assertFalse(db.bookDao().bookExists(book.uuid))
        }
    }

    @Test fun aPendingLocalDeletionPreventsCatalogRecreation() = runBlocking<Unit>(Dispatchers.IO) {
        fixture { db, store, book, _ ->
            db.syncDao().upsertOutbox(SyncOutboxEntity("deletion", SyncEntityType.BOOK.name, book.uuid, "DELETE", 1, 1, "device"))
            assertFalse(store.register(book))
            assertFalse(db.bookDao().bookExists(book.uuid))
        }
    }

    @Test fun aBadHashCannotRemoveTheEntryOrInstallAnUnverifiedFile() = runBlocking<Unit>(Dispatchers.IO) {
        fixture { db, store, book, source ->
            store.register(book)
            source.appendText("corrupt")
            assertTrue(runCatching { store.install(book, source.path, true) }.isFailure)
            assertTrue(db.bookDao().getBook(book.uuid)!!.toModel().isCloudOnly)
        }
    }

    @Test fun anInterruptedInstallationWithAFileButNoDirectoryIsRepairedInsteadOfDeclaredReady() = runBlocking<Unit>(Dispatchers.IO) {
        fixture { db, store, book, source ->
            store.register(book)
            db.bookDao().installBookSource(book.uuid, source.path, null)
            assertTrue(db.bookDao().getChapters(book.uuid).isEmpty())
            assertTrue(store.install(book, source.path, true))
            assertEquals(2, db.bookDao().getChapters(book.uuid).size)
            assertTrue(source.isFile)
            assertTrue(source.readText().contains("正文"))
        }
    }

    @Test fun aLocalPathIsPublishedOnlyAfterTheChapterDirectoryHasBeenPrepared() = runBlocking<Unit>(Dispatchers.IO) {
        fixture { db, store, book, source ->
            store.register(book)
            var prepared = false
            val preparing = SyncedBookStore(RuntimeEnvironment.getApplication(), db, { TxtBookParser() }) { value, _, _ ->
                assertEquals("", db.bookDao().getBook(book.uuid)!!.storagePath)
                db.bookDao().insertChapter(ChapterEntity(bookUuid = value.uuid, title = "正文", chapterIndex = 0))
                prepared = true
            }
            assertTrue(preparing.install(book, source.path, true))
            assertTrue(prepared)
            assertTrue(db.bookDao().getBook(book.uuid)!!.storagePath.isNotBlank())
        }
    }
}
