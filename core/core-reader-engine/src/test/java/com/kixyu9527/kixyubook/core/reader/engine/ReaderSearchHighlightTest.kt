package com.kixyu9527.kixyubook.core.reader.engine

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSearchHighlightTest {
    @Test
    fun highlighted_preservesEntireParagraph() {
        val paragraph = "2019年5月20日，星期一。正文继续。"

        val highlighted = paragraph.highlighted("2019", Color.Blue)

        assertEquals(paragraph, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun highlighted_preservesParagraphWithoutMatch() {
        val paragraph = "这一段没有匹配内容。"

        assertEquals(paragraph, paragraph.highlighted("2019", Color.Blue).text)
    }
}
