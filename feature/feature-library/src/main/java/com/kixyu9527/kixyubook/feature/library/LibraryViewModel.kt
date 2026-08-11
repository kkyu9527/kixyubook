package com.kixyu9527.kixyubook.feature.library

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

data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val query: String = "",
    val category: String = "全部",
    val categories: List<String> = listOf("全部"),
    val allCategories: List<String> = emptyList(),
    val hiddenCategories: Set<String> = emptySet(),
    val sortMode: LibrarySortMode = LibrarySortMode.RECENT,
    val layoutMode: LibraryLayoutMode = LibraryLayoutMode.LIST,
    val hiddenOnly: Boolean = false,
)

data class BookExportEvent(val uriString: String)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
    catalogRepository: LibraryCatalogRepository,
    private val preferencesRepository: LibraryPreferencesRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow("全部")
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
    val messageEvents = messages.receiveAsFlow()
    private val exports = Channel<BookExportEvent>(Channel.BUFFERED)
    val exportEvents = exports.receiveAsFlow()

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
            it != "全部" && it !in visibleCategories
        } ?: "全部"
        val sortedBooks = sortLibraryBooks(books, libraryPreferences)
        return LibraryUiState(
            books = sortedBooks.filter {
                (effectiveCategory == "全部" || it.book.category == effectiveCategory) &&
                    (search.isBlank() || it.book.title.contains(search, true) || it.book.author.contains(search, true))
            },
            query = search,
            category = effectiveCategory,
            categories = listOf("全部") + visibleCategories,
            allCategories = catalog.allCategories,
            hiddenCategories = catalog.hiddenCategories,
            sortMode = libraryPreferences.sortMode,
            layoutMode = libraryPreferences.layoutMode,
            hiddenOnly = hiddenOnly,
        )
    }

    fun search(value: String) { query.value = value }
    fun selectCategory(value: String) { category.value = value }
    fun setSortMode(mode: LibrarySortMode) = viewModelScope.launch {
        if (mode != LibrarySortMode.CUSTOM) customOrderOverride.value = null
        if (mode == LibrarySortMode.CUSTOM && preferences.value.customOrder.isEmpty()) {
            preferencesRepository.setCustomOrder(catalog.value.allBooks.map { it.book.uuid })
        }
        preferencesRepository.setSortMode(mode)
    }
    fun setLayoutMode(mode: LibraryLayoutMode) = viewModelScope.launch {
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
            persistCustomOrder(order)
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
        customOrderPersistence = viewModelScope.launch { persistCustomOrder(order) }
    }

    fun setCategoryHidden(value: String, hidden: Boolean) = viewModelScope.launch {
        preferencesRepository.setCategoryHidden(value, hidden)
        if (category.value == value) category.value = "全部"
    }

    fun import(uriStrings: List<String>, onComplete: () -> Unit = {}) = viewModelScope.launch {
        try {
            if (uriStrings.isEmpty()) return@launch
            val result = try {
                repository.importDocuments(uriStrings)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messages.send(error.message ?: "导入失败，请重新选择文件")
                null
            }
            if (result == null) return@launch
            val success = buildString {
                if (result.importedCount > 0) append("已导入 ${result.importedCount} 本书")
                if (result.duplicateCount > 0) {
                    append(if (isNotEmpty()) "，" else "")
                    append("已跳过 ${result.duplicateCount} 本重复书籍")
                }
            }
            val failure = result.failures.joinToString("\n")
            messages.send(listOf(success, failure).filter(String::isNotBlank).joinToString("\n"))
        } finally {
            onComplete()
        }
    }

    fun export(bookUuid: String, uriString: String) = viewModelScope.launch {
        repository.exportBook(bookUuid, uriString)
            .onSuccess { exports.send(BookExportEvent(uriString)) }
            .onFailure { messages.send(it.message ?: "书籍导出失败") }
    }

    fun delete(bookUuid: String) = viewModelScope.launch { repository.deleteBook(bookUuid) }
    fun deleteBooks(bookUuids: Set<String>) = viewModelScope.launch {
        if (bookUuids.isNotEmpty()) repository.deleteBooks(bookUuids)
    }
    fun updateMetadata(bookUuid: String, title: String, author: String, description: String) = viewModelScope.launch {
        runCatching { repository.updateBookMetadata(bookUuid, title, author, description) }
            .onFailure { messages.send(it.message ?: "修改失败") }
    }
    fun setCategory(bookUuid: String, value: String) = viewModelScope.launch { repository.setCategory(bookUuid, value) }
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
