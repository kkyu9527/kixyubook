package com.kixyu9527.kixyubook.feature.reader

/** Accumulates only explicitly active reader intervals across pause/resume cycles. */
internal class ReadingSessionTimer(
    private val elapsedRealtime: () -> Long,
) {
    private var accumulatedMillis = 0L
    private var activeSince: Long? = null

    fun setActive(active: Boolean) {
        val now = elapsedRealtime()
        if (active) {
            if (activeSince == null) activeSince = now
        } else {
            activeSince?.let { started ->
                accumulatedMillis += (now - started).coerceAtLeast(0L)
                activeSince = null
            }
        }
    }

    fun finish(): Long {
        setActive(false)
        val total = accumulatedMillis
        // Reset so a re-entered reader scene records a fresh session instead of adding to the total.
        accumulatedMillis = 0L
        return total
    }
}
