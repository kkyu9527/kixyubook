package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.EpubLinkResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubNavigationIdentityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun unlistedSpineTextRemainsReadableAndAnchorsShareStableParagraphIndices() = runBlocking {
        val epub = folder.newFile("navigation.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun text(path: String, value: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry()
            }
            text("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
            text("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>测试</dc:title></metadata><manifest><item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/><item id="b" href="b.xhtml" media-type="application/xhtml+xml"/><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="a"/><itemref idref="b"/><itemref idref="c"/></spine></package>""")
            text("OPS/nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="page-list"><a href="a.xhtml#page">印刷页</a></nav><nav epub:type="toc"><ol><li><a href="a.xhtml#first">第一节</a></li><li><a href="a.xhtml#second">第二节</a></li><li><a href="c.xhtml">结尾</a></li></ol></nav></body></html>""")
            text("OPS/a.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>正文</h1><p id="first">第一段，书签仍然指向这里。</p><section id="second"><h2>第二节</h2><p>第二节正文，搜索与批注仍然指向这里。</p></section></body></html>""")
            text("OPS/b.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>目录未列出的正文也不能丢失。</p></body></html>""")
            text("OPS/c.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>结尾正文。</p></body></html>""")
        }
        val parser = EpubBookParser()
        val before = parser.readChapter(epub, 0)!!
        val navigation = parser.readNavigation(epub)
        assertEquals(listOf(0, 1, 2), parser.readChapterOutlines(epub).map { it.sourceIndex })
        assertEquals(listOf("OPS/a.xhtml#first", "OPS/a.xhtml#second", "OPS/c.xhtml"), navigation.map { it.target })
        assertEquals(EpubLinkResult.Location(0, 0), parser.resolveLink(epub, navigation[0].target))
        assertEquals(EpubLinkResult.Location(0, 1), parser.resolveLink(epub, navigation[1].target))
        assertEquals(before, parser.readChapter(epub, 0))
        assertEquals("目录未列出的正文也不能丢失。", parser.readChapter(epub, 1)!!.paragraphs.single())
        assertEquals(listOf("第一段，书签仍然指向这里。", "第二节", "第二节正文，搜索与批注仍然指向这里。"), before.paragraphs)
    }
}
