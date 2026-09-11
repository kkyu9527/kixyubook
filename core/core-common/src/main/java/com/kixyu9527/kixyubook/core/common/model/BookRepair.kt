package com.kixyu9527.kixyubook.core.common.model

enum class BookRepairMode { CACHE, SEARCH_INDEX, REPARSE }
data class BookRepairProgress(val completed: Int, val total: Int)
data class BookRepairOutcome(val chapters: Int, val originalPreserved: Boolean = false)

/** Never silently invalidate an annotation/correction by replacing its source paragraph. */
fun repairAnchorMatches(paragraphs: List<String>, paragraph: Int, start: Int, end: Int, quote: String): Boolean {
    val text = paragraphs.getOrNull(paragraph) ?: return false
    return start >= 0 && end > start && end <= text.length && text.substring(start, end) == quote
}
