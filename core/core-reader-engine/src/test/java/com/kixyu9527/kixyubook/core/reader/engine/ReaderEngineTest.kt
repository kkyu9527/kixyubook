package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.Paragraph
import com.kixyu9527.kixyubook.core.common.model.ReaderInlineStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSemanticColor
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

    @Test fun txtParserAcceptsPunctuationInsideNumberedChapterTitles() = runBlocking {
        val source = """第三话 前情
正文三。
第四话 她太会了，太香了！
正文四。
第五话 后续
正文五。"""
        val file = folder.newFile("punctuated-chapters.txt").apply { writeText(source) }
        val chapters = mutableListOf<DocumentChapter>()

        TxtBookParser().readChapters(file, chapters::add)

        assertEquals(
            listOf("第三话 前情", "第四话 她太会了，太香了！", "第五话 后续"),
            chapters.map { it.title },
        )
        assertEquals("正文四。", chapters[1].paragraphs.single())
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
                "第一章 初见",
                "第二章 同行",
                "第一章 重逢",
                "第二章 再会",
                "第五章 终途",
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

    @Test fun readingProgressReachesOneOnlyWhenTheLastChapterIsComplete() {
        val positions = ReaderPositionManager()

        assertTrue(positions.bookFraction(9, 10, 99, 100, chapterComplete = false) < 1f)
        assertEquals(1f, positions.bookFraction(9, 10, 99, 100, chapterComplete = true), 0f)
        assertEquals(.5f, positions.bookFraction(4, 10, 0, 1, chapterComplete = true), 0f)
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
        assertEquals(listOf("真正的第一段正文。"), chapter.contentParagraphs().map { it.text })
        assertEquals(1, chapter.contentParagraphs().first().index)
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

    @Test fun chapterHeadingSeparatesOrdinalFromNameAndDropsLegacyVolumePrefix() {
        assertEquals(
            ReaderChapterHeading("第一章", "初见"),
            splitReaderChapterHeading("第二卷 云涌 · 第一章 初见"),
        )
        assertEquals(
            ReaderChapterHeading("第二话", "重逢"),
            splitReaderChapterHeading("第二话：重逢"),
        )
        assertEquals(
            ReaderChapterHeading("第十二章", "重逢"),
            splitReaderChapterHeading("正文 第 十二 章：重逢"),
        )
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

    @Test fun epubChapterOutlinesUseNavigationWithoutParsingBodies() {
        val epub = folder.newFile("navigation.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
            entry("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>目录测试</dc:title></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="text/opaque-1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="text/opaque-2.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""")
            entry("OPS/nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><nav><a href="text/opaque-1.xhtml#start">第一章 开始</a><a href="text/opaque-2.xhtml">第二章 继续</a></nav></body></html>""")
            // Invalid bodies prove that creating the directory only touches OPF/NAV data.
            entry("OPS/text/opaque-1.xhtml", "not xml")
            entry("OPS/text/opaque-2.xhtml", "not xml")
        }

        assertEquals(
            listOf(
                DocumentChapterOutline(0, "第一章 开始"),
                DocumentChapterOutline(1, "第二章 继续"),
            ),
            EpubBookParser().readChapterOutlines(epub),
        )
    }

    @Test fun epubParserNormalizesSemanticInlineStylesAndExternalCss() = runBlocking {
        val epub = folder.newFile("styled.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
            entry("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>样式书</dc:title></metadata><manifest><item id="c1" href="Text/c1.xhtml" media-type="application/xhtml+xml"/><item id="css" href="Styles/book.css" media-type="text/css"/></manifest><spine><itemref idref="c1"/></spine></package>""")
            entry(
                "OPS/Styles/book.css",
                """
                    @import "extra.css";
                    :root { --warning: rgb(255 170 180); }
                    .voice { color: #0055ee; font-weight: 700; }
                    .sky { color: lightblue; }
                    .ocean { color: hsl(220 90% 45%); }
                    .warning { color: var(--warning); }
                    .leaf { color: rgb(35, 160, 70); }
                    .note { background-color: #ffe36e; }
                    strong .regular { font-weight: normal; }
                """.trimIndent(),
            )
            entry("OPS/Styles/extra.css", ".imported { color: rebeccapurple; } .gone { display: none; }")
            entry(
                "OPS/Text/c1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><head><link rel="stylesheet" href="../Styles/book.css"/></head><body><h1>第一章</h1><p><span class="voice">深蓝粗体</span><span class="sky">浅蓝</span><span class="ocean">天空蓝</span><span class="warning">粉红</span><span class="leaf">绿色</span><span class="imported">紫色</span><span class="gone">隐藏</span><em>斜体</em><a href="#note">链接</a><span class="note">高亮</span><span style="text-decoration: line-through">删除</span><strong><span class="regular">常规</span></strong><br/>换行<ruby>字<rt>zi</rt><rp>(zi)</rp></ruby></p></body></html>""",
            )
        }

        val chapter = EpubBookParser().readChapter(epub, 0)!!
        val text = chapter.paragraphs.single()
        val spans = chapter.paragraphSpans.single()
        fun stylesFor(value: String) = text.indexOf(value).let { start ->
            spans.filter { it.start < start + value.length && it.end > start }.flatMap { it.styles }.toSet()
        }
        fun spanFor(value: String) = text.indexOf(value).let { start ->
            spans.first { it.start <= start && it.end >= start + value.length }
        }

        assertEquals("深蓝粗体浅蓝天空蓝粉红绿色紫色斜体链接高亮删除常规\n换行字zi", text)
        assertTrue(ReaderInlineStyle.BOLD in stylesFor("深蓝粗体"))
        assertEquals(ReaderSemanticColor.BLUE, spanFor("深蓝粗体").foreground)
        assertEquals(ReaderSemanticColor.BLUE, spanFor("浅蓝").foreground)
        assertEquals(ReaderSemanticColor.BLUE, spanFor("天空蓝").foreground)
        assertEquals(ReaderSemanticColor.RED, spanFor("粉红").foreground)
        assertEquals(ReaderSemanticColor.GREEN, spanFor("绿色").foreground)
        assertEquals(ReaderSemanticColor.PURPLE, spanFor("紫色").foreground)
        assertTrue(ReaderInlineStyle.ITALIC in stylesFor("斜体"))
        assertEquals(ReaderSemanticColor.ACCENT, spanFor("链接").foreground)
        assertTrue(ReaderInlineStyle.UNDERLINE in stylesFor("链接"))
        assertEquals(ReaderSemanticColor.YELLOW, spanFor("高亮").background)
        assertTrue(ReaderInlineStyle.STRIKETHROUGH in stylesFor("删除"))
        assertTrue(ReaderInlineStyle.BOLD !in stylesFor("常规"))
        assertTrue(ReaderInlineStyle.SUPERSCRIPT in stylesFor("zi"))
    }

    @Test fun epubParserKeepsImagesInReadingOrderAndResolvesRelativePaths() = runBlocking {
        val epub = folder.newFile("images.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/book.opf"/></rootfiles></container>""")
            entry("OEBPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>插图书</dc:title></metadata><manifest><item id="c1" href="Text/c1.xhtml" media-type="application/xhtml+xml"/><item id="art" href="Images/art.png" media-type="image/png"/></manifest><spine><itemref idref="c1"/></spine></package>""")
            entry("OEBPS/Text/c1.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>插图之前。</p><img src="../Images/art.png" alt="场景插画"/><p>插图之后。</p></body></html>""")
            val pngHeader = ByteArray(24).apply {
                this[0] = 0x89.toByte(); this[1] = 0x50; this[2] = 0x4E; this[3] = 0x47
                this[16] = 0; this[17] = 0; this[18] = 0x04; this[19] = 0xB0.toByte()
                this[20] = 0; this[21] = 0; this[22] = 0x03; this[23] = 0x20
            }
            zip.putNextEntry(ZipEntry("OEBPS/Images/art.png")); zip.write(pngHeader); zip.closeEntry()
        }

        val chapter = EpubBookParser().readChapter(epub, 0)!!

        assertEquals(listOf("插图之前。", "插图之后。"), chapter.paragraphs)
        assertEquals(1, chapter.images.single().contentIndex)
        assertEquals("OEBPS/Images/art.png", chapter.images.single().resourcePath)
        assertEquals("场景插画", chapter.images.single().altText)
        assertEquals(1200, chapter.images.single().intrinsicWidth)
        assertEquals(800, chapter.images.single().intrinsicHeight)
    }

    @Test fun epubParserNormalizesFallbackBlocksListsAndTables() = runBlocking {
        val epub = folder.newFile("structural-elements.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
            entry("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>结构测试</dc:title></metadata><manifest><item id="c1" href="content.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""")
            entry(
                "OPS/content.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><div><span>区块正文</span></div><div style="display:none">隐藏内容</div><ul><li>第一项</li><li>第二项</li></ul><hr/><table><tr><td>甲</td><td>乙</td></tr></table></body></html>""",
            )
        }

        val chapter = EpubBookParser().readChapter(epub, 0)!!
        assertEquals(listOf("区块正文", "• 第一项", "• 第二项", "· · ·", "甲", "乙"), chapter.paragraphs)
    }

    @Test fun epubImageLayoutUsesStableSizeClassesAndKeepsAspectRatio() {
        val wide = standardizedReaderImageLayout(320f, 1600, 800)
        val portrait = standardizedReaderImageLayout(320f, 800, 1600)
        val compact = standardizedReaderImageLayout(320f, 240, 180)

        assertEquals(ReaderImageSizeClass.WIDE, wide.sizeClass)
        assertEquals(2f, wide.widthDp / wide.heightDp, .01f)
        assertEquals(ReaderImageSizeClass.PORTRAIT, portrait.sizeClass)
        assertEquals(.5f, portrait.widthDp / portrait.heightDp, .01f)
        assertEquals(ReaderImageSizeClass.COMPACT, compact.sizeClass)
    }
}
