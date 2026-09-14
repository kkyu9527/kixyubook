package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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

    @Test fun aFailedSettingsStoreRollsBackTheStoresAlreadyWritten() = kotlinx.coroutines.runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("reminder store down")
        try {
            applySettingsWithRollback(
                applyReader = { events += "applyReader" },
                applyLibrary = { events += "applyLibrary" },
                applyReminder = { events += "applyReminder"; throw failure },
                hasLibrary = true,
                hasReminder = true,
                rollbackReader = { events += "rollbackReader" },
                rollbackLibrary = { events += "rollbackLibrary" },
                rollbackReminder = { events += "rollbackReminder" },
            )
            error("expected the reminder failure to propagate")
        } catch (error: IllegalStateException) {
            assertEquals(failure, error)
        }
        // The reminder group may already have saved its config before scheduling failed, so it is
        // rolled back too; every group follows the same "mark before the first write" rule.
        assertEquals(
            listOf(
                "applyReader",
                "applyLibrary",
                "applyReminder",
                "rollbackReminder",
                "rollbackLibrary",
                "rollbackReader",
            ),
            events,
        )
    }

    @Test fun omittedSettingsStoresAreNotAppliedOrRolledBack() = kotlinx.coroutines.runBlocking {
        val events = mutableListOf<String>()
        applySettingsWithRollback(
            applyReader = { events += "applyReader" },
            applyLibrary = { events += "applyLibrary" },
            applyReminder = { events += "applyReminder" },
            hasLibrary = false,
            hasReminder = false,
            rollbackReader = { events += "rollbackReader" },
            rollbackLibrary = { events += "rollbackLibrary" },
            rollbackReminder = { events += "rollbackReminder" },
        )
        assertEquals(listOf("applyReader"), events)
    }

    @Test fun aPartiallyAppliedReaderGroupIsRolledBack() = kotlinx.coroutines.runBlocking {
        val state = mutableListOf("initial")
        try {
            applySettingsWithRollback(
                applyReader = {
                    // Font size already saved before the goal write fails inside the same group.
                    state += "reader-settings"
                    throw IllegalStateException("goal write failed")
                },
                applyLibrary = {},
                applyReminder = {},
                hasLibrary = false,
                hasReminder = false,
                rollbackReader = { state.clear(); state.add("initial") },
                rollbackLibrary = {},
                rollbackReminder = {},
            )
            error("expected the reader failure to propagate")
        } catch (_: IllegalStateException) {
        }
        assertEquals(listOf("initial"), state)
    }

    @Test fun settingsRollbackSurvivesCancellation() = kotlinx.coroutines.runBlocking {
        var rolledBack = false
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            applySettingsWithRollback(
                applyReader = {
                    started.complete(Unit)
                    release.await()
                },
                applyLibrary = {},
                applyReminder = {},
                hasLibrary = false,
                hasReminder = false,
                rollbackReader = { rolledBack = true },
                rollbackLibrary = {},
                rollbackReminder = {},
            )
        }
        started.await()
        job.cancelAndJoin()
        assertTrue("rollback must run even when the caller was cancelled", rolledBack)
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
