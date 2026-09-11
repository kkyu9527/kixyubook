package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ReaderProgressWriterTest {
    private fun progress(time: Long) = ReadingProgress(
        "book", 12, time.toInt(), 9, time, chapterKey = "stable-chapter",
        paragraphIndex = time.toInt(), charOffset = 9,
    )

    @Test fun lockAndOwnerCancellationDrainLastPositionWithoutWaitingForNetwork() = runBlocking {
        withTimeout(30_000) {
            val releaseDisk = CompletableDeferred<Unit>()
            val writes = mutableListOf<ReadingProgress>()
            val owner = CoroutineScope(Job() + Dispatchers.Unconfined)
            val writer = ReaderProgressWriter(
                save = { releaseDisk.await(); writes += it },
                onFailure = { throw AssertionError(it) }, dispatcher = Dispatchers.Unconfined,
            )
            try {
                owner.launch { writer.submit(progress(1)) }
                assertNull(writer.persisted) // queued is not acknowledged
                owner.launch { writer.submit(progress(2)); writer.submit(progress(3)) }
                writer.checkpoint() // ON_PAUSE / ON_STOP, same timestamp, not a new user action
                owner.cancel()
                writer.close() // ViewModel cleared: drain, do not cancel pending local I/O
                releaseDisk.complete(Unit)
                writer.awaitClosed()
                assertEquals(listOf(progress(1), progress(3)), writes)
                assertEquals(progress(3), writer.persisted)
            } finally {
                releaseDisk.complete(Unit)
                owner.cancel()
                writer.close()
            }
        }
    }

    @Test fun failedWriteIsNotAcknowledgedAndLifecycleCheckpointRetriesIt() = runBlocking {
        var fail = true
        val failures = mutableListOf<Throwable>()
        val writes = mutableListOf<ReadingProgress>()
        val writer = ReaderProgressWriter(
            save = { if (fail) throw IOException("disk unavailable"); writes += it },
            onFailure = failures::add, dispatcher = Dispatchers.Unconfined,
        )
        try {
            writer.submit(progress(5))
            assertNull(writer.persisted)
            assertEquals(1, failures.size)
            fail = false
            writer.checkpoint()
            assertEquals(progress(5), writer.persisted)
            writer.submit(progress(3)) // delayed older callback must not rewind saved progress
            writer.checkpoint()
            assertEquals(listOf(progress(5)), writes)
        } finally { writer.close(); writer.awaitClosed() }
    }

    @Test fun repeatedCheckpointWithoutReadingDoesNotInventNewProgress() = runBlocking {
        val writes = mutableListOf<ReadingProgress>()
        val writer = ReaderProgressWriter(
            save = { writes += it }, onFailure = { throw AssertionError(it) },
            dispatcher = Dispatchers.Unconfined,
        )
        try {
            assertTrue(writer.flush())
            assertTrue(writes.isEmpty())
            writer.submit(progress(7))
            repeat(3) { writer.checkpoint(); assertTrue(writer.flush()) }
            assertEquals(listOf(progress(7)), writes)
        } finally { writer.close(); writer.awaitClosed() }
    }
}
