package com.kixyu9527.kixyubook.core.reader.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubFallbackTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun oversizedPackageAndChapterFallBackWithoutRejectingTheBook() = runBlocking {
        val epub = folder.newFile("oversized.epub")
        val oversizedDescription = "d".repeat(MAX_EPUB_XML_BYTES + 1)
        val oversizedBody = buildString(MAX_EPUB_XHTML_BYTES + 1024) {
            append("流式正文开始。")
            while (length <= MAX_EPUB_XHTML_BYTES + 512) append('a')
            append("流式正文结束。")
        }
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>超限兼容测试</dc:title><dc:description>$oversizedDescription</dc:description></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>$oversizedBody</p></body></html>""",
            )
        }

        val parser = EpubBookParser()
        val metadata = parser.readMetadata(epub, epub.name)
        val chapter = parser.readChapter(epub, 0)!!

        assertEquals("超限兼容测试", metadata.title)
        assertEquals("第一章", chapter.title)
        assertTrue(chapter.paragraphs.size > 1)
        assertTrue(chapter.paragraphs.first().startsWith("流式正文开始。"))
        assertTrue(chapter.paragraphs.last().endsWith("流式正文结束。"))
    }

    @Test fun domComplexityLimitFallsBackToStreamingContent() = runBlocking {
        val epub = folder.newFile("complex.epub")
        val paragraphs = buildString {
            repeat(MAX_CHAPTER_DOM_BLOCKS_FOR_TEST + 1) { index ->
                append("<p>第").append(index).append("段</p>")
            }
        }
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>复杂结构测试</dc:title></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>正文</h1>$paragraphs</body></html>""",
            )
        }

        val chapter = EpubBookParser().readChapter(epub, 0)!!

        assertEquals(MAX_CHAPTER_DOM_BLOCKS_FOR_TEST + 1, chapter.paragraphs.size)
        assertEquals("第0段", chapter.paragraphs.first())
        assertEquals("第${MAX_CHAPTER_DOM_BLOCKS_FOR_TEST}段", chapter.paragraphs.last())
    }

    @Test fun doctypeAndLenientMarkupStillParse() = runBlocking {
        val epub = folder.newFile("doctype.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.textEntry("mimetype", "application/epub+zip")
            zip.textEntry(
                "META-INF/container.xml",
                """<?xml version="1.0"?><!DOCTYPE container><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            )
            zip.textEntry(
                "OPS/book.opf",
                """<?xml version="1.0"?><!DOCTYPE package><package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>DOCTYPE 测试</dc:title></metadata><manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.textEntry(
                "OPS/c1.xhtml",
                """<?xml version="1.0"?><!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>DOCTYPE 正文。</p></body></html>""",
            )
        }

        val parser = EpubBookParser()
        assertEquals("DOCTYPE 测试", parser.readMetadata(epub, epub.name).title)
        val chapter = parser.readChapter(epub, 0)!!
        assertEquals("第一章", chapter.title)
        assertTrue(chapter.paragraphs.contains("DOCTYPE 正文。"))
    }
}

private fun ZipOutputStream.textEntry(path: String, value: String) {
    putNextEntry(ZipEntry(path))
    write(value.toByteArray())
    closeEntry()
}

private const val MAX_CHAPTER_DOM_BLOCKS_FOR_TEST = 50_000
