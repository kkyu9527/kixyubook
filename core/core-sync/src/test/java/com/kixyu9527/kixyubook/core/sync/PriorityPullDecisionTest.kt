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

    @Test
    fun sameSecondRemoteWritesAreDistinguishedByVersion() {
        val remote = driveObject(modifiedAt = 100, version = 5)
        assertTrue(isRemoteNewer(remote, localModifiedAt = 100, localVersion = 4))
        assertFalse(isRemoteNewer(remote, localModifiedAt = 100, localVersion = 5))
    }

    @Test
    fun freshnessFallsBackToModifiedTimeWithoutVersions() {
        val remote = driveObject(modifiedAt = 100, version = 0)
        assertTrue(isRemoteNewer(remote, localModifiedAt = 99, localVersion = 0))
        assertFalse(isRemoteNewer(remote, localModifiedAt = 100, localVersion = 0))
    }

    @Test
    fun tombstoneIsSkippedWhileALocalEditIsPending() {
        assertFalse(shouldApplyRemoteTombstone(localPendingCount = 1))
        assertTrue(shouldApplyRemoteTombstone(localPendingCount = 0))
    }

    private fun driveObject(modifiedAt: Long, version: Long) = DriveObject(
        id = "id",
        name = "name",
        objectKey = "key",
        mimeType = "application/json",
        modifiedAt = modifiedAt,
        version = version,
        size = 0,
        md5 = null,
    )

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
