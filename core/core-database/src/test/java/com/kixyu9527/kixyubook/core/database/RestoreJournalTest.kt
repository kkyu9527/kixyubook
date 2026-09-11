package com.kixyu9527.kixyubook.core.database

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreJournalTest {
    @Test fun committedCleanupFailureNeverBlocksHealthyDatabaseOrRollsItBack() {
        val root = folder.newFolder()
        val targets = fixture(root)
        val journalRoot = File(root, "journal")
        val replacement = File(root, "replacement").apply { writeText("committed database") }
        val journal = RestoreJournal(journalRoot, targets, checkpoint = {
            if (it == "cleanup") throw java.io.IOException("cleanup failed")
        })
        journal.prepare(mapOf("database" to replacement)); journal.install(); journal.commit()
        journal.recoverBeforeOpen()
        assertEquals("committed database", targets.getValue("database").readText())
        assertTrue(File(journalRoot, "committed").exists())
        RestoreJournal(journalRoot, targets).recoverBeforeOpen()
        assertFalse(journalRoot.exists())
    }
    @Test fun allPreferenceStoresRollbackTogether() {
        val root = folder.newFolder()
        val targets = fixture(root) + mapOf(
            "library" to File(root, "library").apply { writeText("old library") },
            "reminder" to File(root, "reminder").apply { writeText("old reminder") },
        )
        val keys = setOf("preferences", "library", "reminder")
        val journalRoot = File(root, "journal")
        RestoreJournal(journalRoot, targets, keys).apply {
            prepare(emptyMap())
            keys.forEach { targets.getValue(it).writeText("new") }
            install()
        }
        RestoreJournal(journalRoot, targets, keys).recoverBeforeOpen()
        assertEquals("old settings", targets.getValue("preferences").readText())
        assertEquals("old library", targets.getValue("library").readText())
        assertEquals("old reminder", targets.getValue("reminder").readText())
    }
    @get:Rule val folder = TemporaryFolder()
    private class ProcessDeath : Error()

    @Test fun stagingInterruptionDoesNotDeleteWalCreatedByStillLiveDatabase() {
        val root = folder.newFolder()
        val database = File(root, "db").apply { writeText("old database") }
        val wal = File(root, "db-wal")
        val replacement = File(root, "replacement").apply { writeText("new database") }
        val targets = mapOf("database" to database, "wal" to wal)
        val journalRoot = File(root, "journal")
        RestoreJournal(journalRoot, targets).prepare(mapOf("database" to replacement))
        wal.writeText("new committed local changes")
        RestoreJournal(journalRoot, targets).recoverBeforeOpen()
        assertEquals("old database", database.readText())
        assertEquals("new committed local changes", wal.readText())
    }

    @Test fun everyInstallInterruptionRestoresDatabaseAssetsAndPreferences() {
        for (point in listOf("prepared", "old:database", "new:database", "old:books", "new:books")) {
            val root = folder.newFolder()
            val targets = fixture(root)
            val replacement = File(root, "replacement").apply { writeText("new database") }
            val journalRoot = File(root, "journal")
            val interrupted = RestoreJournal(journalRoot, targets, checkpoint = {
                if (it == point) throw ProcessDeath()
            })
            try {
                interrupted.prepare(mapOf("database" to replacement))
                targets.getValue("preferences").writeText("new settings")
                interrupted.install()
                fail("Expected simulated process death at $point")
            } catch (_: ProcessDeath) { }
            RestoreJournal(journalRoot, targets).recoverBeforeOpen()
            assertEquals(point, "old database", targets.getValue("database").readText())
            assertEquals(point, "original book", File(targets.getValue("books"), "book.epub").readText())
            assertEquals(point, "old settings", targets.getValue("preferences").readText())
            assertFalse(journalRoot.exists())
        }
    }

    @Test fun recoveryItselfCanBeInterruptedAndRetried() {
        val root = folder.newFolder()
        val targets = fixture(root)
        val journalRoot = File(root, "journal")
        val replacement = File(root, "replacement").apply { writeText("new") }
        RestoreJournal(journalRoot, targets).apply {
            prepare(mapOf("database" to replacement))
            install()
        }
        try {
            RestoreJournal(journalRoot, targets, checkpoint = {
                if (it == "rollback:database") throw ProcessDeath()
            }).recoverBeforeOpen()
            fail()
        } catch (_: ProcessDeath) { }
        RestoreJournal(journalRoot, targets).recoverBeforeOpen()
        assertEquals("old database", targets.getValue("database").readText())
        assertEquals("original book", File(targets.getValue("books"), "book.epub").readText())
    }

    @Test fun successfulCommitIsNeverRolledBack() {
        val root = folder.newFolder()
        val targets = fixture(root)
        val replacement = File(root, "replacement").apply { writeText("new database") }
        val journalRoot = File(root, "journal")
        RestoreJournal(journalRoot, targets).apply {
            prepare(mapOf("database" to replacement))
            targets.getValue("preferences").writeText("new settings")
            install()
            commit()
        }
        RestoreJournal(journalRoot, targets).recoverBeforeOpen()
        assertEquals("new database", targets.getValue("database").readText())
        assertEquals("new settings", targets.getValue("preferences").readText())
        assertFalse(targets.getValue("books").exists())
    }

    @Test fun originallyAbsentFilesAreRemovedDuringRollback() {
        val root = folder.newFolder()
        val targets = mapOf("database" to File(root, "db"), "preferences" to File(root, "preferences"))
        val replacement = File(root, "replacement").apply { writeText("new") }
        val journalRoot = File(root, "journal")
        RestoreJournal(journalRoot, targets).apply {
            prepare(mapOf("database" to replacement))
            targets.getValue("preferences").writeText("new settings")
            install()
        }
        RestoreJournal(journalRoot, targets).recoverBeforeOpen()
        assertTrue(targets.values.none(File::exists))
    }

    private fun fixture(root: File) = linkedMapOf(
        "database" to File(root, "db").apply { writeText("old database") },
        "books" to File(root, "books").apply {
            mkdirs(); File(this, "book.epub").writeText("original book")
        },
        "preferences" to File(root, "preferences").apply { writeText("old settings") },
    )
}
