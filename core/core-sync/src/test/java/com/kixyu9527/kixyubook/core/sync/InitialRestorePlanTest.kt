package com.kixyu9527.kixyubook.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialRestorePlanTest {
    @Test
    fun fiveMostRecentlyReadBooksAreRestoredFirst() {
        val books = (1..8).map { "book-$it" }
        val remote = books.associate { uuid ->
            val recency = uuid.substringAfterLast('-').toLong()
            "progress/$uuid" to driveObject("progress/$uuid", recency)
        }

        val plan = planInitialRestore(books, remote)

        assertEquals(listOf("book-8", "book-7", "book-6", "book-5", "book-4"), plan.priorityBookUuids)
        assertEquals(listOf("book-1", "book-2", "book-3"), plan.remainingBookUuids)
    }

    @Test
    fun unreadBooksRemainInStableShelfOrder() {
        val plan = planInitialRestore(
            bookUuids = listOf("unread-a", "recent", "unread-b", "recent"),
            remote = mapOf("progress/recent" to driveObject("progress/recent", 100)),
        )

        assertEquals(listOf("recent"), plan.priorityBookUuids)
        assertEquals(listOf("unread-a", "unread-b"), plan.remainingBookUuids)
    }

    private fun driveObject(key: String, modifiedAt: Long) = DriveObject(
        id = key,
        name = "$key.json",
        objectKey = key,
        mimeType = "application/json",
        modifiedAt = modifiedAt,
        version = modifiedAt,
        size = 0,
        md5 = null,
    )
}
