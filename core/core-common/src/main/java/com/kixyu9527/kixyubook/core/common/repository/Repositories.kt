package com.kixyu9527.kixyubook.core.common.repository

import com.kixyu9527.kixyubook.core.common.model.Book
import com.kixyu9527.kixyubook.core.common.model.Chapter
import com.kixyu9527.kixyubook.core.common.model.ChapterContent
import com.kixyu9527.kixyubook.core.common.model.ImportSummary
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeLibrary(): Flow<List<LibraryBook>>
    suspend fun importDocuments(uriStrings: List<String>): ImportSummary
    suspend fun deleteBook(bookId: Long)
    suspend fun getBook(bookId: Long): Book?
    suspend fun getChapters(bookId: Long): List<Chapter>
    suspend fun getChapter(bookId: Long, chapterIndex: Int): ChapterContent?
    fun observeProgress(bookId: Long): Flow<ReadingProgress?>
    suspend fun saveProgress(progress: ReadingProgress)
}

interface ReaderSettingsRepository {
    val settings: Flow<ReaderSettings>
    suspend fun update(transform: (ReaderSettings) -> ReaderSettings)
}

/** Extension point for future backup targets. Original novel files are intentionally excluded. */
interface BackupContributor {
    val key: String
    suspend fun export(): ByteArray
    suspend fun restore(payload: ByteArray)
}
