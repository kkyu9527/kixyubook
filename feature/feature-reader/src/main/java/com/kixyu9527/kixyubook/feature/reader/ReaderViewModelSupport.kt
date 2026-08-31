package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.Book
import com.kixyu9527.kixyubook.core.common.model.Chapter
import com.kixyu9527.kixyubook.core.common.model.ChapterContent
import com.kixyu9527.kixyubook.core.common.model.ChapterLoadPriority
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.TextCorrection
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.reader.engine.ReaderChapter
import kotlinx.coroutines.Deferred

internal data class SearchReturnPosition(
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val charOffset: Int,
)

internal data class ChapterLoadRequest(
    val priority: ChapterLoadPriority,
    val deferred: Deferred<ReaderChapter?>,
)

internal data class InitialReaderPresentation(
    val settings: ReaderSettings,
    val fontPath: String?,
    val fonts: List<UserFont>,
)

internal data class InitialReaderData(
    val book: Book?,
    val chapters: List<Chapter>,
    val progress: ReadingProgress?,
    val presentation: InitialReaderPresentation,
)

internal fun ChapterContent.toReaderChapter() = ReaderChapter(
    chapter.id,
    chapter.bookUuid,
    chapter.title,
    chapter.index,
    paragraphs,
)

internal fun hasReaderMovedFromOpening(
    openingChapterId: Long?,
    openingPosition: Int,
    openingCharOffset: Int = 0,
    currentChapterId: Long,
    currentPosition: Int,
    currentCharOffset: Int = 0,
): Boolean = openingChapterId != currentChapterId ||
    openingPosition != currentPosition ||
    openingCharOffset != currentCharOffset

internal fun shouldApplySyncedProgress(
    incomingUpdatedAt: Long,
    acceptedUpdatedAt: Long,
    latestLocalWriteAt: Long,
): Boolean = incomingUpdatedAt > acceptedUpdatedAt && incomingUpdatedAt > latestLocalWriteAt

internal fun nextProgressUpdatedAt(currentTime: Long, latestLocalWriteAt: Long): Long =
    if (latestLocalWriteAt >= currentTime && latestLocalWriteAt < Long.MAX_VALUE) {
        latestLocalWriteAt + 1
    } else {
        currentTime
    }

internal fun changedCorrectionChapterPositions(
    previous: List<TextCorrection>,
    current: List<TextCorrection>,
    chapters: List<Chapter>,
): Set<Int> {
    if (previous == current) return emptySet()
    val previousById = previous.associateBy(TextCorrection::uuid)
    val currentById = current.associateBy(TextCorrection::uuid)
    val changed = (previousById.keys + currentById.keys).flatMap { uuid ->
        val old = previousById[uuid]
        val new = currentById[uuid]
        if (old == new) emptyList() else listOfNotNull(old, new)
    }
    return chapters.mapIndexedNotNull { position, chapter ->
        position.takeIf {
            changed.any { correction ->
                (correction.chapterKey.isNotBlank() && correction.chapterKey == chapter.chapterKey) ||
                    correction.chapterIndex == chapter.index
            }
        }
    }.toSet()
}

// Decode only what the pager can immediately reach. The wider chapter window remains persisted
// by the EPUB binary cache/background index instead of being retained as live paragraph objects.
internal const val CHAPTER_PREFETCH_RADIUS = 2
internal const val RENDER_PREFETCH_RADIUS = 2
internal const val SLOW_NAVIGATION_MS = 150L
internal const val PROGRESS_CHECKPOINT_TIMEOUT_MS = 1_500L
