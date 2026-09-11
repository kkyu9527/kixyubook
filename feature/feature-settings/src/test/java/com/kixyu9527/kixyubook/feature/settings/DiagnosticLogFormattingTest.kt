package com.kixyu9527.kixyubook.feature.settings

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], qualifiers = "zh-rCN")
class DiagnosticLogFormattingTest {
    private fun parseDiagnosticEntry(rawLine: String): ReadableDiagnosticEntry =
        DiagnosticLogFormatter(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources,
        ).parseDiagnosticEntry(rawLine)
    private lateinit var originalTimeZone: TimeZone

    @Test
    fun diagnosticCodesAndFailureClassificationStayStableAcrossLanguages() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(
            "en" to "EPUB chapter parsing failed",
            "zh-Hans" to "EPUB 章节解析失败",
            "zh-Hant" to "EPUB 章節解析失敗",
            "ja" to "EPUB の章の解析失敗",
        ).forEach { (tag, expected) ->
            val config = android.content.res.Configuration(context.resources.configuration).apply {
                setLocales(android.os.LocaleList.forLanguageTags(tag))
            }
            val formatter = DiagnosticLogFormatter(context.createConfigurationContext(config).resources)
            val entry = formatter.parseDiagnosticEntry(
                "2026-08-08T15:06:04Z | EPUB_PARSE | chapter_parse_finished | outcome=io_error | chapter=42",
            )
            assertEquals(expected, entry.title)
            assertEquals("EPUB_PARSE", entry.categoryKey)
            assertTrue(entry.isFailure)
            assertTrue(entry.details.any { it.second == "42" })
        }
    }

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

    @Test
    fun diagnosticFilterCanShowOnlyFailuresWithinCategory() {
        val success = parseDiagnosticEntry(
            "2026-08-08T15:06:04Z | EPUB_PARSE | chapter_parse_finished | outcome=success",
        )
        val parseFailure = parseDiagnosticEntry(
            "2026-08-08T15:06:05Z | EPUB_PARSE | chapter_parse_finished | outcome=error",
        )
        val syncFailure = parseDiagnosticEntry(
            "2026-08-08T15:06:06Z | SYNC | full_sync_finished | outcome=error",
        )

        assertEquals(
            listOf(parseFailure),
            filterDiagnosticEntries(
                listOf(success, parseFailure, syncFailure),
                categoryKey = "EPUB_PARSE",
                onlyFailures = true,
            ),
        )
    }

    @Test
    fun cancelledSyncIsExplainedAsInterruptionAndExcludedFromFailures() {
        val currentEntry = parseDiagnosticEntry(
            "2026-08-09T12:44:43.586Z | SYNC | full_sync_finished | elapsedMs=8450 | " +
                "outcome=interrupted | stage=remote_snapshot",
        )
        assertEquals("云同步被系统中断", currentEntry.title)
        assertEquals(false, currentEntry.isFailure)
        assertTrue(currentEntry.details.contains("结果" to "被系统中断"))
        assertTrue(currentEntry.details.contains("中断或失败阶段" to "检查云端变更"))
    }

    @Test
    fun realSyncFailureShowsStableReadableDiagnosis() {
        val entry = parseDiagnosticEntry(
            "2026-08-09T12:44:43.586Z | SYNC | full_sync_finished | elapsedMs=8450 | " +
                "outcome=drive_http_error | stage=uploading | statusCode=503 | " +
                "reason=Google Drive 服务暂时不可用",
        )

        assertEquals("云同步失败", entry.title)
        assertEquals(true, entry.isFailure)
        assertTrue(entry.details.contains("结果" to "Google Drive 请求失败"))
        assertTrue(entry.details.contains("中断或失败阶段" to "上传本机更改"))
        assertTrue(entry.details.contains("HTTP 状态码" to "503"))
        assertTrue(entry.details.contains("错误原因" to "Google Drive 服务暂时不可用"))
    }

    @Test
    fun partialImportIsAnActionableFailureWithReadableCause() {
        val entry = parseDiagnosticEntry(
            "2026-08-09T12:44:43.586Z | IMPORT | documents_registered | elapsedMs=8450 | " +
                "outcome=partial | run=abc | imported=2 | duplicates=1 | failures=1 | " +
                "failureTypes=invalid_archive,io_error | firstFailureReason=压缩文件结构损坏或不完整",
        )

        assertEquals("书籍导入部分完成", entry.title)
        assertEquals(true, entry.isFailure)
        assertTrue(entry.details.contains("失败类型" to "压缩文件损坏、文件读写异常"))
        assertTrue(entry.details.contains("首个失败原因" to "压缩文件结构损坏或不完整"))
    }

    @Test
    fun locationReadyEntryIsNotAFailureAndExplainsItsSource() {
        val entry = parseDiagnosticEntry(
            "2026-09-11T08:38:11.194Z | READER | location_ready | elapsedMs=374 | " +
                "outcome=ready | source=DOCUMENT_LINK",
        )

        assertEquals("阅读定位完成", entry.title)
        assertEquals(false, entry.isFailure)
        assertTrue(entry.details.contains("结果" to "已就绪"))
        assertTrue(entry.details.contains("内容来源" to "文内链接"))
    }

    @Test
    fun cacheStatisticsEntryIsRecognizedAndNotAFailure() {
        val entry = parseDiagnosticEntry(
            "2026-09-11T08:38:11.194Z | CACHE | cache_stats | cache=epub-package | hits=3 | misses=1 | " +
                "evictions=0 | oversized=0 | parses=2 | repeatedRecentParses=1",
        )

        assertEquals("缓存", entry.category)
        assertEquals("缓存统计", entry.title)
        assertEquals(false, entry.isFailure)
        assertTrue(entry.details.contains("缓存名称" to "epub-package"))
        assertTrue(entry.details.contains("命中" to "3"))
        assertTrue(entry.details.contains("近期重复解析" to "1"))
    }

    @Test
    fun backwardPageTurnKeepsDirectionAndSource() {
        val entry = parseDiagnosticEntry(
            "2026-09-11T08:38:11.194Z | READER | page_turn | elapsedMs=12 | outcome=success | " +
                "direction=backward | source=pager | fromChapter=240 | chapter=239",
        )

        assertEquals("跨章翻页完成", entry.title)
        assertEquals(false, entry.isFailure)
        assertTrue(entry.details.contains("结果" to "成功"))
        assertTrue(entry.details.contains("翻页方向" to "向前回退（上一页）"))
        assertTrue(entry.details.contains("内容来源" to "滑动翻页"))
        assertTrue(entry.details.contains("起始章节" to "240"))
        assertTrue(entry.details.contains("章节索引" to "239"))
    }

    @Test
    fun chapterLoadFailureShowsSourceAndReason() {
        val entry = parseDiagnosticEntry(
            "2026-08-09T12:44:43.586Z | READER | chapter_loaded | elapsedMs=845 | " +
                "outcome=local_data_error | book=12345678 | chapter=77 | priority=USER | " +
                "source=database | reason=读取或保存本地数据失败",
        )

        assertEquals("章节内容加载失败", entry.title)
        assertEquals(true, entry.isFailure)
        assertTrue(entry.details.contains("内容来源" to "本地数据库"))
        assertTrue(entry.details.contains("错误原因" to "读取或保存本地数据失败"))
    }
}
