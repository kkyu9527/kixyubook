package com.kixyu9527.kixyubook.feature.reader

/** The location actually presented on screen, recorded by the coordinator and not by callers. */
internal data class PresentedLocation(
    val chapterPosition: Int,
    val paragraphIndex: Int,
    val charOffset: Int,
)

/**
 * Owns the reader's session state that previously required several tasks to agree on:
 *
 * - the in-flight navigation request ("latest request wins", by monotonic token), and
 * - the location that is actually presented on screen.
 *
 * Keeping both in one authority stops a superseded task from clearing a newer request or claiming a
 * position the reader never reached.
 */
internal class ReaderSessionCoordinator {
    private var sequence = 0L
    private var pendingChapterIndex: Int? = null
    private var presented: PresentedLocation? = null

    /** The chapter index a navigation is currently waiting to present, if any. */
    val pendingIndex: Int? get() = pendingChapterIndex

    /** The location currently presented on screen, if the reader has shown content yet. */
    val presentedLocation: PresentedLocation? get() = presented

    /** Starts a request for [index] and returns its token, invalidating any previous request. */
    fun begin(index: Int): Long {
        pendingChapterIndex = index
        return ++sequence
    }

    /** Invalidates the current request without starting a new one. */
    fun clear() {
        pendingChapterIndex = null
        sequence++
    }

    fun isCurrent(token: Long): Boolean = token == sequence

    /** Clears the pending index only while [token] is still the active request. */
    fun finish(token: Long) {
        if (token == sequence) pendingChapterIndex = null
    }

    /** Records the location that content was actually committed to. */
    fun onPresented(chapterPosition: Int, paragraphIndex: Int, charOffset: Int) {
        presented = PresentedLocation(chapterPosition, paragraphIndex, charOffset)
    }
}
