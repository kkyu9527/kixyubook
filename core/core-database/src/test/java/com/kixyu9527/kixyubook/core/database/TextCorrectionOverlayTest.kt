package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.model.TextCorrection
import com.kixyu9527.kixyubook.core.common.model.TextCorrectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TextCorrectionOverlayTest {
    @Test
    fun `applies source anchored replacements from the end without shifting earlier offsets`() {
        val source = "甲错误乙错误丙"
        val first = correction(start = 1, end = 3, exact = "错误", replacement = "正确")
        val second = correction(start = 4, end = 6, exact = "错误", replacement = "修正")

        assertEquals("甲正确乙修正丙", applyCorrections(source, listOf(first, second)))
    }

    @Test
    fun `does not apply conflict or stale source anchors`() {
        val source = "原始段落"
        val conflict = correction(0, 2, "原始", "纠正").copy(status = TextCorrectionStatus.CONFLICT)
        val stale = correction(0, 2, "别的", "纠正")

        assertEquals(source, applyCorrections(source, listOf(conflict, stale)))
    }

    private fun correction(
        start: Int,
        end: Int,
        exact: String,
        replacement: String,
    ) = TextCorrection(
        uuid = "$start-$end-$replacement",
        bookUuid = "book",
        sourceContentHash = "hash",
        chapterKey = "chapter",
        chapterIndex = 0,
        paragraphIndex = 0,
        startOffset = start,
        endOffset = end,
        exactText = exact,
        prefixText = "",
        suffixText = "",
        replacementText = replacement,
        createdTime = 1,
        updatedTime = 1,
    )
}
