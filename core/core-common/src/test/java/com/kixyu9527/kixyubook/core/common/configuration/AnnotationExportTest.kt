package com.kixyu9527.kixyubook.core.common.configuration

import com.kixyu9527.kixyubook.core.common.model.*
import org.junit.Assert.*
import org.junit.Test

class AnnotationExportTest {
    private val entry = ReaderAnnotation("id", "book", "hash", "chapter", 0, 2, 0, 4,
        "原文\n<script>alert(1)</script>", ReaderAnnotationStyle.HIGHLIGHT, "my **note**", 1, 1)

    @Test fun markdownEscapesActiveMarkupAndKeepsAllLines() {
        val text = buildString { writeAnnotationExport("书名", "作者", mapOf(0 to "第一章"), listOf(entry), AnnotationExportFormat.MARKDOWN) }
        assertTrue(text.contains("# 书名")); assertTrue(text.contains("## 第一章"))
        assertTrue(text.contains("> 原文\n> &lt;script&gt;"))
        assertTrue(text.contains("my \\*\\*note\\*\\*")); assertFalse(text.contains("<script>"))
    }
    @Test fun plainTextPreservesOriginalQuoteAndNote() {
        val text = buildString { writeAnnotationExport("Book", "", emptyMap(), listOf(entry), AnnotationExportFormat.PLAIN_TEXT) }
        assertTrue(text.contains(entry.exactText)); assertTrue(text.contains(entry.note))
        assertFalse(text.startsWith("#"))
    }
}
