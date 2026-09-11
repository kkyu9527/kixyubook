package com.kixyu9527.kixyubook.core.common.operation

import com.kixyu9527.kixyubook.core.common.repository.StartupRecoveryState
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class StartupRecoveryStateTest {
    @After fun reset() { StartupRecoveryState.recover {} }
    @Test fun failedRollbackBlocksDataProvidersUntilSuccessfulRetry() {
        StartupRecoveryState.recover { throw java.io.IOException("rollback blocked") }
        assertNotNull(StartupRecoveryState.failure)
        assertTrue(runCatching { StartupRecoveryState.requireReady() }.isFailure)
        StartupRecoveryState.recover {}
        StartupRecoveryState.requireReady()
        assertNull(StartupRecoveryState.failure)
    }
}
