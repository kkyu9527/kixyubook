package com.kixyu9527.kixyubook.feature.settings

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticLogFormattingTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun paginationEntryUsesDeviceTimeAndReadableLabels() {
        val entry = parseDiagnosticEntry(
            "2026-08-08T15:06:04.623361Z | PAGINATION | measure | elapsedMs=16 | " +
                "outcome=success | chapter=29950 | paragraphs=57 | pages=10 | prefetch=false",
        )

        assertEquals("2026-08-08 23:06:04.623", entry.time)
        assertEquals("页面排版", entry.category)
        assertEquals("章节分页完成", entry.title)
        assertTrue(entry.details.contains("结果" to "成功"))
        assertTrue(entry.details.contains("耗时" to "16 毫秒"))
        assertTrue(entry.details.contains("生成页数" to "10"))
        assertTrue(entry.details.contains("执行方式" to "当前阅读"))
    }

    @Test
    fun readerEntryExplainsCacheAndPrefetch() {
        val entry = parseDiagnosticEntry(
            "2026-08-08T15:06:04.647810Z | READER | chapter_loaded | elapsedMs=2 | " +
                "outcome=success | format=EPUB | chapter=14 | priority=PREFETCH | " +
                "source=epub_disk_cache | paragraphs=47",
        )

        assertEquals("阅读", entry.category)
        assertEquals("章节内容加载完成", entry.title)
        assertTrue(entry.details.contains("加载类型" to "后台预加载"))
        assertTrue(entry.details.contains("内容来源" to "EPUB 磁盘缓存"))
        assertTrue(entry.details.contains("段落数" to "47"))
    }
}
