package com.kixyu9527.kixyubook.core.reader.engine

import org.w3c.dom.Element

/**
 * Publisher CSS is converted into theme-independent semantics, not executed as a browser layout.
 * Keep unconditional screen rules; do not accidentally apply nested print/dark/viewport overrides
 * to every theme. User typography and semantic colors remain owned by the native renderer.
 */
internal fun baseReaderCss(source: String, nesting: Int = 0): String = buildString {
    if (nesting > 16) return@buildString
    var start = 0
    var index = 0
    var quote: Char? = null
    while (index < source.length) {
        val c = source[index]
        if (quote != null) {
            if (c == '\\') index++ else if (c == quote) quote = null
        } else if (c == '\'' || c == '"') quote = c
        else if (c == '{') {
            var depth = 1
            var end = index + 1
            var innerQuote: Char? = null
            while (end < source.length && depth > 0) {
                val inner = source[end]
                if (innerQuote != null) {
                    if (inner == '\\') end++ else if (inner == innerQuote) innerQuote = null
                } else when (inner) {
                    '\'', '"' -> innerQuote = inner
                    '{' -> depth++
                    '}' -> depth--
                }
                end++
            }
            if (depth != 0) break
            val selector = source.substring(start, index).trim()
            when {
                !selector.startsWith('@') -> append(source.substring(start, end))
                selector.lowercase() in setOf("@media all", "@media screen") ||
                    selector.startsWith("@layer", true) -> append(baseReaderCss(source.substring(index + 1, end - 1), nesting + 1))
            }
            index = end
            start = end
            continue
        }
        index++
    }
}

/** Only small, transparent, truly monochrome assets are safe to tint as theme-colored ink. */
internal fun isThemeInkImage(width: Int, height: Int, pixels: IntArray): Boolean {
    if (width !in 1..256 || height !in 1..256 || pixels.isEmpty()) return false
    var transparent = 0
    var ink = 0
    var dark = 0
    var light = 0
    pixels.forEach { pixel ->
        val alpha = pixel ushr 24
        if (alpha < 16) transparent++ else {
            val r = pixel ushr 16 and 255
            val g = pixel ushr 8 and 255
            val b = pixel and 255
            if (maxOf(r, g, b) - minOf(r, g, b) > 12) return false
            when {
                maxOf(r, g, b) < 96 -> dark++
                minOf(r, g, b) > 192 -> light++
                else -> return false
            }
            ink++
        }
    }
    // Do not recolor opaque diagrams, photos, colored illustrations or black-and-white scans.
    return transparent > 0 && transparent * 4 >= pixels.size && ink > 0 && (dark == 0 || light == 0)
}

/** Attribute values may contain spaces; child and sibling combinators are not descendants. */
internal fun Element.matchesReaderCssSelector(selector: String): Boolean {
    val parts = mutableListOf<String>()
    val relations = mutableListOf<Char>()
    val token = StringBuilder()
    var relation = ' '
    var brackets = 0
    var quote: Char? = null
    fun flush() {
        if (token.isEmpty()) return
        if (parts.isNotEmpty()) relations += relation
        parts += token.toString()
        token.setLength(0)
        relation = ' '
    }
    selector.forEach { c ->
        when {
            quote != null -> { token.append(c); if (c == quote) quote = null }
            c == '\'' || c == '"' -> { quote = c; token.append(c) }
            c == '[' -> { brackets++; token.append(c) }
            c == ']' -> { brackets--; token.append(c) }
            brackets == 0 && c in ">+~" -> { flush(); relation = c }
            brackets == 0 && c.isWhitespace() -> flush()
            else -> token.append(c)
        }
    }
    flush()
    if (parts.isEmpty() || parts.size > 32 || brackets != 0 || quote != null) return false
    val cache = java.util.IdentityHashMap<Element, MutableMap<Int, Boolean>>()
    fun previous(element: Element): Element? = generateSequence(element.previousSibling) { it.previousSibling }
        .filterIsInstance<Element>().firstOrNull()
    fun matches(element: Element?, index: Int): Boolean {
        if (element == null) return false
        val states = cache.getOrPut(element) { mutableMapOf() }
        states[index]?.let { return it }
        val result = if (!element.matchesCssCompound(parts[index])) false
        else if (index == 0) true
        else when (relations[index - 1]) {
            '>' -> matches(element.parentNode as? Element, index - 1)
            '+' -> matches(previous(element), index - 1)
            '~' -> generateSequence(previous(element), ::previous).any { matches(it, index - 1) }
            else -> generateSequence(element.parentNode as? Element) { it.parentNode as? Element }
                .any { matches(it, index - 1) }
        }
        states[index] = result
        return result
    }
    return matches(this, parts.lastIndex)
}
