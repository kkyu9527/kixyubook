package com.kixyu9527.kixyubook.core.common.repository

import com.kixyu9527.kixyubook.core.common.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BookRepository {
    fun observeImportEvents(): Flow<String>
    /** Immediately promotes a book in local activity ordering without changing reading progress. */
    fun markBookOpened(bookUuid: String)
    suspend fun importDocuments(uriStrings: List<String>): ImportSummary
    /** Copies the immutable source book to a user-selected document destination. */
    suspend fun exportBook(bookUuid: String, uriString: String): Result<Unit>
    /** Restores an immutable source blob while preserving its permanent book UUID. */
    suspend fun restoreSyncedBook(book: SyncedBook, sourceFilePath: String): Boolean
    suspend fun deleteBook(bookUuid: String)
    suspend fun deleteBooks(bookUuids: Set<String>)
    suspend fun getBook(bookUuid: String): Book?
    suspend fun getChapters(bookUuid: String): List<Chapter>
    fun observeChapters(bookUuid: String): Flow<List<Chapter>>
    suspend fun getChapter(
        bookUuid: String,
        chapterIndex: Int,
        priority: ChapterLoadPriority = ChapterLoadPriority.USER,
    ): ChapterContent?
    suspend fun prepareReader(bookUuid: String)
    /** Releases decoded reader data; persistent EPUB and pagination caches remain available. */
    fun releaseReaderMemory(bookUuid: String) = Unit
    /** Temporarily yields background EPUB indexing to a visible reader gesture/animation. */
    fun setReaderInteractionActive(active: Boolean) = Unit
    /** Pauses full-book EPUB indexing for the entire time a reader destination is visible. */
    fun setReaderSessionActive(active: Boolean) = Unit
    /** Temporarily yields background EPUB indexing to an app-level navigation animation. */
    fun setAppAnimationActive(active: Boolean) = Unit
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

/** Raw complete-library access for visibility partitioning and internal maintenance only. */
interface CompleteLibraryRepository {
    /**
     * Observes every stored book, including books in hidden categories.
     *
     * Feature UI must use [LibraryCatalogRepository] so hidden books cannot accidentally leak
     * into normal shelves, recent reading, or search results.
     */
    fun observeCompleteLibrary(): Flow<List<LibraryBook>>
}

interface ReaderSettingsRepository {
    val settings: Flow<ReaderSettings>
    suspend fun update(transform: (ReaderSettings) -> ReaderSettings)
    val readingGoalMinutes: Flow<Int>
    suspend fun setReadingGoalMinutes(minutes: Int)
}

interface LibraryPreferencesRepository {
    val preferences: Flow<LibraryPreferences>
    suspend fun setSortMode(mode: LibrarySortMode)
    suspend fun setLayoutMode(mode: LibraryLayoutMode)
    suspend fun setCustomOrder(bookUuids: List<String>)
    suspend fun setCategoryHidden(category: String, hidden: Boolean)
    suspend fun replace(preferences: LibraryPreferences)
}

data class LibraryCatalog(
    val allBooks: List<LibraryBook> = emptyList(),
    val visibleBooks: List<LibraryBook> = emptyList(),
    val hiddenBooks: List<LibraryBook> = emptyList(),
    val allCategories: List<String> = emptyList(),
    val hiddenCategories: Set<String> = emptySet(),
)

/**
 * The single UI-facing visibility boundary for the library.
 *
 * Normal destinations consume [observeVisibleLibrary], while the dedicated hidden shelf consumes
 * [observeHiddenLibrary]. Complete data remains available only through
 * [CompleteLibraryRepository] for storage, sync, backup, and maintenance work.
 */
interface LibraryCatalogRepository {
    val catalog: Flow<LibraryCatalog>
    fun observeVisibleLibrary(): Flow<List<LibraryBook>>
    fun observeHiddenLibrary(): Flow<List<LibraryBook>>
}

fun partitionLibraryCatalog(
    books: List<LibraryBook>,
    preferences: LibraryPreferences,
): LibraryCatalog {
    val (hiddenBooks, visibleBooks) = books.partition {
        it.book.category in preferences.hiddenCategories
    }
    return LibraryCatalog(
        allBooks = books,
        visibleBooks = visibleBooks,
        hiddenBooks = hiddenBooks,
        allCategories = books.map { it.book.category }.distinct().sorted(),
        hiddenCategories = preferences.hiddenCategories,
    )
}

interface ReadingStatsRepository {
    fun observeStats(): Flow<ReadingStats>
    suspend fun recordSession(bookUuid: String, durationMillis: Long)
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

interface AppUpdateRepository {
    val state: StateFlow<AppUpdateState>
    val releaseNotesState: StateFlow<ReleaseNotesState>
    suspend fun checkForUpdates(manual: Boolean)
    suspend fun loadReleaseNotes(versionName: String)
    fun clearResult()
}

enum class SyncEntityType { BOOK, PROGRESS, BOOKMARKS, SETTINGS, SESSION, FONT }
enum class SyncMutationOperation { UPSERT, DELETE }

/** Records local mutations for object-level cloud sync. Implementations must be cheap and offline-safe. */
interface SyncMutationRecorder {
    suspend fun record(
        type: SyncEntityType,
        entityId: String,
        operation: SyncMutationOperation = SyncMutationOperation.UPSERT,
    )
}

/**
 * Coordinates cloud work with the visible app and reader without exposing a cloud provider to
 * feature modules. Implementations must keep these calls non-blocking.
 */
interface CloudSyncCoordinator {
    val priorityBookSync: StateFlow<PriorityBookSyncState>

    /** Starts a lightweight progress pull followed by durable background reconciliation. */
    fun onAppForeground()

    /** Flushes the last visible book and leaves durable work running after the app is backgrounded. */
    fun onAppBackground()

    /** Gives one book's progress priority over library and binary-file synchronization. */
    fun prioritizeBook(bookUuid: String)

    /** Releases the foreground priority while retaining the book for the final progress flush. */
    fun releaseBook(bookUuid: String)
}

enum class PriorityBookSyncPhase { IDLE, PULLING, READY, ERROR }

data class PriorityBookSyncState(
    val bookUuid: String? = null,
    val phase: PriorityBookSyncPhase = PriorityBookSyncPhase.IDLE,
    val errorMessage: String? = null,
)
