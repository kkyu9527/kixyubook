package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.Chapter
import com.kixyu9527.kixyubook.core.common.model.TextCorrection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderCorrectionRefreshTest {
    @Test
    fun deletingCorrectionInvalidatesItsDirectoryPosition() {
        val chapters = listOf(
            chapter(id = 10, index = 4, key = "cover"),
            chapter(id = 20, index = 12, key = "chapter-12"),
        )

        val affected = changedCorrectionChapterPositions(
            previous = listOf(correction(chapterIndex = 12, chapterKey = "chapter-12")),
            current = emptyList(),
            chapters = chapters,
        )

        assertEquals(setOf(1), affected)
    }

    @Test
    fun unchangedCorrectionDoesNotRebuildReader() {
        val value = correction(chapterIndex = 12, chapterKey = "chapter-12")

        assertEquals(
            emptySet<Int>(),
            changedCorrectionChapterPositions(
                previous = listOf(value),
                current = listOf(value),
                chapters = listOf(chapter(id = 20, index = 12, key = "chapter-12")),
            ),
        )
    }

    @Test
    fun differenceMasksKeepUnchangedCharactersNeutralAcrossMultipleEdits() {
        val difference = correctionDifferenceMasks("甲乙丙丁戊", "甲新丙改戊")

        assertArrayEquals(
            booleanArrayOf(false, true, false, true, false),
            difference.original,
        )
        assertArrayEquals(
            booleanArrayOf(false, true, false, true, false),
            difference.replacement,
        )
    }

    @Test
    fun differenceMasksDistinguishInsertionAndDeletion() {
        val insertion = correctionDifferenceMasks("甲乙", "甲新增乙")
        val deletion = correctionDifferenceMasks("甲新增乙", "甲乙")

        assertArrayEquals(booleanArrayOf(false, false), insertion.original)
        assertArrayEquals(booleanArrayOf(false, true, true, false), insertion.replacement)
        assertArrayEquals(booleanArrayOf(false, true, true, false), deletion.original)
        assertArrayEquals(booleanArrayOf(false, false), deletion.replacement)
    }

    private fun chapter(id: Long, index: Int, key: String) = Chapter(
        id = id,
        bookUuid = "book",
        title = key,
        index = index,
        chapterKey = key,
    )

    private fun correction(chapterIndex: Int, chapterKey: String) = TextCorrection(
        uuid = "correction",
        bookUuid = "book",
        sourceContentHash = "hash",
        chapterKey = chapterKey,
        chapterIndex = chapterIndex,
        paragraphIndex = 3,
        startOffset = 0,
        endOffset = 2,
        exactText = "原文",
        prefixText = "",
        suffixText = "",
        replacementText = "纠正文",
        createdTime = 1,
        updatedTime = 1,
    )
}
