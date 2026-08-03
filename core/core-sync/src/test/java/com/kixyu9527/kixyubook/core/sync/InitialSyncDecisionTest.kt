package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialSyncDecisionTest {
    @Test
    fun emptyLocalLibraryAndRestorableCloudBooksRestoresAutomatically() {
        assertTrue(InitialSyncDecision(localBookCount = 0, cloudBookCount = 3).shouldRestoreFromCloud())
    }

    @Test
    fun twoNonEmptyLibrariesUseNormalIncrementalMerge() {
        assertFalse(InitialSyncDecision(localBookCount = 2, cloudBookCount = 3).shouldRestoreFromCloud())
    }

    @Test
    fun twoEmptyLibrariesCanStartWithoutPrompt() {
        assertFalse(InitialSyncDecision(localBookCount = 0, cloudBookCount = 0).shouldRestoreFromCloud())
    }

    @Test
    fun newerLocalDataAloneIsNotAConflict() {
        assertFalse(InitialSyncDecision(localBookCount = 2, cloudBookCount = 2).requiresUserDecision())
    }

    @Test
    fun provenDoubleModificationRequiresAChoice() {
        val decision = InitialSyncDecision(
            localBookCount = 2,
            cloudBookCount = 2,
            conflicts = listOf(InitialSyncConflict(SyncEntityType.BOOK, "book-id")),
        )
        assertTrue(decision.requiresUserDecision())
    }
}
