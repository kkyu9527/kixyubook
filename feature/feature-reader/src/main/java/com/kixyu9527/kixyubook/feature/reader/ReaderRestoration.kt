package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.kixyu9527.kixyubook.core.common.model.Chapter
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress

/**
 * A pager index belongs to an in-memory chapter window, not to the book. Preserve it during
 * same-session recreation, but never restore it into a new ViewModel's cold chapter window.
 * The new session must start at the page resolved from the durable text anchor instead.
 */
@Composable
internal fun rememberReaderPagerState(sessionId: String, initialPage: Int, pageCount: () -> Int): PagerState {
    val latestPageCount = rememberUpdatedState(pageCount)
    val saver = remember(sessionId) {
        listSaver<PagerState, Any>(
            save = { listOf(sessionId, it.currentPage, it.currentPageOffsetFraction) },
            restore = { saved ->
                if (saved[0] == sessionId) {
                    PagerState(saved[1] as Int, saved[2] as Float) { latestPageCount.value() }
                } else null
            },
        )
    }
    // Validate the session inside the Saver: rememberSaveable inputs alone are not validated
    // during saved-state restoration. Keep the registry key stable so obsolete data is consumed,
    // rather than accumulating an unused saved-state entry for every cold session.
    return rememberSaveable(sessionId, saver = saver) {
        PagerState(currentPage = initialPage) { latestPageCount.value() }
    }
}

@Composable
internal fun rememberReaderListState(sessionId: String, initialItem: Int): LazyListState {
    val saver = remember(sessionId) {
        listSaver<LazyListState, Any>(
            save = { listOf(sessionId, it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
            restore = { saved ->
                if (saved[0] == sessionId) LazyListState(saved[1] as Int, saved[2] as Int) else null
            },
        )
    }
    return rememberSaveable(sessionId, saver = saver) { LazyListState(initialItem) }
}

/** Stable chapter keys also survive reindexing/reimport when Room row IDs change. */
internal fun readerProgressChapterIndex(chapters: List<Chapter>, progress: ReadingProgress): Int {
    if (progress.chapterKey.isNotBlank()) {
        chapters.indexOfFirst { it.chapterKey == progress.chapterKey }.takeIf { it >= 0 }
            ?.let { return it }
    }
    return chapters.indexOfFirst { it.id == progress.chapterId }
}
