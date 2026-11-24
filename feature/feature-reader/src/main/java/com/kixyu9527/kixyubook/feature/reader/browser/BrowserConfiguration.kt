package com.kixyu9527.kixyubook.feature.reader.browser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.reader.engine.ReaderChapter
import com.kixyu9527.kixyubook.core.reader.engine.ReaderRenderPalette
import com.kixyu9527.kixyubook.core.reader.engine.ReaderImageThemePolicy
import com.kixyu9527.kixyubook.feature.reader.ReaderContentState
import com.kixyu9527.kixyubook.feature.reader.ReaderLocation
import com.kixyu9527.kixyubook.core.reader.engine.epub.browser.EpubBrowserDocument
import org.json.JSONArray
import org.json.JSONObject

internal fun browserOriginalText(corrected: String, corrections: List<TextCorrection>): String {
    var delta = 0
    val positions = corrections.sortedBy { it.startOffset }.map {
        val offset = it.startOffset + delta
        delta += it.replacementText.length - (it.endOffset - it.startOffset)
        it to offset
    }
    var text = corrected
    for ((value, start) in positions.asReversed()) {
        val end = start + value.replacementText.length
        if (start < 0 || end > text.length || text.substring(start, end) != value.replacementText) return corrected
        text = text.replaceRange(start, end, value.exactText)
    }
    return text
}

internal fun browserConfiguration(state: ReaderContentState, chapter: ReaderChapter,
    document: EpubBrowserDocument, palette: ReaderRenderPalette, token: String, active: Boolean,
    anchor: ReaderLocation?, fontScale: Float,
): String {
    fun Color.css() = "#%06x".format(toArgb() and 0xffffff)
    val settings = state.settings
    val corrections = state.browserCorrections.filter { it.chapterIndex == chapter.index && it.status == TextCorrectionStatus.ACTIVE }
    val paragraphs = JSONArray()
    chapter.paragraphs.filter { it.kind == ParagraphKind.TEXT }.forEach { p ->
        paragraphs.put(JSONObject().put("index", p.index).put("text", p.text)
            .put("sourceText", browserOriginalText(p.text, corrections.filter { it.paragraphIndex == p.index })))
    }
    val annotations = JSONArray()
    state.annotations.filter { it.chapterIndex == chapter.index && it.sourceContentHash == state.book?.contentHash }.forEach {
        annotations.put(JSONObject().put("p", it.paragraphIndex).put("start", it.startOffset).put("end", it.endOffset)
            .put("text", it.exactText).put("kind", when {
                it.note.isNotBlank() -> "note"
                it.style == ReaderAnnotationStyle.UNDERLINE -> "underline"
                else -> "highlight"
            }))
    }
    val p = if (active) anchor?.paragraphIndex ?: state.restorePosition else 0
    val c = if (active) anchor?.charOffset ?: state.restoreCharOffset else 0
    return JSONObject().put("token", token).put("active", active).put("fixed", state.browserPlan?.fixedLayout == true)
        .put("paged", settings.pageMode == PageMode.PAGED).put("animate", true)
        .put("dark", palette.background.luminance() < .5f).put("background", palette.background.css())
        .put("foreground", palette.body.css()).put("accent", palette.accent.css())
        .put("margin", settings.margin).put("fontSize", settings.fontSize * fontScale).put("lineHeight", settings.lineHeight)
        .put("letterSpacing", settings.letterSpacing).put("navigationVersion", state.navigationVersion)
        .put("layoutVersion", state.layoutVersion)
        .put("imageTonePolicy", JSONObject().apply {
            put("MIN_TRANSPARENCY", ReaderImageThemePolicy.MIN_TRANSPARENCY)
            put("NEUTRAL_TOLERANCE", ReaderImageThemePolicy.NEUTRAL_TOLERANCE)
            put("SINGLE_INK_FRACTION", ReaderImageThemePolicy.SINGLE_INK_FRACTION)
            put("MAX_INK_SPREAD", ReaderImageThemePolicy.MAX_INK_SPREAD)
            put("SIGNIFICANT_INK_FRACTION", ReaderImageThemePolicy.SIGNIFICANT_INK_FRACTION)
            put("MIN_CONTRAST", ReaderImageThemePolicy.MIN_CONTRAST)
            put("TARGET_INK_CONTRAST", ReaderImageThemePolicy.TARGET_INK_CONTRAST)
            put("SOFT_INK_LUMINANCE", ReaderImageThemePolicy.SOFT_INK_LUMINANCE)
        })
        .put("font", state.fontPath != null).put("fontKey", state.fontPath?.hashCode() ?: 0)
        .put("textVersion", System.identityHashCode(chapter)).put("viewportWidth", document.viewportWidth)
        .put("viewportHeight", document.viewportHeight).put("restore", JSONObject().put("p", p).put("c", c))
        .put("paragraphs", paragraphs).put("annotations", annotations).put("search", state.searchQuery).toString()
}
