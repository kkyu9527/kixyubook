package com.kixyu9527.kixyubook.core.reader.engine.epub.browser

import com.kixyu9527.kixyubook.core.reader.engine.EpubBookParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubBrowserDocumentTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun browserDocumentsRetainCssButRemovePublisherCodeFramesAndNavigationOverrides() {
        val source = """<html xmlns="http://www.w3.org/1999/xhtml"><head><meta name="viewport" content="width=1200,height=800"/><meta http-equiv="refresh" content="0;url=https://evil.invalid"/><base href="https://evil.invalid"/><link rel="stylesheet" href="book.css"/></head><body onload="authorScript()"><script>authorScript()</script><iframe src="https://evil.invalid"/><p><a href="javascript:authorScript()">Text</a></p><div style="display:grid;position:absolute;left:200px">Layout</div></body></html>"""
        val book = archive("chapter.xhtml" to source)
        val prepared = EpubBrowserDocuments.prepare(book, "chapter.xhtml")
        assertEquals(1200, prepared.viewportWidth)
        assertEquals(800, prepared.viewportHeight)
        listOf("authorScript", "evil.invalid", "<script", "<iframe", "<base", "http-equiv=\"refresh\"")
            .forEach { assertFalse(it, it in prepared.html) }
        assertTrue("Content-Security-Policy" in prepared.html)
        assertTrue("script-src 'none'" in prepared.html)
        assertTrue("book.css" in prepared.html)
        assertTrue("display:grid" in prepared.html)
        assertTrue("charset=\"utf-8\"" in prepared.html.lowercase())
    }

    @Test fun archiveUrlsNeverResolveOtherOriginsFilesEncodedTraversalOrScriptResources() {
        listOf("file:///private/data", "content://book/1", "http://epub.kixyu.invalid/a.png",
            "https://epub.kixyu.invalid.evil/a.png", "https://user@epub.kixyu.invalid/a.png",
            "$EPUB_BROWSER_ORIGIN/%2e%2e/private", "$EPUB_BROWSER_ORIGIN/a%2f..%2fprivate",
            "$EPUB_BROWSER_ORIGIN/a%00.png").forEach { assertNull(it, EpubBrowserDocuments.archivePath(it)) }
        val path = "OPS/图 像/a+b.png"
        assertEquals(path, EpubBrowserDocuments.archivePath(EpubBrowserDocuments.url(path)))
        val book = archive("bad.js" to "authorScript()", "font.ttf" to "font", "style.css" to "p {color:red}")
        assertNull(EpubBrowserDocuments.resource(book, "bad.js"))
        EpubBrowserDocuments.resource(book, "style.css")!!.stream.use { assertEquals("p {color:red}", it.bufferedReader().readText()) }
    }

    @Test fun fixedLayoutKeepsAuthoredPagesWhileOrdinaryNovelsStayOnNativePagination() {
        val fixed = publication("<meta property=\"rendition:layout\">pre-paginated</meta>", "p {color:black}")
        val parser = EpubBookParser()
        assertEquals(true, parser.readBrowserPlan(fixed)?.fixedLayout)
        assertEquals("fixed-layout", parser.readBrowserPlan(fixed)?.reason)
        assertNull(parser.readBrowserPlan(publication("", "@font-face {font-family:book;} p {font-size:1em}")))
        assertNull(parser.readBrowserPlan(publication("", "nav {display:flex;} @media print {body {display:grid;}}")))
        assertEquals("complex-layout", parser.readBrowserPlan(publication("", "@charset 'utf-8'; body {display:grid;}"))?.reason)
        assertEquals("vertical-writing", parser.readBrowserPlan(publication("", "body {writing-mode:vertical-rl;}"))?.reason)
        assertEquals("complex-layout", parser.readBrowserPlan(publication("", "", "<section style=\"display:grid\"><p>Body.</p></section>"))?.reason)
        assertEquals("mathml", parser.readBrowserPlan(publication("", "", "<math xmlns=\"http://www.w3.org/1998/Math/MathML\"><mi>x</mi></math>"))?.reason)
    }

    private fun publication(metadata: String, css: String, body: String = "<p>Readable.</p>") = archive(
        "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""",
        "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf"><metadata>$metadata</metadata><manifest><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/><item id="css" href="style.css" media-type="text/css"/></manifest><spine><itemref idref="c"/></spine></package>""",
        "c.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body>$body</body></html>""",
        "style.css" to css,
    )

    private fun archive(vararg entries: Pair<String,String>) = folder.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (path, text) ->
            zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
        } }
    }
}
