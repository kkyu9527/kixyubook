package com.kixyu9527.kixyubook.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val query: String = "",
    val category: String = "全部",
    val categories: List<String> = listOf("全部"),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow("全部")
    private val messages = Channel<String>(Channel.BUFFERED)
    val messageEvents = messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.observeImportEvents().collect { message -> messages.send(message) }
        }
    }

    val uiState = combine(repository.observeLibrary(), query, category) { books, search, selectedCategory ->
        LibraryUiState(
            books = books.filter {
                (selectedCategory == "全部" || it.book.category == selectedCategory) &&
                    (search.isBlank() || it.book.title.contains(search, true) || it.book.author.contains(search, true))
            },
            query = search,
            category = selectedCategory,
            categories = listOf("全部") + books.map { it.book.category }.distinct().sorted(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun search(value: String) { query.value = value }
    fun selectCategory(value: String) { category.value = value }

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

    fun delete(bookUuid: String) = viewModelScope.launch { repository.deleteBook(bookUuid) }
    fun deleteBooks(bookUuids: Set<String>) = viewModelScope.launch {
        if (bookUuids.isNotEmpty()) repository.deleteBooks(bookUuids)
    }
    fun updateMetadata(bookUuid: String, title: String, author: String, description: String) = viewModelScope.launch {
        runCatching { repository.updateBookMetadata(bookUuid, title, author, description) }
            .onFailure { messages.send(it.message ?: "修改失败") }
    }
    fun reparseTxt(bookUuid: String) = viewModelScope.launch {
        repository.reparseTxt(bookUuid)
            .onSuccess { messages.send("正文和目录已重新解析") }
            .onFailure { messages.send(it.message ?: "重新解析失败") }
    }
    fun setCategory(bookUuid: String, value: String) = viewModelScope.launch { repository.setCategory(bookUuid, value) }
}
