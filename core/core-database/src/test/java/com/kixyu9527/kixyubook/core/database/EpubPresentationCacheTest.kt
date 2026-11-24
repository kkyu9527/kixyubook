package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.model.EpubBrowserPlan
import com.kixyu9527.kixyubook.core.common.model.EpubNavigationEntry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubPresentationCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private val data = EpubPresentation(listOf(EpubNavigationEntry(2,"First","title.xhtml",1,listOf(0,1))),
        EpubBrowserPlan(listOf("title.xhtml","plate.xhtml","body.xhtml"),true,"fixed-layout"))

    @Test fun reopeningAfterMemoryLossReadsNavigationAndRendererChoiceWithoutCallingTheParser() {
        val root = folder.newFolder(); val source = folder.newFile().apply { writeText("source") }
        EpubPresentationCache(root).load("book","hash",source) { data }
        val reopened = EpubPresentationCache(root).load("book","hash",source) { error("Reopen must not rescan XHTML") }
        assertEquals(data, reopened)
    }

    @Test fun replacementSourceAndChangedContentHashesNeverUseTheOldPresentation() {
        val cache = EpubPresentationCache(folder.newFolder()); val source = folder.newFile().apply { writeText("source") }
        cache.load("book","hash",source) { data }
        source.appendText(" changed")
        val changed = data.copy(browser = null)
        assertEquals(changed, cache.load("book","hash",source) { changed })
        assertEquals(data, cache.load("book","new-hash",source) { data })
    }

    @Test fun truncatedOrUnwritableCachesNeverPreventOpeningAReadableBook() {
        val root = folder.newFolder(); val source = folder.newFile().apply { writeText("source") }
        val cache = EpubPresentationCache(root)
        cache.load("book","hash",source) { data }
        root.listFiles()!!.single { it.extension == "bin" }.writeBytes(byteArrayOf(1,2,3))
        assertEquals(data, cache.load("book","hash",source) { data })
        assertEquals(data, EpubPresentationCache(folder.newFile()).load("book","hash",source) { data })
    }

    @Test fun deletionAndLibraryPruningOnlyRemoveTheOwnedDerivedCache() {
        val root = folder.newFolder(); val source = folder.newFile().apply { writeText("source") }
        val unrelated = java.io.File(root,"unrelated.txt").apply { writeText("keep") }
        val cache = EpubPresentationCache(root)
        cache.load("first","hash",source) { data }
        cache.load("second","hash",source) { data }
        cache.clearBook("first")
        assertEquals(data, cache.load("second","hash",source) { error("Must retain second book") })
        var reparsed = false
        cache.load("first","hash",source) { reparsed = true; data }
        assertTrue(reparsed)
        cache.retainBooks(setOf("first"))
        assertEquals(1, root.listFiles()!!.count { it.extension == "bin" })
        assertTrue(unrelated.isFile)
        assertEquals("source", source.readText())
    }
}
