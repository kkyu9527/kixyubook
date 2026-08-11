package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@Singleton
class RoomSyncMutationRecorder @Inject constructor(
    private val dao: SyncDao,
    private val preferences: SyncPreferencesStore,
    private val scheduler: CloudSyncScheduler,
) : SyncMutationRecorder {
    private val logicalClock = AtomicLong()

    override suspend fun record(type: SyncEntityType, entityId: String, operation: SyncMutationOperation) {
        if (isSyncMutationRecordingSuppressed()) return
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

    suspend fun <T> withoutRecording(block: suspend () -> T): T =
        withoutSyncMutationRecording(block)
}

internal suspend fun isSyncMutationRecordingSuppressed(): Boolean =
    coroutineContext[MutationRecordingSuppressedKey] != null

internal suspend fun <T> withoutSyncMutationRecording(block: suspend () -> T): T =
    withContext(MutationRecordingSuppressed) { block() }

private object MutationRecordingSuppressedKey : CoroutineContext.Key<MutationRecordingSuppressed>

private object MutationRecordingSuppressed :
    AbstractCoroutineContextElement(MutationRecordingSuppressedKey)
