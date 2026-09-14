package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.database.entity.BookmarkRow
import com.kixyu9527.kixyubook.core.database.entity.ParagraphEntity
import com.kixyu9527.kixyubook.core.database.entity.ReadingProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReparsedProgressMigrationTest {
    private fun progress() = ReadingProgressEntity(
        bookUuid = "book",
        chapterId = 10,
        position = 0,
        offset = 0,
        updatedTime = 1,
        fraction = 0f,
        chapterKey = "old-key",
        paragraphIndex = 0,
        charOffset = 0,
    )

    @Test
    fun movedParagraphKeepsTheReaderVisibleAnchorConsistent() {
        val migrated = migrateReparsedProgress(
            progress = progress(),
            previousChapterIndex = mapOf(10L to 0),
            chapterIds = listOf(100L),
            chapterKeys = listOf("new-key"),
            paragraphsByChapter = mapOf(
                100L to listOf(
                    ParagraphEntity(chapterId = 100L, paragraphIndex = 0, text = "新增段"),
                    ParagraphEntity(chapterId = 100L, paragraphIndex = 1, text = "目标段"),
                ),
            ),
            previousProgressText = "目标段",
        )

        assertEquals(100L, migrated.chapterId)
        assertEquals(1, migrated.position)
        assertEquals(1, migrated.paragraphIndex)
        assertEquals(0, migrated.charOffset)
        assertEquals("new-key", migrated.chapterKey)
    }

    @Test
    fun matchedParagraphKeepsTheIntraParagraphOffset() {
        val longParagraph = "字".repeat(2_000)
        val migrated = migrateReparsedProgress(
            progress = progress().copy(paragraphIndex = 0, charOffset = 1_500),
            previousChapterIndex = mapOf(10L to 0),
            chapterIds = listOf(100L),
            chapterKeys = listOf("new-key"),
            paragraphsByChapter = mapOf(
                100L to listOf(ParagraphEntity(chapterId = 100L, paragraphIndex = 0, text = longParagraph)),
            ),
            previousProgressText = longParagraph,
        )

        assertEquals(1_500, migrated.charOffset)
        assertEquals(1_500, migrated.offset)
        assertEquals(0, migrated.paragraphIndex)
    }

    @Test
    fun matchedParagraphClampsAnOffsetThatNoLongerFits() {
        val longParagraph = "字".repeat(600)
        val migrated = migrateReparsedProgress(
            progress = progress().copy(paragraphIndex = 0, charOffset = 9_999),
            previousChapterIndex = mapOf(10L to 0),
            chapterIds = listOf(100L),
            chapterKeys = listOf("new-key"),
            paragraphsByChapter = mapOf(
                100L to listOf(ParagraphEntity(chapterId = 100L, paragraphIndex = 0, text = longParagraph)),
            ),
            previousProgressText = longParagraph,
        )

        assertEquals(600, migrated.charOffset)
    }

    @Test
    fun missingAnchorFallsBackToTheClampedLegacyPositionAndResetsTheOffset() {
        val migrated = migrateReparsedProgress(
            progress = progress().copy(position = 42, charOffset = 1_500),
            previousChapterIndex = mapOf(10L to 0),
            chapterIds = listOf(100L),
            chapterKeys = listOf("new-key"),
            paragraphsByChapter = mapOf(
                100L to listOf(ParagraphEntity(chapterId = 100L, paragraphIndex = 0, text = "唯一段")),
            ),
            previousProgressText = null,
        )

        assertEquals(0, migrated.paragraphIndex)
        assertEquals(0, migrated.charOffset)
        assertEquals(0, migrated.offset)
        assertEquals("new-key", migrated.chapterKey)
    }

    @Test
    fun reparsedBookmarkReAnchorsByTextAndCarriesTheNewChapterKey() {
        val anchoredText = "被书签的段落"
        val migrated = migrateReparsedBookmark(
            bookmark = BookmarkRow(
                uuid = "bm",
                bookUuid = "book",
                chapterId = 10,
                chapterTitle = "旧标题",
                chapterIndex = 0,
                position = 0,
                preview = "被书签…",
                createdTime = 1,
                chapterKey = "old-key",
            ),
            previousChapterIndex = mapOf(10L to 0),
            previousParagraphText = anchoredText,
            chapterIds = listOf(100L),
            chapterKeys = listOf("new-key"),
            paragraphsByChapter = mapOf(
                100L to listOf(
                    ParagraphEntity(chapterId = 100L, paragraphIndex = 0, text = "新增段"),
                    ParagraphEntity(chapterId = 100L, paragraphIndex = 1, text = anchoredText),
                ),
            ),
        )!!

        assertEquals(1, migrated.position)
        assertEquals(100L, migrated.chapterId)
        assertEquals("new-key", migrated.chapterKey)
    }

    @Test
    fun reparsedBookmarkFollowsItsAnchorIntoTheNextChapterWhenBoundariesShift() {
        // The reparse recognised a new front chapter, so the target paragraph moved to the next
        // chapter while its old index-mapped chapter no longer contains it.
        val migrated = migrateReparsedBookmark(
            bookmark = BookmarkRow(
                uuid = "bm",
                bookUuid = "book",
                chapterId = 10,
                chapterTitle = "旧章",
                chapterIndex = 0,
                position = 0,
                preview = "锚点…",
                createdTime = 1,
                chapterKey = "old-shifted-key",
            ),
            previousChapterIndex = mapOf(10L to 0),
            previousParagraphText = "目标正文",
            chapterIds = listOf(100L, 200L),
            chapterKeys = listOf("new-front", "new-body"),
            paragraphsByChapter = mapOf(
                100L to listOf(ParagraphEntity(chapterId = 100L, paragraphIndex = 0, text = "新增前置章")),
                200L to listOf(ParagraphEntity(chapterId = 200L, paragraphIndex = 0, text = "目标正文")),
            ),
        )!!

        assertEquals(200L, migrated.chapterId)
        assertEquals(0, migrated.position)
        assertEquals("new-body", migrated.chapterKey)
    }

    @Test
    fun reparsedBookmarkWithAGoneAnchorIsNotBoundToAnotherChapter() {
        val migrated = migrateReparsedBookmark(
            bookmark = BookmarkRow(
                uuid = "bm",
                bookUuid = "book",
                chapterId = 10,
                chapterTitle = "旧章",
                chapterIndex = 0,
                position = 0,
                preview = "锚点…",
                createdTime = 1,
                chapterKey = "gone-key",
            ),
            previousChapterIndex = mapOf(10L to 0),
            previousParagraphText = "已删除的正文",
            chapterIds = listOf(100L, 200L),
            chapterKeys = listOf("new-a", "new-b"),
            paragraphsByChapter = mapOf(
                100L to listOf(ParagraphEntity(chapterId = 100L, paragraphIndex = 0, text = "第一章")),
                200L to listOf(ParagraphEntity(chapterId = 200L, paragraphIndex = 0, text = "第二章")),
            ),
        )

        assertEquals(null, migrated)
    }
}
