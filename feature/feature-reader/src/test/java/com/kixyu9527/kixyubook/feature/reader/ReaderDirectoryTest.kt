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
