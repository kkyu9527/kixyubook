package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.entity.ChapterEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/** Search every reading-order chapter; result limits must never silently hide later chapters. */
internal class BookSearchScanner(private val dao: BookDao) {
    suspend fun search(
        chapters: List<ChapterEntity>,
        query: String,
        corrections: List<TextCorrection>,
        ensureIndexed: suspend (ChapterEntity) -> ChapterEntity,
        onProgress: suspend (BookSearchProgress) -> Unit,
        onResults: suspend (List<BookSearchResult>) -> Unit,
        retainResults: Boolean = true,
    ): List<BookSearchResult> {
        val byParagraph = corrections.filter { it.status == TextCorrectionStatus.ACTIVE }
            .groupBy { it.chapterIndex to it.paragraphIndex }
        val results = mutableListOf<BookSearchResult>()
        chapters.forEachIndexed { chapterOffset, stored ->
            currentCoroutineContext().ensureActive()
            if (!stored.indexed) onProgress(BookSearchProgress(BookSearchStage.INDEXING, chapterOffset, chapters.size))
            val chapter = if (stored.indexed) stored else ensureIndexed(stored)
            var afterIndex = -1
            while (true) {
                currentCoroutineContext().ensureActive()
                val batch = dao.getParagraphBatch(chapter.id, afterIndex, SEARCH_BATCH_SIZE)
                if (batch.isEmpty()) break
                val matches = buildList {
                    batch.forEach { paragraph ->
                        currentCoroutineContext().ensureActive()
                        val text = applyCorrections(paragraph.text, byParagraph[chapter.chapterIndex to paragraph.paragraphIndex].orEmpty())
                        val matches = text.searchMatches(query)
                        if (matches.isNotEmpty()) add(BookSearchResult(chapter.id, chapter.title, chapter.chapterIndex,
                            paragraph.paragraphIndex, searchExcerpt(text, matches.first().start, query.length), matches))
                    }
                }
                if (retainResults) results.addAll(matches)
                if (matches.isNotEmpty()) onResults(matches)
                afterIndex = batch.last().paragraphIndex
                yield()
            }
            onProgress(BookSearchProgress(BookSearchStage.SEARCHING, chapterOffset + 1, chapters.size))
        }
        return results
    }
}

/** Search results carry previews only; navigation remains anchored to the original paragraph. */
internal fun searchExcerpt(text: String, match: Int, queryLength: Int): String {
    var start = (match - 48).coerceAtLeast(0)
    var end = (match + queryLength + 112).coerceAtMost(text.length)
    if (start > 0 && text[start].isLowSurrogate()) start--
    if (end < text.length && end > 0 && text[end - 1].isHighSurrogate()) end++
    return (if (start > 0) "…" else "") + text.substring(start, end) + if (end < text.length) "…" else ""
}

private const val SEARCH_BATCH_SIZE = 128
