package com.kixyu9527.kixyubook.core.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DriveAuthorizationRetryTest {
    @Test fun a401RefreshesOnlyTheFailedRequest() = runBlocking<Unit> {
        val requests = mutableListOf<String>()
        var refreshes = 0
        val result = withRefreshingDriveToken(current = { "expired" }, refresh = {
            assertEquals("expired", it); refreshes++; "new"
        }) { token ->
            requests += token
            if (token == "expired") throw DriveHttpException(401, "expired")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("expired", "new"), requests)
        assertEquals(1, refreshes)
    }

    @Test fun aSecond401StopsInsteadOfSpinningForever() = runBlocking<Unit> {
        var requests = 0
        var refreshes = 0
        val failure = runCatching { withRefreshingDriveToken(current = { "expired" }, refresh = { refreshes++; "new" }) {
            requests++; throw DriveHttpException(401, "revoked")
        } }.exceptionOrNull()
        assertTrue(failure is DriveHttpException)
        assertEquals(2, requests)
        assertEquals(1, refreshes)
    }

    @Test fun nonAuthorizationErrorsDoNotRefreshOrRepeatTheRequest() = runBlocking<Unit> {
        for (status in listOf(403, 404, 429, 500)) {
            var refreshed = false
            var requests = 0
            runCatching { withRefreshingDriveToken(current = { "valid" }, refresh = { refreshed = true; "new" }) {
                requests++; throw DriveHttpException(status, "failed")
            } }
            assertFalse(refreshed)
            assertEquals(1, requests)
        }
    }

    @Test fun cancellationNeverTriggersAuthorizationOrRetry() = runBlocking<Unit> {
        var refreshed = false
        val cancelled = CancellationException("cancelled")
        val failure = runCatching { withRefreshingDriveToken(current = { "valid" }, refresh = { refreshed = true; "new" }) {
            throw cancelled
        } }.exceptionOrNull()
        assertSame(cancelled, failure)
        assertFalse(refreshed)
    }
}
