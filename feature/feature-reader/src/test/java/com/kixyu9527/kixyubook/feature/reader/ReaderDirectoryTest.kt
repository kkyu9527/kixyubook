package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReaderDirectoryTest {
    @Test
    fun epubVolumePageTargetsReadableParentAndDoesNotDuplicateIt() {
        val chapters = listOf(
            chapter(10, "第一卷 风起", 0),
            chapter(11, "第一章 初见", 1, "第一卷 风起", 0),
            chapter(12, "第二章 同行", 2, "第一卷 风起", 0),
        )

        val rows = buildDirectoryRows(chapters, mapOf(0 to true))
        val volume = rows.first() as DirectoryRow.Volume

        assertEquals(0, volume.targetChapterIndex)
        assertEquals(true, volume.hasOwnContent)
        assertEquals(2, volume.chapterCount)
        assertEquals(listOf(1, 2), rows.filterIsInstance<DirectoryRow.ChapterRow>().map { it.index })
        assertFalse(rows.filterIsInstance<DirectoryRow.ChapterRow>().any { it.index == 0 })
    }

    @Test
    fun txtVolumeIntroductionTargetsItsOwnContent() {
        val chapters = listOf(
            chapter(20, "第一卷 风起", 0, "第一卷 风起", 0),
            chapter(21, "第一章 初见", 1, "第一卷 风起", 0),
        )

        val rows = buildDirectoryRows(chapters, mapOf(0 to true))
        val volume = rows.first() as DirectoryRow.Volume

        assertEquals(0, volume.targetChapterIndex)
        assertEquals(true, volume.hasOwnContent)
        assertEquals(1, volume.chapterCount)
        assertEquals(listOf(1), rows.filterIsInstance<DirectoryRow.ChapterRow>().map { it.index })
    }

    @Test
    fun volumeWithoutOpeningContentFallsBackToFirstChapter() {
        val chapters = listOf(
            chapter(30, "第一章 初见", 0, "第一卷 风起", 0),
            chapter(31, "第二章 同行", 1, "第一卷 风起", 0),
        )

        val volume = buildDirectoryRows(chapters, emptyMap()).single() as DirectoryRow.Volume

        assertEquals(0, volume.targetChapterIndex)
        assertEquals(2, volume.chapterCount)
        assertFalse(volume.hasOwnContent)
    }

    @Test
    fun stickyVolumeFollowsDirectoryViewportInsteadOfReadingChapter() {
        val chapters = listOf(
            chapter(40, "第一章", 0, "第一卷", 0),
            chapter(41, "第二章", 1, "第一卷", 0),
            chapter(50, "第三章", 2, "第二卷", 1),
            chapter(51, "第四章", 3, "第二卷", 1),
        )
        val rows = buildDirectoryRows(chapters, mapOf(0 to true, 1 to true))
        val secondVolumeChapter = rows.indexOfFirst {
            it is DirectoryRow.ChapterRow && it.index == 2
        }

        assertEquals(
            "第二卷",
            stickyVolumeFor(rows, secondVolumeChapter, 0)?.title,
        )
    }

    @Test
    fun volumeDoesNotDuplicateWhileItsHeaderIsRestingAtTop() {
        val chapters = listOf(
            chapter(60, "第一章", 0, "第一卷", 0),
            chapter(61, "第二章", 1, "第一卷", 0),
        )
        val rows = buildDirectoryRows(chapters, mapOf(0 to true))

        assertEquals(null, stickyVolumeFor(rows, 0, 0))
        assertEquals("第一卷", stickyVolumeFor(rows, 0, 25)?.title)
    }

    @Test
    fun standaloneChapterDoesNotInheritPreviousStickyVolume() {
        val rows = listOf(
            DirectoryRow.Volume(0, "第一卷", 1, setOf(70), 0, false),
            DirectoryRow.ChapterRow(0, 70, volumeIndex = 0),
            DirectoryRow.ChapterRow(1, 71),
        )

        assertEquals(null, stickyVolumeFor(rows, 2, 0))
    }

    @Test
    fun currentChapterAnchorLeavesRoomForFrozenVolumeWithoutCrossingHeader() {
        val chapters = listOf(
            chapter(80, "第一章", 0, "第一卷", 0),
            chapter(81, "第二章", 1, "第一卷", 0),
            chapter(82, "第三章", 2, "第一卷", 0),
            chapter(83, "第四章", 3, "第一卷", 0),
        )
        val rows = buildDirectoryRows(chapters, mapOf(0 to true))

        assertEquals(0, directoryChapterScrollAnchor(rows, 1))
        assertEquals(0, directoryChapterScrollAnchor(rows, 2))
        assertEquals(1, directoryChapterScrollAnchor(rows, 3))
        assertEquals(2, directoryChapterScrollAnchor(rows, 4))
    }

    private fun chapter(
        id: Long,
        title: String,
        index: Int,
        volumeTitle: String? = null,
        volumeIndex: Int? = null,
    ) = Chapter(
        id = id,
        bookUuid = "book",
        title = title,
        index = index,
        volumeTitle = volumeTitle,
        volumeIndex = volumeIndex,
    )
}
