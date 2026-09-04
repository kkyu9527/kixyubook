package com.kixyu9527.kixyubook.core.reader.engine

/** Finds a human-readable split point without retaining another copy of the whole source text. */
internal fun CharSequence.readableSplitIndex(
    endExclusive: Int,
    minimumIndex: Int = endExclusive / 2,
): Int {
    val safeEnd = endExclusive.coerceIn(0, length)
    val safeMinimum = minimumIndex.coerceIn(0, safeEnd)
    for (index in safeEnd - 1 downTo safeMinimum) {
        if (this[index].isWhitespace() || this[index] in READABLE_TEXT_BOUNDARIES) return index + 1
    }
    return safeEnd
}

/** Splits a malformed or minified long line into bounded paragraphs at punctuation when possible. */
internal fun String.readableChunks(maxChars: Int): Sequence<String> = sequence {
    require(maxChars > 0)
    var start = 0
    while (start < length) {
        val end = (start + maxChars).coerceAtMost(length)
        val split = if (end == length) {
            end
        } else {
            val relative = subSequence(start, end)
            start + relative.readableSplitIndex(
                endExclusive = relative.length,
                minimumIndex = relative.length / 2,
            )
        }
        val safeSplit = split.takeIf { it > start } ?: end
        substring(start, safeSplit).trim().takeIf(String::isNotEmpty)?.let { yield(it) }
        start = safeSplit
    }
}

private val READABLE_TEXT_BOUNDARIES = setOf('。', '！', '？', '；', '.', '!', '?', ';', '，', ',')
