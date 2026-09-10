package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.database.dao.SyncDao
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.collect
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSyncMutationRecorder @Inject constructor(
    private val dao: SyncDao,
    private val preferences: SyncPreferencesStore,
    private val scheduler: CloudSyncScheduler,
) : SyncMutationRecorder {
    private val logicalClock = AtomicLong()

    init {
        // Scheduling is a consequence of committed data, never part of a caller's transaction.
        // A scheduler failure retries from durable Room state and cannot undo a saved note.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var seen = emptySet<String>()
            dao.observePendingMutations().onEach { pending ->
                if (preferences.current().enabled) {
                    val fresh = pending.filter { it.uuid !in seen }
                    scheduler.requestDebouncedForMutations(fresh.map { it.entityType to it.entityId })
                }
                seen = pending.mapTo(hashSetOf()) { it.uuid }
            }.retryWhen { cause, _ ->
                if (cause is CancellationException) false else {
                    delay(1_000)
                    true
                }
            }.collect()
        }
    }

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
    }

    suspend fun <T> withoutRecording(block: suspend () -> T): T =
        withoutSyncMutationRecording(block)
}

internal suspend fun isSyncMutationRecordingSuppressed(): Boolean =
    com.kixyu9527.kixyubook.core.common.repository.syncMutationRecordingSuppressed()

internal suspend fun <T> withoutSyncMutationRecording(block: suspend () -> T): T =
    com.kixyu9527.kixyubook.core.common.repository.withoutRecordingSyncMutations(block)
