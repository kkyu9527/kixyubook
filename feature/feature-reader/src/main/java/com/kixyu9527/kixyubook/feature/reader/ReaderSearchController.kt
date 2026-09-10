package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.BookSearchResult
import com.kixyu9527.kixyubook.core.common.model.BookSearchStage
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.reader.engine.contentParagraphs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val recordHistory: suspend (String) -> Unit,
    private val recordOrigin: (chapterIndex: Int, paragraphIndex: Int) -> Unit,
    private val jumpToPosition: (chapterIndex: Int, paragraphIndex: Int) -> Unit,
    private val returnToOrigin: () -> Unit,
    private val failureMessage: () -> String,
) {
    private var originRecorded = false
    private var searchJob: Job? = null

    fun search(query: String, searchScope: ReaderSearchScope) {
        val normalized = query.trim()
        searchJob?.cancel()
        originRecorded = false
        if (normalized.isBlank()) {
            clearState()
            return
        }
        val immediateResults = currentChapterResults(normalized)
        state.update {
            it.copy(
                searchQuery = normalized,
                searchScope = searchScope,
                searchHistory = (listOf(normalized) + it.searchHistory)
                    .distinct().take(MAX_SEARCH_HISTORY),
                searchResults = immediateResults,
                selectedSearchIndex = if (immediateResults.isEmpty()) -1 else 0,
                searchReturnAvailable = false,
                searchInProgress = searchScope == ReaderSearchScope.BOOK,
                searchProgress = if (searchScope == ReaderSearchScope.BOOK) 0f else 1f,
                searchStage = BookSearchStage.INDEXING,
                searchCompleted = 0,
                searchTotal = 0,
                searchError = null,
            )
        }
        searchJob = scope.launch {
            try {
                recordHistory(normalized)
                if (searchScope == ReaderSearchScope.CURRENT_CHAPTER) return@launch
                val results = books.searchBook(
                    bookUuid = bookUuid,
                    query = normalized,
                    onProgress = { progress ->
                        state.update { current ->
                            if (current.searchQuery != normalized || current.searchScope != searchScope) current
                            else current.copy(
                                searchProgress = progress.fraction,
                                searchStage = progress.stage,
                                searchCompleted = progress.completed,
                                searchTotal = progress.total,
                            )
                        }
                    },
                    onResults = { partialResults ->
                        publishResults(normalized, searchScope, immediateResults + partialResults)
                    },
                )
                val merged = mergeResults(immediateResults + results)
                state.update { current ->
                    if (current.searchQuery != normalized || current.searchScope != searchScope) current
                    else current.withSearchResults(merged).copy(
                        searchInProgress = false,
                        searchProgress = 1f,
                        searchCompleted = current.searchTotal,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.update { current ->
                    if (current.searchQuery != normalized || current.searchScope != searchScope) current
                    else current.copy(
                        searchInProgress = false,
                        searchError = error.message ?: failureMessage(),
                    )
                }
            }
        }
    }

    private fun currentChapterResults(query: String): List<BookSearchResult> {
        val snapshot = state.value
        val chapter = snapshot.chapter ?: return emptyList()
        val chapterIndex = snapshot.chapters
            .getOrNull(snapshot.chapterIndex)
            ?.index
            ?: snapshot.chapterIndex
        return chapter.contentParagraphs()
            .asSequence()
            .filter { paragraph ->
                paragraph.kind == ParagraphKind.TEXT &&
                    paragraph.text.contains(query, ignoreCase = true)
            }
            .map { paragraph ->
                BookSearchResult(
                    chapterId = chapter.id,
                    chapterTitle = chapter.title,
                    chapterIndex = chapterIndex,
                    paragraphIndex = paragraph.index,
                    text = paragraph.text,
                )
            }
            .toList()
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
        searchJob?.cancel()
        searchJob = null
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
                searchInProgress = false,
                searchProgress = 0f,
                searchStage = BookSearchStage.INDEXING,
                searchCompleted = 0,
                searchTotal = 0,
                searchError = null,
            )
        }
    }

    private fun publishResults(
        query: String,
        searchScope: ReaderSearchScope,
        candidates: List<BookSearchResult>,
    ) {
        state.update { current ->
            if (current.searchQuery != query || current.searchScope != searchScope) current
            else current.withSearchResults(mergeResults(current.searchResults + candidates))
        }
    }

    private fun mergeResults(candidates: List<BookSearchResult>): List<BookSearchResult> = candidates
        .distinctBy { it.chapterId to it.paragraphIndex }
        .sortedWith(compareBy(BookSearchResult::chapterIndex, BookSearchResult::paragraphIndex))

    private fun ReaderUiState.withSearchResults(results: List<BookSearchResult>): ReaderUiState {
        val selected = searchResults.getOrNull(selectedSearchIndex)
        val selectedIndex = selected?.let { target ->
            results.indexOfFirst {
                it.chapterId == target.chapterId && it.paragraphIndex == target.paragraphIndex
            }.takeIf { it >= 0 }
        } ?: if (results.isEmpty()) -1 else 0
        return copy(searchResults = results, selectedSearchIndex = selectedIndex)
    }

    private companion object {
        const val MAX_SEARCH_HISTORY = 10
    }
}
