package com.kixyu9527.kixyubook.core.common.repository

import com.kixyu9527.kixyubook.core.common.model.*
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeLibrary(): Flow<List<LibraryBook>>
    fun observeImportEvents(): Flow<String>
    suspend fun importDocuments(uriStrings: List<String>): ImportSummary
    suspend fun deleteBook(bookUuid: String)
    suspend fun deleteBooks(bookUuids: Set<String>)
    suspend fun getBook(bookUuid: String): Book?
    suspend fun getChapters(bookUuid: String): List<Chapter>
    fun observeChapters(bookUuid: String): Flow<List<Chapter>>
    suspend fun getChapter(bookUuid: String, chapterIndex: Int): ChapterContent?
    suspend fun prepareReader(bookUuid: String)
    fun observeProgress(bookUuid: String): Flow<ReadingProgress?>
    suspend fun saveProgress(progress: ReadingProgress)
    suspend fun updateBookMetadata(bookUuid: String, title: String, author: String, description: String)
    suspend fun reparseTxt(bookUuid: String): Result<Unit>
    suspend fun setCategory(bookUuid: String, category: String)
    fun observeBookmarks(bookUuid: String): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(bookmarkUuid: String)
    suspend fun searchBook(bookUuid: String, query: String): List<BookSearchResult>
}

interface ReaderSettingsRepository {
    val settings: Flow<ReaderSettings>
    suspend fun update(transform: (ReaderSettings) -> ReaderSettings)
    val readingGoalMinutes: Flow<Int>
    suspend fun setReadingGoalMinutes(minutes: Int)
}

interface ReadingStatsRepository {
    fun observeStats(): Flow<ReadingStats>
    suspend fun recordSession(bookUuid: String, durationMillis: Long, charactersRead: Long)
}

interface FontRepository {
    fun observeFonts(): Flow<List<UserFont>>
    suspend fun importFont(uriString: String): Result<UserFont>
    suspend fun deleteFont(fontUuid: String)
    suspend fun getFont(fontUuid: String): UserFont?
}

data class BackupResult(
    val bookCount: Int,
    val totalBytes: Long,
    val requiresRestart: Boolean = false,
)

/** Streaming full backup suitable for SAF documents and cross-device restoration. */
interface BackupRepository {
    suspend fun exportTo(uriString: String): Result<BackupResult>
    suspend fun restoreFrom(uriString: String): Result<BackupResult>
}

/** All contributors participate in full-device restoration, including original books and user assets. */
interface BackupContributor {
    val key: String
    suspend fun export(): ByteArray
    suspend fun restore(payload: ByteArray)
}
