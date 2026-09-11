package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.reader.engine.ReaderChapter
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Only the search boundary is fake; unexpected repository calls fail instead of silently passing. */
internal class ScriptedSearchRepository : BookRepository by unsupportedBookRepository() {
    data class Request(
        val query: String,
        val progress: suspend (BookSearchProgress) -> Unit,
        val partial: suspend (List<BookSearchResult>) -> Unit,
        val result: CompletableDeferred<List<BookSearchResult>> = CompletableDeferred(),
    )
    val requests = mutableListOf<Request>()
    override suspend fun searchBook(
        bookUuid: String, query: String,
        onProgress: suspend (BookSearchProgress) -> Unit,
        onResults: suspend (List<BookSearchResult>) -> Unit,
        retainResults: Boolean,
    ): List<BookSearchResult> {
        check(bookUuid == "book")
        val request = Request(query, onProgress, onResults)
        requests += request
        return request.result.await()
    }
}

private fun unsupportedBookRepository(): BookRepository = Proxy.newProxyInstance(
    BookRepository::class.java.classLoader, arrayOf(BookRepository::class.java),
) { _, method, _ -> error("Unexpected repository call: ${method.name}") } as BookRepository

internal class ReaderSearchFixture : AutoCloseable {
    val repository = ScriptedSearchRepository()
    // Inline until suspension: deterministic progress/cancellation without sleeps or Android Main.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val state = MutableStateFlow(ReaderUiState(
        chapters = listOf(Chapter(20, "book", "当前章节", 5)),
        chapter = ReaderChapter(20, "book", "当前章节", 5, listOf(Paragraph(1, 20, 3, "黄金与搜索目标"))),
        loading = false,
    ))
    val history = mutableListOf<String>()
    val origins = mutableListOf<Pair<Int, Int>>()
    val jumps = mutableListOf<Pair<Int, Int>>()
    var returns = 0
    val controller = ReaderSearchController(
        scope, "book", repository, state, { history += it },
        { chapter, paragraph -> origins += chapter to paragraph },
        { chapter, paragraph -> jumps += chapter to paragraph }, { returns++ },
        { "Search failed" },
    )
    override fun close() { scope.cancel() }
}
