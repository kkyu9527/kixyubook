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
        assertEquals("PAGINATION", entry.categoryKey)
        assertEquals("页面排版", entry.category)
        assertEquals("章节分页完成", entry.title)
        assertEquals(false, entry.isFailure)
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

    @Test
    fun backgroundIndexEntryKeepsBookContextAndExplainsPreemption() {
        val entry = parseDiagnosticEntry(
            "2026-08-08T15:06:04Z | EPUB_PARSE | background_index_finished | elapsedMs=12000 | " +
                "outcome=success | book=123e4567 | requested=120 | indexed=120 | preempted=3",
        )

        assertEquals("EPUB_PARSE", entry.categoryKey)
        assertEquals("书籍后台索引完成", entry.title)
        assertTrue(entry.details.contains("书籍标识" to "123e4567"))
        assertTrue(entry.details.contains("完成索引章节" to "120"))
        assertTrue(entry.details.contains("向前台让路次数" to "3"))
    }

    @Test
    fun directoryUpgradeEntryHasReadableDescriptionAndCounts() {
        val entry = parseDiagnosticEntry(
            "2026-08-09T03:21:15.807Z | EPUB_PARSE | directory_upgrade_finished | " +
                "outcome=success | inserted=74 | updated=12 | failures=0",
        )

        assertEquals("EPUB 目录更新完成", entry.title)
        assertEquals("已根据新版解析规则自动补齐并更新已导入书籍的目录信息。", entry.description)
        assertTrue(entry.details.contains("新增目录项" to "74"))
        assertTrue(entry.details.contains("更新目录项" to "12"))
        assertTrue(entry.details.contains("处理失败" to "0"))
    }

    @Test
    fun backgroundIndexFailureIsMarkedAndExplainsConstraint() {
        val entry = parseDiagnosticEntry(
            "2026-08-08T15:06:04Z | EPUB_PARSE | background_index_finished | elapsedMs=150700 | " +
                "outcome=SQLiteConstraintException | book=b5f236d6 | chapter=134 | requested=3058 | " +
                "indexed=134 | preempted=53 | reason=UNIQUE constraint failed: paragraphs.chapterId, paragraphs.paragraphIndex",
        )

        assertTrue(entry.isFailure)
        assertEquals("书籍后台索引失败", entry.title)
        assertTrue(entry.details.contains("章节索引" to "134"))
        assertTrue(
            entry.details.contains(
                "错误原因" to "UNIQUE constraint failed: paragraphs.chapterId, paragraphs.paragraphIndex",
            ),
        )
    }
}
