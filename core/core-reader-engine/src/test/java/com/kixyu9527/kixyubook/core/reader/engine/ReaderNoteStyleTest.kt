package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.kixyu9527.kixyubook.core.common.model.ReaderInlineStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderNoteStyleTest {
    @Test
    fun noteCombinesStrongerHighlightWithUnderlineMarker() {
        val text = readerAnnotatedText(
            text = "annotated text",
            spans = listOf(
                ReaderTextSpan(
                    start = 0,
                    end = 9,
                    styles = setOf(ReaderInlineStyle.HIGHLIGHT, ReaderInlineStyle.NOTE),
                ),
            ),
            accentColor = Color.Blue,
            backgroundColor = Color.White,
        )

        val style = text.spanStyles.single().item
        assertEquals(TextDecoration.Underline, style.textDecoration)
        assertTrue(style.background.alpha > .25f)
    }
}
