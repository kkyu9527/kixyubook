package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.withTransaction
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.*
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** All reader/library writes and their durable outbound mutations share a transaction. */
internal class BookMutationStore(
    private val context: Context,
    private val database: KixyuDatabase,
    private val dao: BookDao,
    private val syncMutations: SyncMutationRecorder,
) {
    suspend fun saveProgress(progress: ReadingProgress) = database.withTransaction {
        val chapter = dao.getChapters(progress.bookUuid).firstOrNull { it.id == progress.chapterId }
        val saved = dao.saveProgressIfNewer(
            ReadingProgressEntity(
                bookUuid = progress.bookUuid,
                chapterId = progress.chapterId,
                position = progress.position,
                offset = progress.offset,
                updatedTime = progress.updatedTime,
                fraction = progress.fraction,
                chapterKey = progress.chapterKey.ifBlank { chapter?.chapterKey.orEmpty() },
                paragraphIndex = progress.paragraphIndex,
                charOffset = progress.charOffset,
                quoteAnchor = progress.quoteAnchor,
            ),
        )
        if (saved) syncMutations.record(SyncEntityType.PROGRESS, progress.bookUuid)
    }

    suspend fun updateBookMetadata(bookUuid: String, title: String, author: String, description: String): Unit = database.withTransaction {
        val book = dao.getBook(bookUuid) ?: error(context.getString(R.string.db_book_missing))
        dao.insertMetadataEdit(MetadataEditEntity(UUID.randomUUID().toString(), bookUuid, book.title, book.author, book.description, title.trim(), author.trim(), description.trim(), System.currentTimeMillis()))
        dao.updateBookMetadata(bookUuid, title.trim().ifBlank { "未命名书籍" }, author.trim().ifBlank { "未知作者" }, description.trim())
        syncMutations.record(SyncEntityType.BOOK, bookUuid)
    }

    suspend fun setCategory(bookUuid: String, category: String) = database.withTransaction {
        dao.setCategory(bookUuid, category.trim().ifBlank { "未分类" })
        syncMutations.record(SyncEntityType.BOOK, bookUuid)
    }

    suspend fun updateBookDetails(bookUuid: String, title: String, author: String, description: String, category: String): Unit =
        database.withTransaction {
            updateBookMetadata(bookUuid, title, author, description)
            setCategory(bookUuid, category)
        }

    suspend fun setCategories(bookUuids: Set<String>, category: String) = withContext(Dispatchers.IO) {
        if (bookUuids.isEmpty()) return@withContext
        val normalized = category.trim().ifBlank { "未分类" }
        database.withTransaction {
            dao.setCategories(bookUuids, normalized)
            bookUuids.forEach { uuid -> syncMutations.record(SyncEntityType.BOOK, uuid) }
        }
    }

    suspend fun addBookmark(bookmark: Bookmark): Unit = database.withTransaction {
        // Persist the stable chapter key alongside the mutable row id so the bookmark can be
        // re-anchored after a reparse. Callers may supply it; otherwise derive it from the chapter.
        val chapterKey = bookmark.chapterKey.ifBlank { dao.getChapterKey(bookmark.chapterId).orEmpty() }
        dao.insertBookmark(
            BookmarkEntity(
                uuid = bookmark.uuid,
                bookUuid = bookmark.bookUuid,
                chapterId = bookmark.chapterId,
                position = bookmark.position,
                preview = bookmark.preview,
                createdTime = bookmark.createdTime,
                chapterKey = chapterKey,
            ),
        )
        syncMutations.record(SyncEntityType.BOOKMARKS, bookmark.bookUuid)
    }

    suspend fun deleteBookmark(bookmarkUuid: String) = database.withTransaction {
        // A bookmark that a reparse has not relocated yet lives in the pending table; deleting the
        // uuid must remove it there too, or the next remote snapshot would resurrect it.
        val owner = dao.getBookmarkEntity(bookmarkUuid)?.bookUuid
            ?: dao.getPendingBookmark(bookmarkUuid)?.bookUuid
        dao.deleteBookmark(bookmarkUuid)
        dao.deletePendingBookmark(bookmarkUuid)
        owner?.let { syncMutations.record(SyncEntityType.BOOKMARKS, it) }
        Unit
    }
}
