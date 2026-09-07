package com.kixyu9527.kixyubook.core.reader.engine

import java.io.Reader

internal data class TextLineFragment(val lineIndex: Int, val text: String, val continuation: Boolean)

/** Incremental decoded input, including CRLF across buffers and surrogate pairs across chunks. */
internal fun Reader.boundedLines(maxChars: Int): Sequence<TextLineFragment> = sequence {
    require(maxChars >= 2)
    val input = CharArray(minOf(8192, maxChars))
    val pending = StringBuilder(maxChars + input.size)
    var lineIndex = 0
    var continuation = false
    var afterCr = false
    while (true) {
        val count = read(input)
        if (count < 0) break
        for (i in 0 until count) {
            val char = input[i]
            if (afterCr && char == '\n') {
                afterCr = false
                continue
            }
            afterCr = false
            if (char == '\r' || char == '\n') {
                yield(TextLineFragment(lineIndex++, pending.toString(), continuation))
                pending.setLength(0)
                continuation = false
                afterCr = char == '\r'
            } else {
                pending.append(char)
                // One character of lookahead preserves the final fragment exactly like readLine.
                if (pending.length > maxChars) {
                    var split = pending.readableSplitIndex(maxChars, maxChars / 2)
                    if (Character.isHighSurrogate(pending[split - 1]) &&
                        Character.isLowSurrogate(pending[split])
                    ) split--
                    yield(TextLineFragment(lineIndex, pending.substring(0, split), continuation))
                    pending.delete(0, split)
                    continuation = true
                }
            }
        }
    }
    if (pending.isNotEmpty()) yield(TextLineFragment(lineIndex, pending.toString(), continuation))
}
