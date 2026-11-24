package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.text.TextMeasurer
import com.kixyu9527.kixyubook.core.reader.engine.ReaderLayoutSpec
import com.kixyu9527.kixyubook.core.reader.engine.ReaderPaginationCoordinator
import com.kixyu9527.kixyubook.core.reader.engine.rememberMeasuredReaderPages

/** Fill the existing bounded pagination LRU only after the required three-chapter window. */
@Composable
internal fun ReaderReadAheadLayouts(
    state: ReaderContentState,
    spec: ReaderLayoutSpec,
    coordinator: ReaderPaginationCoordinator,
    measurer: TextMeasurer,
    paused: Boolean,
) {
    for (distance in listOf(2, -2)) {
        val chapter = state.prefetchedChapters[state.chapterIndex + distance] ?: continue
        key(chapter.id) {
            rememberMeasuredReaderPages(
                chapter = chapter,
                contentHash = state.book?.contentHash.orEmpty(),
                spec = spec,
                fontPath = state.fontPath,
                showRegularChapterTitle = state.settings.showChapterTitle,
                coordinator = coordinator,
                measurer = measurer,
                prefetch = true,
                paused = paused,
                allowPartialResults = false,
            )
        }
    }
}
