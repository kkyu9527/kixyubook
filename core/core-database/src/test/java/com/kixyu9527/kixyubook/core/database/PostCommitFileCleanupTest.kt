package com.kixyu9527.kixyubook.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostCommitFileCleanupTest {
    private fun database(): KixyuDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication() as Context,
        KixyuDatabase::class.java,
    ).build()

    @Test
    fun deferredCleanupRunsOnlyAfterTheTransactionCommits() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            var cleaned = false
            runWithPostCommitFileCleanup {
                database.withTransaction {
                    deferFileCleanup { cleaned = true }
                    assertFalse("cleanup must not run inside the open transaction", cleaned)
                }
            }
            assertTrue("cleanup must run after the commit", cleaned)
        } finally {
            database.close()
        }
    }

    @Test
    fun aRolledBackTransactionDropsItsDeferredCleanup() = runBlocking(Dispatchers.IO) {
        val database = database()
        try {
            var cleaned = false
            runCatching {
                runWithPostCommitFileCleanup {
                    database.withTransaction {
                        deferFileCleanup { cleaned = true }
                        error("rollback")
                    }
                }
            }
            assertFalse("rolled-back rows must keep their files", cleaned)
        } finally {
            database.close()
        }
    }

    @Test
    fun cleanupWithoutABatchRunsImmediately() = runBlocking(Dispatchers.IO) {
        var cleaned = false
        deferFileCleanup { cleaned = true }
        assertTrue(cleaned)
    }
}
