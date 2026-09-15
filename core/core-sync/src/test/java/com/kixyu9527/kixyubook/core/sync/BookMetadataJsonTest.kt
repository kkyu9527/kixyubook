package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.database.entity.BookEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookMetadataJsonTest {
    @Test
    fun importedNameSortAndSeriesSurviveTheCloudRoundTrip() {
        val entity = BookEntity(
            uuid = "book",
            title = "三体",
            author = "刘慈欣",
            description = "简介",
            coverPath = null,
            format = "EPUB",
            originalPath = "google-drive://book",
            storagePath = "/stored.epub",
            createdTime = 1,
            contentHash = "hash",
            category = "未分类",
            originalDisplayName = "《三体》作者：刘慈欣.epub",
            titleSort = "san ti",
            seriesName = "地球往事",
            seriesIndex = 2.0,
        )

        val restored = parseBook(bookMetadataJson(entity))

        assertEquals("《三体》作者：刘慈欣.epub", restored.originalDisplayName)
        assertEquals("san ti", restored.titleSort)
        assertEquals("地球往事", restored.seriesName)
        assertEquals(2.0, restored.seriesIndex!!, 0.0)
    }

    @Test
    fun manualEditOwnershipTravelsWithTheValues() {
        val entity = BookEntity(
            uuid = "book",
            title = "三体",
            author = "刘慈欣",
            description = "简介",
            coverPath = null,
            format = "TXT",
            originalPath = "/tmp/a.txt",
            storagePath = "/stored.txt",
            createdTime = 1,
            contentHash = "hash",
            category = "未分类",
            userEditedAuthor = true,
        )

        val payload = bookMetadataJson(entity)
        val restored = parseBook(payload)

        assertEquals(3, payload.getInt("schema"))
        assertEquals(true, restored.metadataOwnership!!.author)
        assertEquals(false, restored.metadataOwnership!!.title)
        assertEquals(false, restored.metadataOwnership!!.description)
    }

    @Test
    fun anOldPayloadWithoutOwnershipKeepsTheLocalFlags() {
        val legacy = JSONObject()
            .put("schema", 2)
            .put("uuid", "book")
            .put("title", "三体")
            .put("contentHash", "hash")

        assertNull(parseBook(legacy).metadataOwnership)
    }
}
