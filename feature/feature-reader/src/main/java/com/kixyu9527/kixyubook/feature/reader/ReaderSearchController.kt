package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.BookSearchResult
import com.kixyu9527.kixyubook.core.common.model.BookSearchStage
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import com.kixyu9527.kixyubook.core.common.model.searchMatches
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.reader.engine.contentParagraphs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import java.io.File

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
    private val jumpToPosition: (chapterIndex: Int, paragraphIndex: Int, charOffset: Int) -> Unit,
    private val returnToOrigin: () -> Unit,
    private val failureMessage: () -> String,
    private val resultDirectory: File? = null,
) {
    private var originRecorded = false
    private var searchJob: Job? = null
    private var resultStore = SearchResultStore(resultDirectory)
    @Volatile private var generation = 0L
    private var lastPublished = 0L
    private val storageDispatcher = if (resultDirectory == null) Dispatchers.Unconfined else Dispatchers.IO
    /** Chapter scanning is CPU-bound; it must not occupy the caller or the storage pool. */
    private val analysisDispatcher = if (resultDirectory == null) Dispatchers.Unconfined else Dispatchers.Default

    fun search(query: String, searchScope: ReaderSearchScope) {
        val normalized = query.trim()
        searchJob?.cancel()
        val token = ++generation
        val oldStore = resultStore
        val store = SearchResultStore(resultDirectory).also { resultStore = it }
        dispose(oldStore)
        lastPublished = 0L
        originRecorded = false
        if (normalized.isBlank()) {
            clearState()
            return
        }
        state.update {
            it.copy(
                searchQuery = normalized,
                searchScope = searchScope,
                searchHistory = (listOf(normalized) + it.searchHistory)
                    .distinct().take(MAX_SEARCH_HISTORY),
                searchResults = emptyList(),
                searchResultStart = 0,
                searchMatchCount = 0,
                searchOccurrenceCount = 0,
                selectedSearchIndex = -1,
                selectedSearchMatch = 0,
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
                // A large chapter must be scanned off the caller thread; the generation token below
                // still discards this batch if the query changed while it ran.
                val immediateResults = withContext(analysisDispatcher) { currentChapterResults(normalized) }
                if (token != generation) return@launch
                state.update {
                    it.copy(
                        searchResults = immediateResults,
                        searchMatchCount = immediateResults.size,
                        searchOccurrenceCount = immediateResults.sumOf { result -> result.matches.size },
                        selectedSearchIndex = if (immediateResults.isEmpty()) -1 else 0,
                        selectedSearchMatch = 0,
                    )
                }
                withContext(storageDispatcher) { store.add(immediateResults) }
                try { recordHistory(normalized) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.record(
                        com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category.READER,
                        "search_history_save_failed",
                    )
                }
                if (searchScope == ReaderSearchScope.CURRENT_CHAPTER) {
                    publishResults(token, store, emptyList(), force = true)
                    return@launch
                }
                val results = books.searchBook(
                    bookUuid = bookUuid,
                    query = normalized,
                    retainResults = false,
                    onProgress = { progress ->
                        state.update { current ->
                            if (token != generation) current
                            else current.copy(
                                searchProgress = progress.fraction,
                                searchStage = progress.stage,
                                searchCompleted = progress.completed,
                                searchTotal = progress.total,
                            )
                        }
                    },
                    onResults = { partialResults ->
                        publishResults(token, store, partialResults)
                    },
                )
                publishResults(token, store, results, force = true)
                state.update { current ->
                    if (token != generation) current
                    else current.copy(
                        searchInProgress = false,
                        searchProgress = 1f,
                        searchCompleted = current.searchTotal,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // Flush the last successful batch even when the next chapter fails.
                try { publishResults(token, store, emptyList(), force = true) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Keep the already visible page if temporary storage failed. */ }
                state.update { current ->
                    if (token != generation) current
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
            .filter { paragraph -> paragraph.kind == ParagraphKind.TEXT }
            .mapNotNull { paragraph ->
                val matches = paragraph.text.searchMatches(query)
                if (matches.isEmpty()) null
                else BookSearchResult(
                    chapterId = chapter.id,
                    chapterTitle = chapter.title,
                    chapterIndex = chapterIndex,
                    paragraphIndex = paragraph.index,
                    text = paragraph.text,
                    matches = matches,
                )
            }
            .toList()
    }

    fun select(index: Int, selectLastMatch: Boolean = false) {
        val snapshot = state.value
        if (snapshot.searchResults.isEmpty()) return
        val safeIndex = index.coerceIn(0, snapshot.searchResults.lastIndex)
        val result = snapshot.searchResults.getOrNull(safeIndex) ?: return
        if (!originRecorded) {
            recordOrigin(result.chapterIndex, result.paragraphIndex)
            originRecorded = true
        }
        // Entering a paragraph backwards must land on its last hit, not its first.
        val matchIndex = if (selectLastMatch) result.matches.lastIndex.coerceAtLeast(0) else 0
        state.update {
            it.copy(selectedSearchIndex = safeIndex, selectedSearchMatch = matchIndex, searchReturnAvailable = true)
        }
        jumpToMatch(result, matchIndex)
    }

    /** Moves to the previous/next occurrence, crossing into the neighbouring matching paragraph. */
    fun moveMatch(delta: Int) {
        val snapshot = state.value
        val result = snapshot.searchResults.getOrNull(snapshot.selectedSearchIndex) ?: return
        val target = snapshot.selectedSearchMatch + delta
        if (target in result.matches.indices) {
            state.update { it.copy(selectedSearchMatch = target) }
            jumpToMatch(result, target)
        } else {
            // Leaving backwards selects the previous paragraph's final hit, including when that
            // paragraph lives on another result page.
            move(delta, selectLastMatch = delta < 0)
        }
    }

    /** Re-runs the active query after the underlying text changed (correction saved / reparse). */
    fun invalidate() {
        val query = state.value.searchQuery
        if (query.isBlank()) return
        search(query, state.value.searchScope)
    }

    private fun jumpToMatch(result: BookSearchResult, matchIndex: Int) {
        val offset = result.matches.getOrNull(matchIndex)?.start ?: 0
        jumpToPosition(result.chapterIndex, result.paragraphIndex, offset)
    }

    fun returnToReadingPosition() {
        if (!originRecorded) return
        originRecorded = false
        state.update { it.copy(searchReturnAvailable = false) }
        returnToOrigin()
    }

    fun move(delta: Int, selectLastMatch: Boolean = false) {
        val snapshot = state.value
        if (snapshot.searchResults.isEmpty()) return
        val current = snapshot.selectedSearchIndex.coerceAtLeast(0)
        val localTarget = current + delta
        if (localTarget in snapshot.searchResults.indices) select(localTarget, selectLastMatch)
        else {
            val target = (snapshot.searchResultStart + localTarget).coerceIn(0, (snapshot.searchMatchCount - 1).coerceAtLeast(0))
            val token = generation
            val store = resultStore
            launchPageRequest(token) {
                publishPage(token, store, target)
                if (token == generation) select(target - state.value.searchResultStart, selectLastMatch)
            }
        }
    }

    fun clear() {
        generation++
        searchJob?.cancel()
        searchJob = null
        originRecorded = false
        clearState()
        val old = resultStore
        resultStore = SearchResultStore(resultDirectory)
        dispose(old)
    }

    private fun clearState() {
        state.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                searchResultStart = 0,
                searchMatchCount = 0,
                searchOccurrenceCount = 0,
                selectedSearchIndex = -1,
                selectedSearchMatch = 0,
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

    private suspend fun publishResults(token: Long, store: SearchResultStore, candidates: List<BookSearchResult>, force: Boolean = false) {
        if (token != generation) return
        withContext(storageDispatcher) { store.add(candidates) }
        val now = System.nanoTime()
        if (!force && resultDirectory != null && now - lastPublished < 120_000_000L) return
        lastPublished = now
        val selected = state.value.let { it.searchResults.getOrNull(it.selectedSearchIndex) }
        val start = withContext(storageDispatcher) { selected?.let(store::indexOf)?.takeIf { it >= 0 } }
            ?: state.value.searchResultStart
        publishPage(token, store, start)
    }

    private suspend fun publishPage(token: Long, store: SearchResultStore, start: Int) {
        val page = withContext(storageDispatcher) { store.page(start) }
        state.update { current ->
            if (token != generation) current else current.withSearchResults(page.rows)
                .copy(
                    searchResultStart = page.start,
                    searchMatchCount = page.total,
                    searchOccurrenceCount = store.occurrenceCount,
                )
        }
    }

    fun movePage(delta: Int) {
        val token = generation
        val store = resultStore
        val start = state.value.searchResultStart + delta * SearchResultStore.PAGE_SIZE
        launchPageRequest(token) { publishPage(token, store, start) }
    }

    private fun launchPageRequest(token: Long, action: suspend () -> Unit) = scope.launch {
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            state.update { if (token == generation) it.copy(searchError = failureMessage()) else it }
        }
    }

    private fun dispose(store: SearchResultStore) {
        // ViewModel scope is already cancelled in onCleared; cleanup must still finish off-main.
        cleanupScope.launch { runCatching { store.close() } }
    }

    fun close() {
        generation++
        searchJob?.cancel()
        dispose(resultStore)
    }

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
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
