package com.kixyu9527.kixyubook.core.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationOperation
import com.kixyu9527.kixyubook.core.database.entity.BookmarkEntity
import com.kixyu9527.kixyubook.core.database.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.MessageDigest

data class SyncAccount(
    val subject: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
)

data class DriveStorageQuota(
    /** Total Google account storage used by Drive, Gmail, and Google Photos. */
    val usageBytes: Long,
    val limitBytes: Long?,
    val usageInDriveBytes: Long,
    val usageInDriveTrashBytes: Long,
) {
    val remainingBytes: Long?
        get() = limitBytes?.let { (it - usageBytes).coerceAtLeast(0L) }

    val usedFraction: Float?
        get() = limitBytes
            ?.takeIf { it > 0L }
            ?.let { (usageBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }

    val isNearlyFull: Boolean
        get() = limitBytes?.takeIf { it > 0L }?.let { limit ->
            usageBytes.toDouble() / limit.toDouble() >= STORAGE_WARNING_FRACTION
        } == true
}

data class DriveStorageQuotaState(
    val quota: DriveStorageQuota? = null,
    val refreshing: Boolean = false,
    val errorMessage: String? = null,
)

enum class CloudSyncPhase { IDLE, AUTHORIZING, SYNCING, SUCCESS, AUTH_REQUIRED, ERROR }

data class InitialSyncDecision(
    val localBookCount: Int,
    val cloudBookCount: Int,
    val conflicts: List<InitialSyncConflict> = emptyList(),
)

internal fun InitialSyncDecision.shouldRestoreFromCloud(): Boolean =
    localBookCount == 0 && cloudBookCount > 0

internal fun InitialSyncDecision.requiresUserDecision(): Boolean = conflicts.isNotEmpty()

internal fun shouldPreferLocalConflicts(preferLocalUntil: Long, now: Long): Boolean =
    preferLocalUntil > now

data class InitialSyncConflict(
    val entityType: SyncEntityType,
    val entityId: String,
)

enum class InitialSyncChoice {
    KEEP_LOCAL_CHANGES,
    USE_CLOUD_CHANGES,
}

data class CloudSyncState(
    val account: SyncAccount? = null,
    val enabled: Boolean = false,
    val syncOriginalFiles: Boolean = true,
    val syncFonts: Boolean = true,
    val wifiOnlyForLargeFiles: Boolean = true,
    val phase: CloudSyncPhase = CloudSyncPhase.IDLE,
    val lastSyncTime: Long = 0,
    val pendingCount: Int = 0,
    val errorMessage: String? = null,
    val initialSyncDecision: InitialSyncDecision? = null,
    val inspectingInitialSync: Boolean = false,
    val storageQuota: DriveStorageQuotaState = DriveStorageQuotaState(),
)

data class CloudSyncProgress(
    val title: String,
    val text: String,
    val completed: Int? = null,
    val total: Int? = null,
)

sealed interface GoogleConnectResult {
    data object Connected : GoogleConnectResult
    data class NeedsAuthorization(val pendingIntent: PendingIntent) : GoogleConnectResult
    data class Failed(val message: String) : GoogleConnectResult
}

interface CloudSyncManager {
    val state: StateFlow<CloudSyncState>
    suspend fun connect(activity: Activity): GoogleConnectResult
    suspend fun switchAccount(activity: Activity): GoogleConnectResult
    suspend fun finishAuthorization(activity: Activity, resultData: Intent?): GoogleConnectResult
    suspend fun disconnect()
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setSyncOriginalFiles(enabled: Boolean)
    suspend fun setSyncFonts(enabled: Boolean)
    suspend fun setWifiOnlyForLargeFiles(enabled: Boolean)
    suspend fun resolveInitialSync(choice: InitialSyncChoice): Result<Unit>
    fun syncNow()
    fun refreshStorageQuota(force: Boolean = false)
    suspend fun deleteCloudData(activity: Activity): Result<Unit>
}

internal data class LocalCloudObject(
    val key: String,
    val name: String,
    val mimeType: String,
    val file: File,
    val temporary: Boolean = false,
)

internal data class RemoteSnapshot(
    val known: MutableMap<String, DriveObject>,
    val changed: MutableMap<String, DriveObject>,
    val nextPageToken: String?,
)

internal data class PreparedInitialSnapshot(
    val accountSubject: String,
    val snapshot: RemoteSnapshot,
    val createdAt: Long,
)

internal data class InitialRestorePlan(
    val priorityBookUuids: List<String>,
    val remainingBookUuids: List<String>,
)

internal fun planInitialRestore(
    bookUuids: List<String>,
    remote: Map<String, DriveObject>,
    priorityLimit: Int = INITIAL_PRIORITY_BOOK_LIMIT,
): InitialRestorePlan {
    val distinctBookUuids = bookUuids.distinct()
    val priority = distinctBookUuids
        .mapNotNull { uuid -> remote["progress/$uuid"]?.let { uuid to it.modifiedAt } }
        .sortedByDescending { (_, modifiedAt) -> modifiedAt }
        .take(priorityLimit)
        .map { it.first }
    val prioritySet = priority.toHashSet()
    return InitialRestorePlan(
        priorityBookUuids = priority,
        remainingBookUuids = distinctBookUuids.filterNot(prioritySet::contains),
    )
}

internal fun keysForMutation(value: SyncOutboxEntity): List<String> =
    when (SyncEntityType.valueOf(value.entityType)) {
        SyncEntityType.BOOK -> listOf("books/${value.entityId}/metadata", "books/${value.entityId}/source")
        SyncEntityType.PROGRESS -> listOf("progress/${value.entityId}")
        SyncEntityType.BOOKMARKS -> listOf("bookmarks/${value.entityId}")
        SyncEntityType.SETTINGS -> listOf("settings/global")
        SyncEntityType.SESSION -> listOf("sessions/${value.entityId}")
        SyncEntityType.FONT -> listOf("fonts/${value.entityId}/metadata", "fonts/${value.entityId}/source")
    }

/** A pending deletion always wins over an older cloud object, including priority reader pulls. */
internal fun shouldPullPriorityRemote(
    localMutation: SyncOutboxEntity?,
    remoteModifiedAt: Long,
): Boolean = localMutation == null || (
    localMutation.operation != SyncMutationOperation.DELETE.name &&
        localMutation.changedAt < remoteModifiedAt
    )

internal fun File.sha256(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

internal data class BookMarkOwner(val bookUuid: String) {
    companion object {
        fun from(value: BookmarkEntity) = BookMarkOwner(value.bookUuid)
    }
}

private const val INITIAL_PRIORITY_BOOK_LIMIT = 5
private const val STORAGE_WARNING_FRACTION = 0.9
