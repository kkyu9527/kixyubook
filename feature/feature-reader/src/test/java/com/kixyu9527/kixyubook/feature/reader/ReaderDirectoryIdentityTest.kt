package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.reader.engine.ReaderChapter
import org.junit.Assert.*
import org.junit.Test

class ReaderDirectoryIdentityTest {
    @Test fun addingMissingSpineEntriesPreservesOpenChapterAndStoredAnchors() {
        val old = Chapter(22, "book", "正文", 5, chapterKey = "permanent-key")
        val content = ReaderChapter(old.id, "book", old.title, old.index, emptyList())
        val bookmark = Bookmark("mark", "book", old.id, old.title, old.index, 12, "正文", 1)
        val state = ReaderUiState(
            chapters = listOf(old), chapter = content, chapterIndex = 0,
            prefetchedChapters = mapOf(0 to content), restorePosition = 12, restoreCharOffset = 7,
            bookmarks = listOf(bookmark), navigationVersion = 4,
            searchQuery = "正文", selectedSearchIndex = 0,
        )
        val updated = state.withChapters(listOf(Chapter(33, "book", "序", 2), old))
        assertEquals(1, updated.chapterIndex)
        assertSame(content, updated.chapter)
        assertSame(content, updated.prefetchedChapters[1])
        assertSame(state.bookmarks, updated.bookmarks)
        assertEquals(12, updated.restorePosition)
        assertEquals(7, updated.restoreCharOffset)
        assertEquals(4, updated.navigationVersion)
        assertEquals(state.searchQuery, updated.searchQuery)
        assertEquals("permanent-key", updated.chapters[1].chapterKey)
    }

    @Test fun multipleNavigationAnchorsDoNotCreateOrRenumberChapters() {
        val chapter = Chapter(22, "book", "正文", 5, chapterKey = "stable")
        val entries = listOf(
            EpubNavigationEntry(5, "第一节", "OPS/body.xhtml#a"),
            EpubNavigationEntry(5, "第二节", "OPS/body.xhtml#b"),
        )
        val rows = buildDirectoryRows(listOf(chapter), emptyMap(), entries).filterIsInstance<DirectoryRow.ChapterRow>()
        assertEquals(listOf(22L, 22L), rows.map { it.id })
        assertEquals(listOf(0, 0), rows.map { it.index })
        assertEquals(entries.map { it.target }, rows.map { it.navigationTarget })
        assertEquals(2, rows.map { it.key }.distinct().size)
    }

    @Test fun singleNavigationAnchorStillOpensItsFragment() {
        val chapter = Chapter(22, "book", "正文", 5)
        val entry = EpubNavigationEntry(5, "正文起点", "OPS/body.xhtml#start")
        val row = buildDirectoryRows(listOf(chapter), emptyMap(), listOf(entry)).single() as DirectoryRow.ChapterRow
        assertEquals(entry.target, row.navigationTarget)
        assertEquals(entry.title, row.navigationTitle)
        assertEquals(chapter.id, row.id)
    }
}
