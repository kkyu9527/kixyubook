package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSyncMutationRecorder @Inject constructor(
    private val dao: SyncDao,
    private val preferences: SyncPreferencesStore,
    private val scheduler: CloudSyncScheduler,
) : SyncMutationRecorder {
    private val logicalClock = AtomicLong()
    private val suppression = AtomicInteger()

    override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) {
        if (suppression.get() > 0) return
        val now = System.currentTimeMillis()
        dao.upsertOutbox(
            SyncOutboxEntity(
                uuid = UUID.randomUUID().toString(),
                entityType = type.name,
                entityId = entityId,
                operation = operation.name,
                changedAt = now,
                logicalCounter = logicalClock.updateAndGet { previous -> maxOf(previous + 1, now) },
                deviceId = preferences.deviceId(),
            ),
        )
        if (preferences.current().enabled) scheduler.requestDebounced(type, entityId)
    }

    suspend fun <T> withoutRecording(block: suspend () -> T): T {
        suppression.incrementAndGet()
        return try { block() } finally { suppression.decrementAndGet() }
    }
}
