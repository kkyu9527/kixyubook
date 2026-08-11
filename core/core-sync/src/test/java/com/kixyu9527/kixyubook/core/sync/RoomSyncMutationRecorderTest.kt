package com.kixyu9527.kixyubook.core.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSyncMutationRecorderTest {
    @Test
    fun suppressionOnlyAffectsTheRemoteApplyCoroutine() = runBlocking {
        val remoteApplyStarted = CompletableDeferred<Unit>()
        val localMutationChecked = CompletableDeferred<Unit>()
        val unrelatedLocalMutation = async {
            remoteApplyStarted.await()
            val suppressed = isSyncMutationRecordingSuppressed()
            localMutationChecked.complete(Unit)
            suppressed
        }

        val remoteApplySuppressed = withoutSyncMutationRecording {
            remoteApplyStarted.complete(Unit)
            localMutationChecked.await()
            isSyncMutationRecordingSuppressed()
        }

        assertTrue(remoteApplySuppressed)
        assertFalse(unrelatedLocalMutation.await())
    }
}
