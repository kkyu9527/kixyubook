package com.kixyu9527.kixyubook.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialSyncDecisionTest {
    @Test
    fun emptyLocalLibraryAndRestorableCloudBooksRequiresDecision() {
        assertTrue(InitialSyncDecision(localBookCount = 0, cloudBookCount = 3).requiresUserDecision())
    }

    @Test
    fun twoNonEmptyLibrariesUseNormalIncrementalMerge() {
        assertFalse(InitialSyncDecision(localBookCount = 2, cloudBookCount = 3).requiresUserDecision())
    }

    @Test
    fun twoEmptyLibrariesCanStartWithoutPrompt() {
        assertFalse(InitialSyncDecision(localBookCount = 0, cloudBookCount = 0).requiresUserDecision())
    }
}
