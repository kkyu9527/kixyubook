package com.kixyu9527.kixyubook.feature.library
import com.kixyu9527.kixyubook.core.common.operation.UserOperationController
import com.kixyu9527.kixyubook.core.common.operation.UserOperationKind
import com.kixyu9527.kixyubook.core.common.operation.LatestOperationWriter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.LibraryPreferences
import com.kixyu9527.kixyubook.core.common.model.LibraryLayoutMode
import com.kixyu9527.kixyubook.core.common.model.LibrarySortMode
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryCatalog
import com.kixyu9527.kixyubook.core.common.repository.LibraryCatalogRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

// Internal filter token, never translated or used as a localized category name.
// A control-prefixed token can never equal a user-created category, so the "all" filter is
// unambiguous even when a category is named like its displayed label.
internal const val ALL_LIBRARY_CATEGORIES = "\u0000all"

data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val query: String = "",
    val category: String = ALL_LIBRARY_CATEGORIES,
    val categories: List<String> = listOf(ALL_LIBRARY_CATEGORIES),
    val allCategories: List<String> = emptyList(),
    val hiddenCategories: Set<String> = emptySet(),
    val sortMode: LibrarySortMode = LibrarySortMode.RECENT,
    val layoutMode: LibraryLayoutMode = LibraryLayoutMode.LIST,
    val hiddenOnly: Boolean = false,
)

data class BookExportEvent(
    val uriString: String,
    val exportedCount: Int = 1,
    val failedCount: Int = 0,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
    catalogRepository: LibraryCatalogRepository,
    private val preferencesRepository: LibraryPreferencesRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow(ALL_LIBRARY_CATEGORIES)
    private val catalog = catalogRepository.catalog
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryCatalog())
    private val preferences = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryPreferences())
    private val customOrderOverride = MutableStateFlow<List<String>?>(null)
    private val effectivePreferences = combine(preferences, customOrderOverride) { stored, override ->
        if (override == null) stored else stored.copy(customOrder = override)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryPreferences())
    private var customOrderPersistence: Job? = null
    private val messages = Channel<String>(Channel.BUFFERED)
    val operations = UserOperationController(viewModelScope)
    private val settingWrites = LatestOperationWriter(viewModelScope, operations)
    val messageEvents = messages.receiveAsFlow()
    private val _repairProgress = MutableStateFlow<com.kixyu9527.kixyubook.core.common.model.BookRepairProgress?>(null)
    private val _repairOutcome = MutableStateFlow<com.kixyu9527.kixyubook.core.common.model.BookRepairOutcome?>(null)
    val repairProgress = _repairProgress.asStateFlow()
    val repairOutcome = _repairOutcome.asStateFlow()
    fun clearRepairOutcome() { _repairOutcome.value = null }

    fun repairBook(uuid: String, mode: com.kixyu9527.kixyubook.core.common.model.BookRepairMode) = operations.submit {
        _repairOutcome.value = null
        _repairProgress.value = com.kixyu9527.kixyubook.core.common.model.BookRepairProgress(0, 0)
        try { _repairOutcome.value = repository.repairBook(uuid, mode) { _repairProgress.value = it }.getOrThrow() }
        finally { _repairProgress.value = null }
    }
    private val exports = Channel<BookExportEvent>(Channel.BUFFERED)
    val exportEvents = exports.receiveAsFlow()
    val importProgress = repository.importProgress
    val importHistory = repository.observeImportHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repository.observeImportEvents().collect { message -> messages.send(message) }
        }
    }

    private val displayOptions = combine(query, category) { search, selectedCategory -> search to selectedCategory }

    val uiState = combine(catalog, effectivePreferences, displayOptions) { currentCatalog, libraryPreferences, display ->
        libraryState(
            books = currentCatalog.visibleBooks,
            catalog = currentCatalog,
            libraryPreferences = libraryPreferences,
            search = display.first,
            selectedCategory = display.second,
            hiddenOnly = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val hiddenUiState = combine(catalog, effectivePreferences, displayOptions) { currentCatalog, libraryPreferences, display ->
        libraryState(
            books = currentCatalog.hiddenBooks,
            catalog = currentCatalog,
            libraryPreferences = libraryPreferences,
            search = display.first,
            selectedCategory = display.second,
            hiddenOnly = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState(hiddenOnly = true))

    private fun libraryState(
        books: List<LibraryBook>,
        catalog: LibraryCatalog,
        libraryPreferences: LibraryPreferences,
        search: String,
        selectedCategory: String,
        hiddenOnly: Boolean,
    ): LibraryUiState {
        val visibleCategories = books.map { it.book.category }.distinct().sorted()
        val effectiveCategory = selectedCategory.takeUnless {
            it != ALL_LIBRARY_CATEGORIES && it !in visibleCategories
        } ?: ALL_LIBRARY_CATEGORIES
        val sortedBooks = sortLibraryBooks(books, libraryPreferences)
        return LibraryUiState(
            books = sortedBooks.filter {
                (effectiveCategory == ALL_LIBRARY_CATEGORIES || it.book.category == effectiveCategory) &&
                    (search.isBlank() || it.book.title.contains(search, true) || it.book.author.contains(search, true))
            },
            query = search,
            category = effectiveCategory,
            categories = listOf(ALL_LIBRARY_CATEGORIES) + visibleCategories,
            allCategories = catalog.allCategories,
            hiddenCategories = catalog.hiddenCategories,
            sortMode = libraryPreferences.sortMode,
            layoutMode = libraryPreferences.layoutMode,
            hiddenOnly = hiddenOnly,
        )
    }

    fun search(value: String) { query.value = value }
    fun selectCategory(value: String) { category.value = value }
    fun setSortMode(mode: LibrarySortMode) = settingWrites.submit("sort") {
        if (mode != LibrarySortMode.CUSTOM) customOrderOverride.value = null
        if (mode == LibrarySortMode.CUSTOM && preferences.value.customOrder.isEmpty()) {
            preferencesRepository.setCustomOrder(catalog.value.allBooks.map { it.book.uuid })
        }
        preferencesRepository.setSortMode(mode)
    }
    fun setLayoutMode(mode: LibraryLayoutMode) = settingWrites.submit("layout") {
        preferencesRepository.setLayoutMode(mode)
    }

    fun moveBook(bookUuid: String, targetUuid: String) {
        if (bookUuid == targetUuid) return
        val order = sortLibraryBooks(
            catalog.value.allBooks,
            effectivePreferences.value.copy(sortMode = LibrarySortMode.CUSTOM),
        ).mapTo(mutableListOf()) { it.book.uuid }
        val fromIndex = order.indexOf(bookUuid)
        val targetIndex = order.indexOf(targetUuid)
        if (fromIndex < 0 || targetIndex < 0) return
        order.removeAt(fromIndex)
        order.add(targetIndex.coerceAtMost(order.size), bookUuid)
        customOrderOverride.value = order
        customOrderPersistence?.cancel()
        customOrderPersistence = viewModelScope.launch {
            delay(350)
            settingWrites.submit("order") { persistCustomOrder(order) }
        }
    }

    private suspend fun persistCustomOrder(order: List<String>) {
        preferencesRepository.setCustomOrder(order)
        preferences.first { it.customOrder == order }
        if (customOrderOverride.value == order) customOrderOverride.value = null
    }

    fun finishCustomReorder() {
        val order = customOrderOverride.value ?: return
        customOrderPersistence?.cancel()
        settingWrites.submit("order") { persistCustomOrder(order) }
    }

    fun setCategoryHidden(value: String, hidden: Boolean) = settingWrites.submit("hidden:$value") {
        preferencesRepository.setCategoryHidden(value, hidden)
        if (category.value == value) category.value = ALL_LIBRARY_CATEGORIES
    }

    fun import(uriStrings: List<String>, onComplete: () -> Unit = {}) = viewModelScope.launch {
        try {
            if (uriStrings.isEmpty()) return@launch
            val result = try {
                repository.importDocuments(uriStrings)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.send(error.message ?: context.getString(R.string.library_import_error))
                null
            }
            if (result == null) return@launch
            val success = buildString {
                if (result.importedCount > 0) append(context.getString(R.string.library_imported_count, result.importedCount))
                if (result.duplicateCount > 0) {
                    append(if (isNotEmpty()) "\n" else "")
                    append(context.getString(R.string.library_duplicate_count, result.duplicateCount))
                }
            }
            val failure = result.failures.joinToString("\n")
            messages.send(listOf(success, failure).filter(String::isNotBlank).joinToString("\n"))
        } finally {
            onComplete()
        }
    }

    fun clearFinishedImportProgress() = repository.clearFinishedImportProgress()

    fun cancelImport(runId: String) = viewModelScope.launch {
        try {
            repository.cancelImport(runId)
            messages.send(context.getString(R.string.library_import_stopped))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            messages.send(error.message ?: context.getString(R.string.library_import_error))
        }
    }

    fun retryImport(runId: String) = viewModelScope.launch {
        try {
            val result = repository.retryImport(runId)
            if (result.importedCount == 0 && result.failures.isEmpty()) {
                messages.send(context.getString(R.string.library_no_retry_files))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            messages.send(error.message ?: context.getString(R.string.library_import_error))
        }
    }

    fun clearImportHistory() = viewModelScope.launch {
        runCatching { repository.clearImportHistory() }
            .onFailure { messages.send(it.message ?: context.getString(R.string.library_clear_history_failed)) }
    }

    fun export(bookUuid: String, uriString: String) = viewModelScope.launch {
        repository.exportBook(bookUuid, uriString)
            .onSuccess { exports.send(BookExportEvent(uriString)) }
            .onFailure { messages.send(it.message ?: context.getString(R.string.library_export_failed)) }
    }

    fun exportAnnotations(bookUuid: String, uriString: String, format: com.kixyu9527.kixyubook.core.common.model.AnnotationExportFormat) = operations.submit {
        repository.exportAnnotations(bookUuid, uriString, format).getOrThrow()
        exports.send(BookExportEvent(uriString))
    }

    fun exportBooks(bookUuids: Set<String>, directoryUriString: String) = viewModelScope.launch {
        runCatching { repository.exportBooks(bookUuids, directoryUriString) }
            .onSuccess { summary ->
                exports.send(
                    BookExportEvent(
                        uriString = summary.directoryUri,
                        exportedCount = summary.exportedCount,
                        failedCount = summary.failedTitles.size,
                    ),
                )
            }
            .onFailure { messages.send(it.message ?: context.getString(R.string.library_batch_export_failed)) }
    }

    fun delete(bookUuid: String) = operations.submit(kind = UserOperationKind.DELETE) {
        repository.deleteBook(bookUuid)
    }

    fun deleteBooks(bookUuids: Set<String>) = operations.submit(kind = UserOperationKind.DELETE) {
        if (bookUuids.isNotEmpty()) repository.deleteBooks(bookUuids)
    }
    fun updateMetadata(bookUuid: String, title: String, author: String, description: String, category: String) = operations.submit {
        repository.updateBookDetails(bookUuid, title, author, description, category)
    }
    fun setCategories(bookUuids: Set<String>, value: String) = operations.submit {
        repository.setCategories(bookUuids, value)
    }
}

internal fun sortLibraryBooks(
    books: List<LibraryBook>,
    preferences: LibraryPreferences,
): List<LibraryBook> = when (preferences.sortMode) {
    LibrarySortMode.RECENT -> books
    LibrarySortMode.IMPORTED -> books.sortedByDescending { it.book.createdTime }
    LibrarySortMode.TITLE -> books.sortedWith(libraryTextComparator { it.book.title })
    LibrarySortMode.AUTHOR -> books.sortedWith(libraryTextComparator { it.book.author })
    LibrarySortMode.PROGRESS -> books.sortedByDescending { it.progress?.fraction ?: 0f }
    LibrarySortMode.CUSTOM -> {
        val byUuid = books.associateBy { it.book.uuid }
        val orderedUuids = preferences.customOrder.toSet()
        books.filterNot { it.book.uuid in orderedUuids } +
            preferences.customOrder.mapNotNull(byUuid::get)
    }
}

private fun libraryTextComparator(value: (LibraryBook) -> String): Comparator<LibraryBook> {
    val collator = Collator.getInstance(Locale.CHINA)
    return Comparator { left, right -> collator.compare(value(left), value(right)) }
}
