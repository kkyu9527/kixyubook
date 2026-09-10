package com.kixyu9527.kixyubook.feature.reader

/** Measures a location action through the target's ready callback, not just its repository read. */
internal class ReaderJourneyTracker(
    private val clock: () -> Long,
    private val report: (ReaderLocationSource, Long, String) -> Unit,
) {
    private data class Pending(val source: ReaderLocationSource, val chapter: Int, val afterVersion: Int, val started: Long)
    private var pending: Pending? = null

    fun begin(source: ReaderLocationSource, chapter: Int, afterVersion: Int) {
        finish("superseded")
        pending = Pending(source, chapter, afterVersion, clock())
    }

    fun rendered(chapter: Int, version: Int) {
        val request = pending ?: return
        if (request.chapter == chapter && version > request.afterVersion) finish("ready")
    }

    fun failed(chapter: Int) {
        if (pending?.chapter == chapter) finish("failed")
    }

    fun finish(outcome: String = "cancelled") {
        val request = pending ?: return
        pending = null
        report(request.source, (clock() - request.started).coerceAtLeast(0), outcome)
    }
}
