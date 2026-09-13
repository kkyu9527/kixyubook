package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.repository.SettingsWriteGate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsWriteGuardTest {
    private suspend fun markLocalWrite() = SettingsWriteGate.mutex.withLock { SettingsWriteGate.markLocalWrite() }

    @Test fun localWriteWhileTheFileDownloadsKeepsTheLocalSettings() = runBlocking {
        val start = SettingsWriteGate.currentGeneration()
        markLocalWrite()
        var applied = false
        val result = withSettingsWriteGuard(start, isLocallyPending = { false }) { applied = true }
        assertFalse(result)
        assertFalse(applied)
    }

    @Test fun persistedButUnregisteredWriteBlocksEvenWhenGenerationWasCapturedAfterIt() = runBlocking {
        val unregister = SettingsWriteGate.registerPersistedPendingCheck { true }
        try {
            val start = SettingsWriteGate.currentGeneration()
            var applied = false
            val result = withSettingsWriteGuard(start, isLocallyPending = { false }) { applied = true }
            assertFalse(result)
            assertFalse(applied)
        } finally {
            unregister()
        }
    }

    @Test fun pendingOutboxRowBlocksTheApply() = runBlocking {
        val start = SettingsWriteGate.currentGeneration()
        var applied = false
        val result = withSettingsWriteGuard(start, isLocallyPending = { true }) { applied = true }
        assertFalse(result)
        assertFalse(applied)
    }

    @Test fun unchangedGenerationAndNoPendingChangeAppliesTheRemoteSettings() = runBlocking {
        val start = SettingsWriteGate.currentGeneration()
        var applied = false
        val result = withSettingsWriteGuard(start, isLocallyPending = { false }) { applied = true }
        assertTrue(result)
        assertTrue(applied)
    }
}
