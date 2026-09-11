package com.kixyu9527.kixyubook.core.common.operation

import java.io.IOException
import java.io.FileNotFoundException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LatestOperationWriterTest {
    @Test fun leavingScreenDuringDebounceStillPersistsLatestSetting() = runTest {
        val job = kotlinx.coroutines.SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val scope = kotlinx.coroutines.CoroutineScope(coroutineContext + job)
        val writer = LatestOperationWriter(scope, UserOperationController(scope))
        var saved = 0
        writer.submit("size") { saved = 25 }
        writer.submit("size") { saved = 27 }
        job.cancel()
        advanceUntilIdle()
        assertEquals(27, saved)
    }
    @Test fun rapidUpdatesKeepFinalValueOfEachField() = runTest {
        val controller = UserOperationController(this)
        val writer = LatestOperationWriter(this, controller)
        val saved = mutableListOf<String>()
        repeat(100) { index -> writer.submit("size") { saved += "size=$index" } }
        writer.submit("margin") { saved += "margin=30" }
        advanceUntilIdle()
        assertEquals(listOf("size=99", "margin=30"), saved)
    }

    @Test fun editingSupersedesFailedSettingInsteadOfRetryingOldValue() = runTest {
        val controller = UserOperationController(this)
        val writer = LatestOperationWriter(this, controller)
        writer.submit("size") { throw IOException() }
        advanceUntilIdle()
        val failed = controller.state.value.attempt
        var saved = 0
        writer.submit("size") { saved = 25 }
        controller.retry(failed)
        advanceUntilIdle()
        assertEquals(25, saved); assertTrue(controller.state.value.succeeded)
    }

    @Test fun failuresDistinguishUserActionFromRetryableIo() {
        assertEquals(OperationFailure.STORAGE_FULL, OperationFailure.from(IOException("ENOSPC")))
        assertEquals(OperationFailure.PERMISSION, OperationFailure.from(SecurityException()))
        assertEquals(OperationFailure.SOURCE_MISSING, OperationFailure.from(FileNotFoundException()))
        assertEquals(OperationFailure.INVALID_INPUT, OperationFailure.from(IllegalArgumentException()))
        assertTrue(OperationFailure.from(IOException()).retryable)
    }
}
