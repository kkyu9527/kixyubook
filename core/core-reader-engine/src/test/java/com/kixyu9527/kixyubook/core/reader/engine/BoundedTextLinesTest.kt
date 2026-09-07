package com.kixyu9527.kixyubook.core.reader.engine

import java.io.Reader
import org.junit.Assert.*
import org.junit.Test

class BoundedTextLinesTest {
    @Test fun mixedLineEndingsAndEmptyLinesKeepSourceLineNumbers() {
        val fragments = "甲\r\n乙\r\n\r丁\n戊".reader().boundedLines(2).toList()
        assertEquals(listOf("甲", "乙", "", "丁", "戊"), fragments.map { it.text })
        assertEquals((0..4).toList(), fragments.map { it.lineIndex })
        assertTrue(fragments.none { it.continuation })
    }

    @Test fun hugeUnbrokenLineIsLazyAndNeverSplitsUnicodePair() {
        var consumed = 0
        val source = object : Reader() {
            override fun close() = Unit
            override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                for (index in 0 until len) cbuf[off + index] = if ((consumed + index) % 2 == 0) '\uD83D' else '\uDE00'
                consumed += len
                return len // Conceptually unbounded: taking one fragment must still finish.
            }
        }
        val fragment = source.boundedLines(65).first()
        assertTrue(consumed <= 130)
        assertEquals(64, fragment.text.length)
        assertFalse(Character.isHighSurrogate(fragment.text.last()))
    }

    @Test fun boundedInputPreservesLegacyParagraphSplitsAndOffsets() {
        val text = ("正文包含标点，和文字。".repeat(2000) + "结尾")
        val expected = text.readableChunks(64).toList()
        val actual = text.reader().boundedLines(64).map { it.text.trim() }.filter { it.isNotEmpty() }.toList()
        assertEquals(expected, actual)
        assertEquals(text, actual.joinToString(""))
    }
}
