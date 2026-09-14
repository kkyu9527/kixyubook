package com.kixyu9527.kixyubook.feature.reader

/**
 * Owns the reader's in-flight navigation request.
 *
 * A monotonically increasing token makes "latest request wins" explicit: a job that was superseded
 * can neither clear the pending index nor surface its failure, even when the same chapter index is
 * requested twice. This is the boundary that previously relied on every task agreeing to compare
 * `pendingChapterIndex == index`, which a stale job could still match.
 */
internal class ReaderSessionCoordinator {
    private var sequence = 0L
    private var pendingChapterIndex: Int? = null

    /** The chapter index a navigation is currently waiting to present, if any. */
    val pendingIndex: Int? get() = pendingChapterIndex

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
}
