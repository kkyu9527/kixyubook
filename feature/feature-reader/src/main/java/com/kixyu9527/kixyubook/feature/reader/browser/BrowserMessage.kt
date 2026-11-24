package com.kixyu9527.kixyubook.feature.reader.browser

import com.kixyu9527.kixyubook.core.reader.engine.ReaderChapter
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import org.json.JSONObject

internal sealed interface BrowserMessage {
    data object Ready : BrowserMessage
    data object ClearSelection : BrowserMessage
    data class Position(val paragraph: Int, val offset: Int, val end: Int, val complete: Boolean) : BrowserMessage
    data class Boundary(val delta: Int) : BrowserMessage
    data class Link(val href: String) : BrowserMessage
    data class Tap(val fraction: Float) : BrowserMessage
    data class Interaction(val active: Boolean) : BrowserMessage
    data class Selection(val paragraph: Int, val endParagraph: Int, val start: Int, val end: Int, val copyText: String,
        val x: Float, val y: Float, val width: Float, val height: Float) : BrowserMessage
}

/** A browser message is a bounded request, not authority to write arbitrary chapter locations. */
internal fun parseBrowserMessage(text: String?, token: String, chapter: ReaderChapter,
    navigationVersion: Int? = null, layoutVersion: Int? = null,
): BrowserMessage? = runCatching {
    if (text == null || text.length > 65_536) return null
    val value = JSONObject(text)
    if (value.optString("token") != token) return null
    if (navigationVersion != null && value.optInt("navigationVersion", -1) != navigationVersion) return null
    if (layoutVersion != null && value.optInt("layoutVersion", -1) != layoutVersion) return null
    fun coordinate(key: String): Float = value.getDouble(key).toFloat().also { require(it.isFinite() && kotlin.math.abs(it) <= 1_000_000) }
    when (value.getString("type")) {
        "ready" -> BrowserMessage.Ready
        "selection-clear" -> BrowserMessage.ClearSelection
        "interaction" -> BrowserMessage.Interaction(value.getBoolean("active"))
        "boundary" -> BrowserMessage.Boundary(value.getInt("delta").also { require(it == -1 || it == 1) })
        "tap" -> BrowserMessage.Tap(coordinate("fraction").also { require(it in 0f..1f) })
        "link" -> BrowserMessage.Link(value.getString("href").also { require(it.length <= 8192) })
        "position" -> {
            val p = value.getInt("p")
            val paragraph = chapter.paragraphs.firstOrNull { it.index == p && it.kind == ParagraphKind.TEXT }
                ?: chapter.paragraphs.firstOrNull { it.index == p } ?: return null
            val offset = value.getInt("c").also { require(it in 0..paragraph.text.length) }
            val end = value.getInt("end").also { require(it >= p && chapter.paragraphs.any { para -> para.index == it }) }
            BrowserMessage.Position(p, offset, end, value.optBoolean("complete"))
        }
        "selection" -> {
            val p = value.getInt("p")
            val paragraph = chapter.paragraphs.firstOrNull { it.index == p && it.kind == ParagraphKind.TEXT } ?: return null
            val endP = value.optInt("endP", p)
            val last = chapter.paragraphs.firstOrNull { it.index == endP && it.kind == ParagraphKind.TEXT } ?: return null
            val start = value.getInt("start")
            val end = value.getInt("end")
            require(endP >= p && start in 0..paragraph.text.length && end in 0..last.text.length && (endP > p || end > start))
            val copy = if (endP == p) paragraph.text.substring(start, end) else
                chapter.paragraphs.filter { it.index in p..endP && it.kind == ParagraphKind.TEXT }.joinToString("\n") {
                    when (it.index) { p -> it.text.substring(start); endP -> it.text.substring(0, end); else -> it.text }
                }
            require(copy.length <= 65_536)
            BrowserMessage.Selection(p, endP, start, end, copy, coordinate("x"), coordinate("y"),
                coordinate("w").also { require(it >= 0) }, coordinate("h").also { require(it >= 0) })
        }
        else -> null
    }
}.getOrNull()
