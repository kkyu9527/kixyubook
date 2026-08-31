package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns full-book search state and the temporary location used to return from a result.
 *
 * Keeping this outside [ReaderViewModel] makes search an independent overlay workflow instead of
 * mixing it with chapter loading, progress checkpoints and the frame-sensitive prefetch pipeline.
 */
internal class ReaderSearchController(
    private val scope: CoroutineScope,
    private val bookUuid: String,
    private val books: BookRepository,
    private val state: MutableStateFlow<ReaderUiState>,
    private val recordOrigin: (chapterIndex: Int, paragraphIndex: Int) -> Unit,
    private val jumpToPosition: (chapterIndex: Int, paragraphIndex: Int) -> Unit,
    private val returnToOrigin: () -> Unit,
) {
    private var originRecorded = false

    fun search(query: String) {
        scope.launch {
            val normalized = query.trim()
            originRecorded = false
            if (normalized.isBlank()) {
                clearState()
                return@launch
            }
            val results = books.searchBook(bookUuid, normalized)
            state.update {
                it.copy(
                    searchQuery = normalized,
                    searchResults = results,
                    selectedSearchIndex = if (results.isEmpty()) -1 else 0,
                    searchReturnAvailable = false,
                )
            }
        }
    }

    fun select(index: Int) {
        val snapshot = state.value
        if (snapshot.searchResults.isEmpty()) return
        val safeIndex = index.coerceIn(0, snapshot.searchResults.lastIndex)
        val result = snapshot.searchResults.getOrNull(safeIndex) ?: return
        if (!originRecorded) {
            recordOrigin(result.chapterIndex, result.paragraphIndex)
            originRecorded = true
        }
        state.update {
            it.copy(selectedSearchIndex = safeIndex, searchReturnAvailable = true)
        }
        jumpToPosition(result.chapterIndex, result.paragraphIndex)
    }

    fun returnToReadingPosition() {
        if (!originRecorded) return
        originRecorded = false
        state.update { it.copy(searchReturnAvailable = false) }
        returnToOrigin()
    }

    fun move(delta: Int) {
        val snapshot = state.value
        if (snapshot.searchResults.isEmpty()) return
        val current = snapshot.selectedSearchIndex.coerceAtLeast(0)
        select((current + delta).coerceIn(snapshot.searchResults.indices))
    }

    fun clear() {
        originRecorded = false
        clearState()
    }

    private fun clearState() {
        state.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                selectedSearchIndex = -1,
                searchReturnAvailable = false,
            )
        }
    }
}
