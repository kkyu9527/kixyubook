package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.Chapter
import com.kixyu9527.kixyubook.core.common.model.EpubNavigationEntry
import org.junit.Assert.*
import org.junit.Test

class ReaderEpubSectionsTest {
    private val chapters = listOf(
        Chapter(10, "book", "Volume", 0),
        Chapter(11, "book", "Chapter one", 1, "Volume", 0),
        Chapter(12, "book", "Chapter one", 2, "Volume", 0),
        Chapter(13, "book", "Chapter two", 3, "Volume", 0),
        Chapter(14, "book", "Chapter two", 4, "Volume", 0),
    )
    private val navigation = listOf(
        EpubNavigationEntry(0, "Volume", "plate.xhtml"),
        EpubNavigationEntry(1, "Chapter one", "first.xhtml", 1),
        EpubNavigationEntry(3, "Chapter two", "second.xhtml", 1),
    )

    @Test fun tocHidesContinuationResourcesButKeepsTheirSelectionCoverageAndVolumeMembership() {
        val rows = buildDirectoryRows(chapters, mapOf(0 to true), navigation)
        val volume = rows.first() as DirectoryRow.Volume
        val children = rows.filterIsInstance<DirectoryRow.ChapterRow>()
        assertEquals(2, volume.chapterCount)
        assertEquals(chapters.map { it.id }.toSet(), volume.chapterIds)
        assertEquals(listOf(1, 3), children.map { it.index })
        assertEquals(1..2, children[0].coveredChapterIndices)
        assertEquals(3..4, children[1].coveredChapterIndices)
        assertEquals("Volume", stickyVolumeFor(rows, 2, 0)?.title)
    }

    @Test fun chapterButtonsSkipContinuationsButPageCoordinatesRemainUnchanged() {
        assertEquals(3, chapterButtonTarget(chapters, navigation, 1, 1))
        assertEquals(3, chapterButtonTarget(chapters, navigation, 2, 1))
        assertEquals(1, chapterButtonTarget(chapters, navigation, 4, -1))
        assertEquals(2, chapterButtonTarget(chapters, emptyList(), 1, 1))
        assertEquals(listOf(0, 1, 2, 3, 4), chapters.map { it.index })
    }

    @Test fun invalidNavigationFallsBackToEveryPhysicalResource() {
        val rows = buildDirectoryRows(chapters, mapOf(0 to true), listOf(EpubNavigationEntry(100, "Broken link", "missing.xhtml")))
        assertEquals(listOf(1, 2, 3, 4), rows.filterIsInstance<DirectoryRow.ChapterRow>().map { it.index })
    }

    @Test fun titleOnlyEntriesAreNotPagesAndOldBookmarksOrProgressStillResolveToTheBody() {
        val entries = listOf(navigation[0],
            navigation[1].copy(sourceIndex = 2, titlePageSourceIndices = listOf(1)),
            navigation[2].copy(sourceIndex = 4, titlePageSourceIndices = listOf(3)))
        val visible = readerVisibleChapters(chapters, entries)
        assertEquals(listOf(0, 2, 4), visible.map { it.index })
        assertEquals(2, readingSourceIndex(1, entries))
        assertEquals(4, readingSourceIndex(3, entries))
        assertEquals(2, chapterButtonTarget(visible, entries, 1, 1))
        val old = com.kixyu9527.kixyubook.core.common.model.ReadingProgress("book", 11, 0, 0, 1)
        assertEquals(1, readerProgressChapterIndex(visible, old, entries, chapters))
        val bookmark = com.kixyu9527.kixyubook.core.common.model.Bookmark("old", "book", 11, "First", 1, 0, "preview", 1)
        val projected = readerVisibleBookmarks(listOf(bookmark), visible, entries).single()
        assertEquals(bookmark.uuid, projected.uuid)
        assertEquals(12L, projected.chapterId)
        assertEquals(2, projected.chapterIndex)
        assertEquals(bookmark.preview, projected.preview)
        assertEquals(projected, currentVisiblePageBookmark(listOf(projected), 12, ReaderPositionState(0, 0)))
        val rows = buildDirectoryRows(visible, mapOf(0 to true), entries)
        assertEquals(listOf(1, 2), rows.filterIsInstance<DirectoryRow.ChapterRow>().map { it.index })
    }
}
