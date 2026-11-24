package com.kixyu9527.kixyubook.core.reader.engine

import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class EpubStyleCompatibilityTest {
    @Test fun conditionalPublisherCssCannotLeakIntoAllThemes() {
        val rules = parseCss("""
            @charset "UTF-8";
            p { color: black; }
            @media (prefers-color-scheme: dark) { p { display: none; } }
            @media print { p { color: white; } }
            @media screen { strong { font-weight: bold; } }
            @supports (display: grid) { p { visibility: hidden; } }
            @layer book { em { font-style: italic; } }
        """.trimIndent(), "book.css")
        assertEquals(listOf("p", "strong", "em"), rules.map { it.selector })
        assertTrue(rules.none { "display" in it.declarations || "visibility" in it.declarations })
    }

    @Test fun unsupportedPseudoSelectorsNeverBecomeUnconditionalStyles() {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse("""<html xmlns="http://www.w3.org/1999/xhtml"><body><p>Text</p></body></html>""".byteInputStream())
        val p = document.getElementsByTagNameNS("*", "p").item(0) as org.w3c.dom.Element
        assertFalse(p.matchesCssSelector("p:not(.body)"))
        assertFalse(p.matchesCssSelector("p::before"))
        assertTrue(p.matchesCssSelector(":root p"))
        assertFalse(p.matchesCssSelector("p:root"))
    }

    @Test fun themeInkAdaptationNeverInvertsPhotographsScansOrFullSizeArtwork() {
        val transparent = IntArray(16)
        assertTrue(isThemeInkImage(4, 4, transparent.copyOf().apply { this[0] = 0xff000000.toInt() }))
        assertTrue(isThemeInkImage(4, 4, transparent.copyOf().apply { this[0] = 0xffffffff.toInt() }))
        assertFalse(isThemeInkImage(4, 4, IntArray(16) { 0xff000000.toInt() }))
        assertFalse(isThemeInkImage(4, 4, transparent.copyOf().apply { this[0] = 0xffff0000.toInt() }))
        assertFalse(isThemeInkImage(4, 4, transparent.copyOf().apply { this[0] = 0xff999999.toInt() }))
        assertFalse(isThemeInkImage(1000, 1000, transparent.copyOf().apply { this[0] = 0xff000000.toInt() }))
        assertFalse(isThemeInkImage(4, 4, transparent))
    }

    @Test fun childSiblingAndAttributeSelectorsHaveTheirActualCssMeaning() {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse("""<html xmlns="http://www.w3.org/1999/xhtml"><body><section><p class="lead">A</p><div><p title="two words">B</p></div><p>C</p></section></body></html>""".byteInputStream())
        val paragraphs = document.getElementsByTagNameNS("*", "p")
        val nested = paragraphs.item(1) as org.w3c.dom.Element
        val last = paragraphs.item(2) as org.w3c.dom.Element
        assertTrue(nested.matchesCssSelector("section p[title='two words']"))
        assertFalse(nested.matchesCssSelector("section > p"))
        assertTrue(last.matchesCssSelector("div + p"))
        assertTrue(last.matchesCssSelector("p.lead ~ p"))
        assertFalse(last.matchesCssSelector("p.lead + p"))
    }

    @Test fun hugeAndPanoramicImagesHaveABoundedDecodeRegardlessOfTheirEncodedSize() {
        listOf(100_000 to 100, 100 to 100_000, 60_000 to 60_000, Int.MAX_VALUE to Int.MAX_VALUE).forEach { (w, h) ->
            val sample = epubImageSampleSize(w, h, 1080, 1920)
            assertTrue((w.toLong() / sample) * (h.toLong() / sample) <= 4L * 1024 * 1024)
            assertTrue(maxOf(w, h) / sample <= 8192)
        }
        assertEquals(1, epubImageSampleSize(100, 200, 1080, 1920))
        assertEquals(4, epubImageSampleSize(4000, 4000, 1000, 1000))
    }

    @Test fun transparentDarkArtworkGetsPaperOnlyWhenTheActualReaderBackgroundNeedsIt() {
        val ink = readerImageProfile(IntArray(100) { if (it < 40) 0 else 0xff302818.toInt() }).inkLuminance!!
        assertNotNull(imagePaperBackdrop(ink, .015f))
        assertNull(imagePaperBackdrop(ink, .95f))
        assertNull(readerImageProfile(IntArray(100) { 0xff101010.toInt() }).inkLuminance) // opaque photos
        assertNull(imagePaperBackdrop(null, .015f))
    }

    @Test fun transparentWhiteInkCanAlsoBeReadOnALightThemeWithoutRecoloringIt() {
        val ink = readerImageProfile(IntArray(100) { if (it < 40) 0 else 0xffeeeeee.toInt() }).inkLuminance!!
        assertNotNull(imagePaperBackdrop(ink, .95f))
        assertNull(imagePaperBackdrop(ink, .015f))
    }
}
