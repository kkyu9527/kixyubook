package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.Paragraph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReaderEngineTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun txtParserKeepsChapterBoundaries() = runBlocking {
        val file = folder.newFile("book.txt").apply { writeText("序章内容\n第一章 开始\n第一段\n第二章 继续\n第二段") }
        val chapters = mutableListOf<DocumentChapter>()
        TxtBookParser().readChapters(file, chapters::add)
        assertEquals(listOf("正文", "第一章 开始", "第二章 继续"), chapters.map { it.title })
    }

    @Test fun txtParserDetectsGb18030StripsMetadataAndRecognizesHua() = runBlocking {
        val source = """书名：安静的书
作者：测试作者
内容简介：这段文字只属于简介

第一话 初见
第一段正文。
第二话 重逢
第二段正文。"""
        val file = folder.newFile("legacy.txt").apply { writeBytes(source.toByteArray(charset("GB18030"))) }
        val parser = TxtBookParser()
        val metadata = parser.readMetadata(file, file.name)
        val chapters = mutableListOf<DocumentChapter>()
        parser.readChapters(file, chapters::add)

        assertEquals("安静的书", metadata.title)
        assertEquals("测试作者", metadata.author)
        assertEquals("这段文字只属于简介", metadata.description)
        assertEquals(listOf("第一话 初见", "第二话 重逢"), chapters.map { it.title })
        assertEquals(listOf("第一段正文。", "第二段正文。"), chapters.flatMap { it.paragraphs })
    }

    @Test fun txtParserDetectsUtf16BomAndDoesNotTreatSentencesAsChapters() = runBlocking {
        val source = "第一话 开始\r\n这是第一话发生的事情。\r\n第二话 继续\r\n正文。"
        val encoded = source.toByteArray(Charsets.UTF_16LE)
        val file = folder.newFile("utf16.txt").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + encoded) }
        val chapters = mutableListOf<DocumentChapter>()
        TxtBookParser().readChapters(file, chapters::add)

        assertEquals(listOf("第一话 开始", "第二话 继续"), chapters.map { it.title })
        assertEquals("这是第一话发生的事情。", chapters.first().paragraphs.single())
    }

    @Test fun txtParserKeepsUtf8WhenDetectionSampleEndsMidCharacter() = runBlocking {
        val header = "书名：超长 UTF8 小说\n作者：测试作者\n第一章 开始\n"
        val sampleSize = 128 * 1024
        val paddingLength = (1 - (sampleSize - header.toByteArray().size) % 3 + 3) % 3
        val body = "a".repeat(paddingLength) + "中".repeat(50_000)
        val file = folder.newFile("large-utf8.txt").apply { writeText(header + body, Charsets.UTF_8) }
        val parser = TxtBookParser()
        val chapters = mutableListOf<DocumentChapter>()

        val metadata = parser.readMetadata(file, file.name)
        parser.readChapters(file, chapters::add)

        assertEquals("超长 UTF8 小说", metadata.title)
        assertEquals("测试作者", metadata.author)
        assertTrue(chapters.single().paragraphs.single().contains("中中中"))
        assertFalse(chapters.single().paragraphs.single().contains("锟斤拷"))
    }

    @Test fun txtParserScopesRestartedAndContinuousChapterNumbersByVolume() = runBlocking {
        val source = """第一卷 风起
第一章 初见
正文一。
第二章 同行
正文二。
第二卷 云涌
第一章 重逢
正文三。
第二章 再会
正文四。
第三卷 星落
第五章 终途
正文五。"""
        val file = folder.newFile("volumes.txt").apply { writeText(source) }
        val chapters = mutableListOf<DocumentChapter>()
        TxtBookParser().readChapters(file, chapters::add)

        assertEquals(
            listOf(
                "第一卷 风起 · 第一章 初见",
                "第一卷 风起 · 第二章 同行",
                "第二卷 云涌 · 第一章 重逢",
                "第二卷 云涌 · 第二章 再会",
                "第三卷 星落 · 第五章 终途",
            ),
            chapters.map { it.title },
        )
    }

    @Test fun txtParserDetectsBig5AndTraditionalMetadata() = runBlocking {
        val source = """書名：安靜的書
作者：測試作者
內容簡介：這是內容介紹

第一章 開始
我們在這個安靜的午後開始閱讀。"""
        val file = folder.newFile("traditional.txt").apply { writeBytes(source.toByteArray(charset("Big5"))) }
        val parser = TxtBookParser()
        val metadata = parser.readMetadata(file, file.name)
        val chapters = mutableListOf<DocumentChapter>()
        parser.readChapters(file, chapters::add)

        assertEquals("安靜的書", metadata.title)
        assertEquals("測試作者", metadata.author)
        assertEquals("這是內容介紹", metadata.description)
        assertEquals(listOf("第一章 開始"), chapters.map { it.title })
        assertEquals(listOf("我們在這個安靜的午後開始閱讀。"), chapters.single().paragraphs)
    }

    @Test fun paginationCreatesOpeningPageAndStablePositions() {
        val chapter = ReaderChapter(1, "uuid", "第一章", 0, List(20) { Paragraph(it.toLong(), 1, it, "这是一段用于分页的正文。".repeat(8)) })
        val pages = ReaderLayoutEngine().paginate(chapter, ReaderLayoutSpec(360f, 720f, 19f, 1.7f, .01f, 24f))
        assertTrue(pages.size > 1)
        assertTrue(pages.first().isChapterOpening)
        assertEquals(0, ReaderPositionManager().pageFor(pages, 0))
        assertTrue(ReaderPositionManager().pageFor(pages, 15) > 0)
    }

    @Test fun paginationUsesRemainingSpaceBeforeSplittingLongParagraph() {
        val short = Paragraph(1, 1, 0, "短段落。")
        val long = Paragraph(2, 1, 1, "这是用于验证剩余页面空间的一段长正文。".repeat(80))
        val chapter = ReaderChapter(1, "uuid", "第一章", 0, listOf(short, long))
        val pages = ReaderLayoutEngine().paginate(chapter, ReaderLayoutSpec(360f, 720f, 19f, 1.7f, .01f, 24f))

        assertTrue(pages.size > 1)
        assertTrue(pages.first().blocks.any { it.paragraphIndex == long.index })
    }

    @Test fun duplicateOpeningHeadingIsNotRenderedAsBodyText() {
        val chapter = ReaderChapter(
            1,
            "uuid",
            "第一章 开始",
            0,
            listOf(
                Paragraph(1, 1, 0, "第一章 开始"),
                Paragraph(2, 1, 1, "真正的第一段正文。"),
            ),
        )
        val pages = ReaderLayoutEngine().paginate(chapter, ReaderLayoutSpec(360f, 720f, 19f, 1.7f, .01f, 24f))

        assertEquals(listOf("真正的第一段正文。"), chapter.contentParagraphs().map { it.text })
        assertEquals(listOf("真正的第一段正文。"), pages.flatMap { page -> page.blocks.map { it.fullText } }.distinct())
        assertEquals(1, pages.first().startParagraph)
    }

    @Test fun volumeScopedOpeningHeadingIsNotRenderedAsBodyText() {
        val chapter = ReaderChapter(
            1,
            "uuid",
            "第二卷 云涌 · 第一章 重逢",
            0,
            listOf(
                Paragraph(1, 1, 0, "第一章 重逢"),
                Paragraph(2, 1, 1, "正文。"),
            ),
        )

        assertEquals(listOf("正文。"), chapter.contentParagraphs().map { it.text })
    }

    @Test fun epubParserReadsMetadataAndSpineContent() = runBlocking {
        val epub = folder.newFile("sample.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            entry("OEBPS/content.opf", """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier>urn:uuid:123e4567-e89b-12d3-a456-426614174000</dc:identifier><dc:title>测试书</dc:title><dc:creator>测试作者</dc:creator><dc:description>简介</dc:description></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""")
            entry("OEBPS/c1.xhtml", """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>第一段正文。</p><p>第二段正文。</p></body></html>""")
        }
        val parser = EpubBookParser()
        val metadata = parser.readMetadata(epub, epub.name)
        val chapters = mutableListOf<DocumentChapter>()
        parser.readChapters(epub, chapters::add)
        assertEquals("123e4567-e89b-12d3-a456-426614174000", metadata.identityHint)
        assertEquals("测试书", metadata.title)
        assertEquals("测试作者", metadata.author)
        assertEquals(listOf("第一段正文。", "第二段正文。"), chapters.single().paragraphs)
    }

    @Test fun epubWithoutHeadingKeepsItsFirstParagraph() = runBlocking {
        val epub = folder.newFile("no-heading.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
            entry("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>无标题章节</dc:title></metadata><manifest><item id="c1" href="content.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""")
            entry("OPS/content.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>正文第一段。</p><p>正文第二段。</p></body></html>""")
        }
        val chapters = mutableListOf<DocumentChapter>()
        EpubBookParser().readChapters(epub, chapters::add)

        assertEquals(listOf("正文第一段。", "正文第二段。"), chapters.single().paragraphs)
    }
}
