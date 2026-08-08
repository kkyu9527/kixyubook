package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityPullDecisionTest {
    @Test
    fun pendingDeleteNeverRestoresRemoteProgress() {
        assertFalse(
            shouldPullPriorityRemote(
                mutation(SyncMutationOperation.DELETE, changedAt = 100),
                remoteModifiedAt = 200,
            ),
        )
    }

    @Test
    fun newerLocalUpdateWinsWhileNewerRemoteCanBePulled() {
        assertFalse(
            shouldPullPriorityRemote(
                mutation(SyncMutationOperation.UPSERT, changedAt = 300),
                remoteModifiedAt = 200,
            ),
        )
        assertTrue(
            shouldPullPriorityRemote(
                mutation(SyncMutationOperation.UPSERT, changedAt = 100),
                remoteModifiedAt = 200,
            ),
        )
        assertTrue(shouldPullPriorityRemote(null, remoteModifiedAt = 200))
    }

    private fun mutation(operation: SyncMutationOperation, changedAt: Long) = SyncOutboxEntity(
        uuid = "mutation",
        entityType = SyncEntityType.PROGRESS.name,
        entityId = "book",
        operation = operation.name,
        changedAt = changedAt,
        logicalCounter = changedAt,
        deviceId = "device",
    )
}
