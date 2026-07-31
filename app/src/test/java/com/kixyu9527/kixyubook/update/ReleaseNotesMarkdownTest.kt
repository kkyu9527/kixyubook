package com.kixyu9527.kixyubook.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesMarkdownTest {
    @Test
    fun headingsAndListsBecomeStructuredBlocks() {
        val blocks = parseReleaseNotes(
            """
            # Kixyu Book 1.7.0

            ## 新增功能
            - 自动检查更新
            - [x] 支持后台下载
            """.trimIndent(),
        )

        assertEquals(ReleaseNoteBlock.Heading(1, "Kixyu Book 1.7.0"), blocks[0])
        assertEquals(ReleaseNoteBlock.Heading(2, "新增功能"), blocks[1])
        assertEquals(ReleaseNoteBlock.ListItem("•", "自动检查更新"), blocks[2])
        assertEquals(ReleaseNoteBlock.ListItem("✓", "支持后台下载"), blocks[3])
    }

    @Test
    fun paragraphsAndCodeBlocksPreserveContent() {
        val blocks = parseReleaseNotes(
            """
            第一行
            第二行

            ```kotlin
            val version = "1.7.0"
            ```
            """.trimIndent(),
        )

        assertEquals(ReleaseNoteBlock.Paragraph("第一行 第二行"), blocks[0])
        assertTrue((blocks[1] as ReleaseNoteBlock.Code).text.contains("val version"))
    }
}
