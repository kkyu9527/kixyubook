package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import com.kixyu9527.kixyubook.core.reader.engine.EpubBookParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A real EPUB parser feeds real ViewModels: no preconstructed navigation or content answers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderEpubNavigationIntegrationTest {
    @get:Rule val folder = TemporaryFolder()
    @Before fun start() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun finish() { Dispatchers.resetMain() }

    @Test fun titlePagesAreSkippedByDirectoryButtonsAndBothNaturalPageDirectionsWithoutChangingSourceAnchors() = runBlocking {
        val book = publication()
        // A previously saved title-page ID must restore into its body, not show an empty page.
        val harness = readerRecoveryHarness(epubFile = book,
            initialProgress = ReadingProgress("book", 2, 0, 0, 1))
        val reader = harness.open()
        suspend fun at(source: Int) = withTimeout(10_000) {
            reader.uiState.first { !it.loading && it.chapter?.index == source }
        }
        try {
            var state = at(3)
            assertEquals(listOf(0, 3, 4, 6), state.chapters.map { it.index })
            assertEquals("First section body.", state.chapter!!.paragraphs.single().text)
            val rows = buildDirectoryRows(state.chapters, mapOf(0 to true), state.epubNavigation)
            val directory = rows.filterIsInstance<DirectoryRow.ChapterRow>()
            assertEquals(listOf("First", "Second"), directory.map { it.navigationTitle })

            reader.jumpToChapter(directory.last().index)
            assertEquals("Second section body.", at(6).chapter!!.paragraphs.single().text)
            reader.moveChapter(-1)
            state = at(3)
            assertEquals(0, state.restorePosition)
            // Paging visits body continuations but never either of the two title-only resources.
            reader.moveChapterFromPage(state.chapterIndex, 1)
            state = at(4)
            assertEquals("First continuation.", state.chapter!!.paragraphs.single().text)
            reader.moveChapterFromPage(state.chapterIndex, 1)
            state = at(6)
            reader.moveChapterFromPage(state.chapterIndex, -1, true)
            at(4)
            reader.moveChapter(1)
            at(6)

            // Legacy bookmark aliases and actual search/annotation coordinates share resolution.
            reader.requestLocation(sourceLocation(2, 0, source = ReaderLocationSource.BOOKMARK))
            at(3)
            reader.requestLocation(sourceLocation(4, 0, 5, ReaderLocationSource.SEARCH))
            state = at(4)
            assertEquals(5, state.restoreCharOffset)
            reader.requestLocation(sourceLocation(6, 0, 7, ReaderLocationSource.ANNOTATION))
            assertEquals(7, at(6).restoreCharOffset)
            reader.onPageSettled(0, 7, true, 0, reader.uiState.value.layoutVersion)
            reader.checkpointReadingProgress()
            withTimeout(10_000) { harness.durable.first { it?.chapterId == 7L && it.charOffset == 7 } }
            val reopened = harness.open()
            withTimeout(10_000) { reopened.uiState.first { !it.loading && it.chapter?.index == 6 } }
            assertEquals(7, reopened.uiState.value.restoreCharOffset)
            assertEquals((0..6).toList(), EpubBookParser().readChapterOutlines(book).map { it.sourceIndex })
        } finally { harness.closeAll() }
    }

    private fun publication() = folder.newFile("split.epub").also { file ->
        val bodies = listOf("<h1>Volume</h1>", "<h1>First</h1>", "<h2>Decorated title</h2>",
            "<p>First section body.</p>", "<p>First continuation.</p>", "<h1>Second</h1>", "<p>Second section body.</p>")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""")
            val manifest = bodies.indices.joinToString("") { """<item id="s$it" href="s$it.xhtml" media-type="application/xhtml+xml"/>""" }
            val spine = bodies.indices.joinToString("") { """<itemref idref="s$it"/>""" }
            entry("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf"><metadata/><manifest>$manifest<item id="nav" href="toc.ncx" media-type="application/x-dtbncx+xml"/></manifest><spine toc="nav">$spine</spine></package>""")
            entry("OPS/toc.ncx", """<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap><navPoint><navLabel><text>Volume</text></navLabel><content src="s0.xhtml"/><navPoint><navLabel><text>First</text></navLabel><content src="s1.xhtml"/></navPoint><navPoint><navLabel><text>Second</text></navLabel><content src="s5.xhtml"/></navPoint></navPoint></navMap></ncx>""")
            bodies.forEachIndexed { i, body -> entry("OPS/s$i.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><body>$body</body></html>""") }
        }
    }
}
