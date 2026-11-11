package com.kixyu9527.kixyubook.core.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CloudForegroundRequestsTest {
    @Test fun fullSyncYieldsWhileABookIsWaitingButResumesAfterwards() = runBlocking<Unit> {
        val requests = CloudForegroundRequests()
        requests.checkpoint()
        requests.prioritize {
            assertTrue(runCatching { requests.checkpoint() }.exceptionOrNull() is CloudSyncYieldedException)
            requests.prioritize { assertTrue(runCatching { requests.checkpoint() }.isFailure) }
            assertTrue(runCatching { requests.checkpoint() }.isFailure)
        }
        requests.checkpoint()
    }

    @Test fun failedOrCancelledDownloadsCannotPermanentlyBlockFullSync() = runBlocking<Unit> {
        val requests = CloudForegroundRequests()
        for (failure in listOf(IllegalStateException("network"), CancellationException("cancel"))) {
            assertSame(failure, runCatching { requests.prioritize { throw failure } }.exceptionOrNull())
            requests.checkpoint()
        }
    }
}
