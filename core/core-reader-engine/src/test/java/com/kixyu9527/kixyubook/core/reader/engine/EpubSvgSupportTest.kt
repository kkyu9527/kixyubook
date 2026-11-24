package com.kixyu9527.kixyubook.core.reader.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubSvgSupportTest {
    @get:Rule val folder = TemporaryFolder()
    private val vector = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 100"><rect x="0" y="0" width="200" height="100" fill="red"/></svg>"""

    @Test fun standaloneSvgRendersAsActualColoredPixelsWithinItsAspectRatio() {
        val book = archive("drawing.svg" to vector)
        ZipFile(book).use { zip ->
            val bitmap = renderEpubSvg(zip, "drawing.svg", 400, 400)!!
            assertEquals(400, bitmap.width)
            assertEquals(200, bitmap.height)
            val pixel = bitmap.getPixel(100, 100)
            assertTrue(pixel ushr 16 and 255 > 240)
            assertTrue(pixel ushr 8 and 255 < 10)
        }
    }

    @Test fun inlineVectorIsAnImageBlockAndSvgLabelsDoNotPolluteSearchText() = runBlocking {
        val book = archive(
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""",
            "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf"><manifest><item id="chapter" href="body.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""",
            "body.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>Before the illustration.</p>$vector<p>After the illustration.</p></body></html>""",
        )
        val parsed = EpubBookParser().readChapter(book, 0)!!
        assertEquals(listOf("Before the illustration.", "After the illustration."), parsed.paragraphs)
        val image = parsed.images.single()
        assertEquals("body.xhtml${INLINE_SVG_FRAGMENT}0", image.resourcePath)
        assertEquals(1, image.contentIndex)
        assertEquals(200, image.intrinsicWidth)
        ZipFile(book).use { assertNotNull(renderEpubSvg(it, image.resourcePath, 400, 400)) }
    }

    @Test fun scriptsEventsExternalAssetsAndEntityContentsNeverReachTheSvgRenderer() {
        val secret = folder.newFile("secret.txt").apply { writeText("PRIVATE_VECTOR_SECRET") }
        val source = """<!DOCTYPE svg [<!ENTITY secret SYSTEM "${secret.toURI()}">]><svg xmlns="http://www.w3.org/2000/svg" onload="danger()" viewBox="0 0 20 20"><script>danger()</script><foreignObject>external html</foreignObject><image href="file:///private/image.png"/><style>@import 'https://invalid.invalid/x.css'; rect { fill:url(https://invalid.invalid/a.svg); }</style><text>&secret;</text><rect width="20" height="20" fill="red"/></svg>"""
        val book = archive("drawing.svg" to source)
        ZipFile(book).use { zip ->
            val sanitized = sanitizedEpubSvg(zip, "drawing.svg")!!
            listOf("PRIVATE_VECTOR_SECRET", "DOCTYPE", "file:", "https:", "danger", "foreignObject", "onload")
                .forEach { assertFalse(it, it in sanitized) }
            assertNotNull(renderEpubSvg(zip, "drawing.svg", 200, 200))
        }
    }

    @Test fun localUseReferencesRemainSupportedButReferenceCyclesDoNotReachRendering() {
        val valid = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"><defs><rect id="r" width="20" height="20" fill="red"/></defs><use href="#r"/></svg>"""
        val invalid = """<svg xmlns="http://www.w3.org/2000/svg"><defs><g id="a"><use href="#b"/></g><g id="b"><use href="#a"/></g></defs><use href="#a"/></svg>"""
        ZipFile(archive("valid.svg" to valid, "invalid.svg" to invalid)).use { zip ->
            assertNotNull(sanitizedEpubSvg(zip, "valid.svg"))
            val pixel = renderEpubSvg(zip, "valid.svg", 200, 200)!!.getPixel(100, 100)
            assertEquals(255, pixel ushr 24)
            assertTrue(pixel ushr 16 and 255 > 240)
            assertTrue(pixel ushr 8 and 255 < 10)
            assertNull(sanitizedEpubSvg(zip, "invalid.svg"))
        }
    }

    @Test fun svgAllocationIsBoundedEvenForAnExtremeRequestedViewport() {
        ZipFile(archive("drawing.svg" to vector)).use { zip ->
            val bitmap = renderEpubSvg(zip, "drawing.svg", 100_000, 100_000)!!
            assertTrue(bitmap.width.toLong() * bitmap.height <= 4L * 1024 * 1024)
        }
    }

    private fun archive(vararg entries: Pair<String, String>) = folder.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (path, text) ->
            zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
        } }
    }
}
