package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingLocalChangeTest {
    private fun mutation(type: String, id: String) =
        SyncOutboxEntity("m-$type-$id", type, id, "UPSERT", 1, 1, "device")

    @Test fun pendingSettingsBlockTheRemoteSettingsSnapshot() {
        val pending = listOf(mutation("SETTINGS", "global"))
        assertTrue(hasPendingLocalChange("settings/global", pending))
        assertFalse(hasPendingLocalChange("progress/book", pending))
    }

    @Test fun pendingBookBlocksBothItsObjects() {
        val pending = listOf(mutation("BOOK", "book"))
        assertTrue(hasPendingLocalChange("books/book/metadata", pending))
        assertTrue(hasPendingLocalChange("books/book/source", pending))
        assertFalse(hasPendingLocalChange("books/other/metadata", pending))
    }

    @Test fun pendingBookmarkBlocksItsCollectionObject() {
        assertTrue(hasPendingLocalChange("bookmarks/book", listOf(mutation("BOOKMARKS", "book"))))
        assertFalse(hasPendingLocalChange("bookmarks/other", listOf(mutation("BOOKMARKS", "book"))))
    }

    @Test fun noPendingChangeNeverBlocks() {
        assertFalse(hasPendingLocalChange("settings/global", emptyList()))
    }

    @Test fun skippedPriorityPullKeepsTheQueuedEditAndTheRemoteBaseline() = kotlinx.coroutines.runBlocking {
        val removed = mutableListOf<List<String>>()
        var remembered = false
        acknowledgePriorityPull(
            applied = false,
            localMutation = mutation("BOOKMARKS", "book"),
            removeOutbox = { removed += it },
            rememberRemote = { remembered = true },
        )
        assertTrue(removed.isEmpty())
        assertFalse(remembered)
    }

    @Test fun appliedPriorityPullDropsTheQueuedEdit() = kotlinx.coroutines.runBlocking {
        val removed = mutableListOf<List<String>>()
        var remembered = false
        acknowledgePriorityPull(
            applied = true,
            localMutation = mutation("BOOKMARKS", "book"),
            removeOutbox = { removed += it },
            rememberRemote = { remembered = true },
        )
        assertEquals(listOf(listOf("m-BOOKMARKS-book")), removed)
        assertTrue(remembered)
    }
}
