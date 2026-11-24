package com.kixyu9527.kixyubook.core.reader.engine

import com.kixyu9527.kixyubook.core.common.model.EpubLinkResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubStructureCompatibilityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun splitSectionsInBothNcxAndHtmlNavigationKeepAllPhysicalSourcesAndCanonicalTitles() = runBlocking {
        listOf(false, true).forEach { epub3 ->
            val pages = listOf(
                "<h1>扉页</h1>", "<h1>Chapter ONE<br/>Decorated heading</h1>",
                "<p>First section body: searchable and annotatable.</p>",
                "<h1>Chapter TWO</h1>", "<p>Second section body.</p>",
            )
            val nav = if (epub3) """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="s0.xhtml">Volume Alpha</a><ol><li><a href="s1.xhtml">First section</a></li><li><a href="s3.xhtml">Second section</a></li></ol></li></ol></nav></body></html>"""
            else """<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap><navPoint><navLabel><text>Volume Alpha</text></navLabel><content src="s0.xhtml"/><navPoint><navLabel><text>First section</text></navLabel><content src="s1.xhtml"/></navPoint><navPoint><navLabel><text>Second section</text></navLabel><content src="s3.xhtml"/></navPoint></navPoint></navMap></ncx>"""
            val book = publication(pages, nav, epub3)
            val parser = EpubBookParser()
            val outlines = parser.readChapterOutlines(book)
            assertEquals((0..4).toList(), outlines.map { it.sourceIndex })
            assertEquals(listOf("Volume Alpha", "First section", "First section", "Second section", "Second section"), outlines.map { it.title })
            assertEquals("Volume Alpha", outlines[2].volumeTitle)
            assertEquals(outlines[1].volumeIndex, outlines[2].volumeIndex)
            assertEquals(listOf(0, 2, 4), parser.readNavigation(book).map { it.sourceIndex })
            assertEquals(listOf(emptyList(), listOf(1), listOf(3)), parser.readNavigation(book).map { it.titlePageSourceIndices })
            assertEquals(EpubLinkResult.Location(2), parser.resolveLink(book, "OPS/s1.xhtml"))
            assertEquals(EpubLinkResult.Location(4), parser.resolveLink(book, "OPS/s3.xhtml"))
            assertEquals("First section", parser.readChapter(book, 1, "stale cached title")!!.title)
            assertEquals("First section", parser.readChapter(book, 2)!!.title)
            assertEquals("First section body: searchable and annotatable.", parser.readChapter(book, 2)!!.paragraphs.single())
            val indexed = mutableMapOf<Int, DocumentChapter>()
            parser.readIndexedChapters(book) { index, chapter -> indexed[index] = chapter }
            assertEquals((0..4).toSet(), indexed.keys)
            assertEquals("Second section body.", indexed.getValue(4).paragraphs.single())
        }
    }

    @Test fun doctypePreservesCssBackgroundPicturesAndImageOnlyFootnoteLinks() = runBlocking {
        val book = publication(listOf(
            """<h1 title="Plate"><br/></h1>""",
            """<h1>正文</h1><p id="start">Visible text<a epub:type="noteref" href="#footnote-1"><img src="art.png" alt="a note"/></a>.</p><aside id="footnote-1" epub:type="footnote"><p>Footnote explanation.</p></aside>""",
        ), doctype = true, bodyStyle = "background-image: url(art.png); background-size: contain;")
        val parser = EpubBookParser()
        val plate = parser.readChapter(book, 0)!!
        assertEquals("OPS/art.png", plate.images.single().resourcePath)
        assertTrue(plate.images.single().isFullPage)
        assertFalse(plate.images.single().cropToFill)
        val body = parser.readChapter(book, 1)!!
        assertTrue(body.paragraphs.any { "Visible text†." in it })
        assertEquals("OPS/s1.xhtml#footnote-1", body.paragraphSpans.flatten().single { it.linkTarget != null && "SUPERSCRIPT" in it.styles.map { s -> s.name } }.linkTarget)
        assertEquals(EpubLinkResult.Footnote("注释", "Footnote explanation."), parser.resolveLink(book, "OPS/s1.xhtml#footnote-1"))
        assertEquals(EpubLinkResult.Location(1, 0), parser.resolveLink(book, "OPS/s1.xhtml#start"))
    }

    @Test fun realBodyIllustrationVolumeAndFragmentTargetsAreNeverMistakenForRedundantTitles() {
        fun nav(target: String) = """<ncx><navMap><navPoint><navLabel><text>Volume</text></navLabel><content src="s0.xhtml"/><navPoint><navLabel><text>First</text></navLabel><content src="$target"/></navPoint></navPoint></navMap></ncx>"""
        val parser = EpubBookParser()
        for (body in listOf("<h1>First</h1><p>Keep actual body.</p>",
            "<h1>First</h1><img src=\"art.png\"/>",
            "<h1>First</h1><div style=\"background-image:url(art.png);height:100%\"/>")) {
            val book = publication(listOf("<h1>Volume</h1>", body, "<p>Continuation.</p>"), nav("s1.xhtml"))
            assertTrue(body, parser.readNavigation(book).all { it.titlePageSourceIndices.isEmpty() })
        }
        val fragment = publication(listOf("<h1>Volume</h1>", "<h1 id=\"named\">First</h1>", "<p>Continuation.</p>"), nav("s1.xhtml#named"))
        assertEquals(1, parser.readNavigation(fragment).last().sourceIndex)
        val fixed = publication(listOf("<h1>Volume</h1>", "<h1>First</h1>", "<p>Continuation.</p>"), nav("s1.xhtml"),
            metadata = """<meta property="rendition:layout">pre-paginated</meta>""")
        assertTrue(parser.readNavigation(fixed).all { it.titlePageSourceIndices.isEmpty() })
        // A decorative root background must not promote all heading aliases to illustration pages.
        val decorative = publication(listOf("<h1>Volume</h1>", "<h1>First</h1>", "<p>Continuation.</p>"), nav("s1.xhtml"),
            bodyStyle = "background-image:url(art.png)")
        assertEquals(listOf(1), parser.readNavigation(decorative).last().titlePageSourceIndices)
    }

    @Test fun anAbsentSynopsisIsNotReplacedByCopyrightOrNarrativePrologue() {
        listOf("书籍信息", "Copyright", "引子", "Preface", "序言").forEach { label ->
            val book = publication(listOf("<h1>$label</h1><p>Copyright and front matter are not a synopsis.</p>"))
            assertEquals(label, "", EpubBookParser().readMetadata(book, book.name).description)
        }
        val synopsis = publication(listOf("<h1>あらすじ</h1><p>A real description.</p>"))
        assertEquals("A real description.", EpubBookParser().readMetadata(synopsis, synopsis.name).description)
    }

    @Test fun uniqueIdentifierSelectionMatchesInDomAndStreamingPackages() {
        val metadata = """<dc:identifier id="store">1234</dc:identifier><dc:identifier id="identity">urn:uuid:fixed-book-id</dc:identifier>"""
        val book = publication(listOf("<p>Body.</p>"), metadata = metadata)
        assertEquals("fixed-book-id", EpubBookParser().readMetadata(book, book.name).identityHint)
        val opf = """<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="identity"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">$metadata</metadata></package>"""
        assertEquals("urn:uuid:fixed-book-id", readPackageStreaming(opf.byteInputStream(), "OPS/book.opf").identifier)
    }

    @Test fun headingFallbackWorksBeyondFrontMatterWhenThePublisherProvidesNoToc() {
        val book = publication(List(22) { "<h1>Meaningful title $it</h1><p>Body $it.</p>" })
        val outlines = EpubBookParser().readChapterOutlines(book)
        assertEquals("Meaningful title 21", outlines.last().title)
        assertEquals(21, outlines.last().sourceIndex)
    }

    @Test fun anExternalEntityNeverReadsALocalFile() = runBlocking {
        val secret = folder.newFile("external.txt").apply { writeText("PRIVATE_ENTITY_MUST_NOT_LEAK") }
        val book = publication(listOf("<h1>Chapter</h1><p>Readable &external; body.</p>"),
            declaration = """<!DOCTYPE html [<!ENTITY external SYSTEM "${secret.toURI()}">]>""")
        val parsed = EpubBookParser().readChapter(book, 0)!!
        assertTrue(parsed.paragraphs.any { "Readable" in it && "body." in it })
        assertFalse(parsed.paragraphs.any { "PRIVATE_ENTITY_MUST_NOT_LEAK" in it })
    }

    @Test fun aSubstantialInlineIllustrationRemainsReadableWithoutSplittingTextAnchors() = runBlocking {
        val book = publication(listOf("""<p>Before<img src="art.png" alt="Illustration"/>after.</p>"""), artWidth = 200)
        val parsed = EpubBookParser().readChapter(book, 0)!!
        assertEquals(listOf("Beforeafter."), parsed.paragraphs)
        assertEquals("OPS/art.png", parsed.images.single().resourcePath)
        assertEquals(1, parsed.images.single().contentIndex)
    }

    private fun publication(
        bodies: List<String>,
        nav: String? = null,
        epub3: Boolean = false,
        doctype: Boolean = false,
        bodyStyle: String = "",
        metadata: String = "",
        artWidth: Int = 40,
        declaration: String = if (doctype) """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://invalid.invalid/never-fetch.dtd">""" else "",
    ) = folder.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
            val navManifest = if (nav == null) "" else """<item id="nav" href="navigation.${if (epub3) "xhtml" else "ncx"}" media-type="${if (epub3) "application/xhtml+xml" else "application/x-dtbncx+xml"}" properties="${if (epub3) "nav" else ""}"/>"""
            val manifest = bodies.indices.joinToString("") { """<item id="s$it" href="s$it.xhtml" media-type="application/xhtml+xml"/>""" }
            val spine = bodies.indices.joinToString("") { """<itemref idref="s$it"/>""" }
            entry("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="identity"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Compatibility fixture</dc:title>$metadata</metadata><manifest>$navManifest$manifest<item id="art" href="art.png" media-type="image/png"/></manifest><spine>$spine</spine></package>""")
            if (nav != null) entry("OPS/navigation.${if (epub3) "xhtml" else "ncx"}", nav)
            bodies.forEachIndexed { i, body -> entry("OPS/s$i.xhtml", """$declaration<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body style="$bodyStyle">$body</body></html>""") }
            zip.putNextEntry(ZipEntry("OPS/art.png"))
            zip.write(ByteArray(24).apply { this[19] = artWidth.toByte(); this[23] = 60 })
            zip.closeEntry()
        }
    }
}
