package com.kixyu9527.kixyubook.core.sync

/** Refresh once per failed request, not once per hours-long library synchronization. */
internal suspend fun <T> withRefreshingDriveToken(
    current: suspend () -> String,
    refresh: suspend (String) -> String,
    request: suspend (String) -> T,
): T {
    val token = current()
    return try { request(token) } catch (error: DriveHttpException) {
        if (error.statusCode != 401) throw error
        // A second 401 is real authorization loss; never turn it into an endless retry loop.
        request(refresh(token))
    }
}
