package com.kixyu9527.kixyubook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.sync.BackupOperationType
import com.kixyu9527.kixyubook.core.sync.BackupTaskPhase
import com.kixyu9527.kixyubook.core.sync.BackupWorkScheduler
import com.kixyu9527.kixyubook.core.sync.CloudSyncManager
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.GoogleConnectResult
import com.kixyu9527.kixyubook.core.sync.InitialSyncChoice
import com.kixyu9527.kixyubook.core.sync.ReadingReminderScheduler
import com.kixyu9527.kixyubook.core.sync.ReadingReminderSettings
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: ReaderSettings = ReaderSettings(),
    val fonts: List<UserFont> = emptyList(),
    val goalMinutes: Int = 30,
    val backupOperation: BackupOperationType? = null,
    val cloudSync: CloudSyncState = CloudSyncState(),
    val readingReminder: ReadingReminderSettings = ReadingReminderSettings(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ReaderSettingsRepository,
    private val fonts: FontRepository,
    private val backups: BackupWorkScheduler,
    private val cloudSync: CloudSyncManager,
    private val readingReminders: ReadingReminderScheduler,
) : ViewModel() {
    private data class BasicSettings(
        val settings: ReaderSettings,
        val fonts: List<UserFont>,
        val goalMinutes: Int,
    )

    private val basicSettings = combine(
        repository.settings,
        fonts.observeFonts(),
        repository.readingGoalMinutes,
    ) { settings, fontList, goal -> BasicSettings(settings, fontList, goal) }

    val uiState = combine(
        basicSettings,
        backups.state,
        cloudSync.state,
        readingReminders.settings,
    ) { basic, backup, sync, reminder ->
        SettingsUiState(
            settings = basic.settings,
            fonts = basic.fonts,
            goalMinutes = basic.goalMinutes,
            backupOperation = backup.operation.takeIf { backup.isActive },
            cloudSync = sync,
            readingReminder = reminder,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    private val _restoreCompleted = MutableSharedFlow<Unit>()
    val restoreCompleted = _restoreCompleted.asSharedFlow()
    private val _authorizationRequests = Channel<PendingIntent>(Channel.BUFFERED)
    val authorizationRequests = _authorizationRequests.receiveAsFlow()

    init {
        viewModelScope.launch {
            var lastReportedWorkId: java.util.UUID? = null
            backups.state.collect { task ->
                val workId = task.workId ?: return@collect
                if (workId == lastReportedWorkId) return@collect
                when (task.phase) {
                    BackupTaskPhase.SUCCEEDED -> {
                        lastReportedWorkId = workId
                        if (task.requiresRestart) _restoreCompleted.emit(Unit)
                        else _messages.emit("完整备份已保存：${task.bookCount ?: 0} 本书")
                    }
                    BackupTaskPhase.FAILED -> {
                        lastReportedWorkId = workId
                        _messages.emit(task.error ?: "完整备份任务失败")
                    }
                    else -> Unit
                }
            }
        }
    }

    fun update(transform: (ReaderSettings) -> ReaderSettings) { viewModelScope.launch { repository.update(transform) } }
    fun setGoal(minutes: Int) { viewModelScope.launch { repository.setReadingGoalMinutes(minutes) } }
    fun importFont(uri: String) {
        viewModelScope.launch {
            fonts.importFont(uri)
                .onSuccess { font ->
                    repository.update { it.copy(fontUuid = font.uuid) }
                    _messages.emit("已导入并使用 ${font.name}")
                }
                .onFailure { _messages.emit(it.message ?: "字体导入失败") }
        }
    }
    fun deleteFont(font: UserFont) { viewModelScope.launch {
        if (uiState.value.settings.fontUuid == font.uuid) repository.update { it.copy(fontUuid = null) }
        fonts.deleteFont(font.uuid)
    } }

    fun exportBackup(uri: String) = backups.enqueue(BackupOperationType.EXPORT, uri)

    fun restoreBackup(uri: String) = backups.enqueue(BackupOperationType.RESTORE, uri)

    fun setReadingReminderEnabled(enabled: Boolean) = viewModelScope.launch {
        readingReminders.setEnabled(enabled)
    }

    fun setReadingReminderTime(hour: Int, minute: Int) = viewModelScope.launch {
        readingReminders.setTime(hour, minute)
    }

    fun connectGoogle(activity: Activity) = viewModelScope.launch { handleConnectResult(cloudSync.connect(activity)) }
    fun switchGoogleAccount(activity: Activity) = viewModelScope.launch {
        handleConnectResult(cloudSync.switchAccount(activity))
    }
    fun finishGoogleAuthorization(activity: Activity, resultData: Intent?) = viewModelScope.launch {
        handleConnectResult(cloudSync.finishAuthorization(activity, resultData))
    }
    fun setCloudSyncEnabled(enabled: Boolean) = viewModelScope.launch { cloudSync.setEnabled(enabled) }
    fun setSyncOriginalFiles(enabled: Boolean) = viewModelScope.launch { cloudSync.setSyncOriginalFiles(enabled) }
    fun setSyncFonts(enabled: Boolean) = viewModelScope.launch { cloudSync.setSyncFonts(enabled) }
    fun setWifiOnlyForLargeFiles(enabled: Boolean) = viewModelScope.launch { cloudSync.setWifiOnlyForLargeFiles(enabled) }
    fun resolveInitialSync(choice: InitialSyncChoice) = viewModelScope.launch {
        cloudSync.resolveInitialSync(choice)
            .onFailure { _messages.emit(it.message ?: "同步冲突处理失败") }
    }
    fun syncNow() = cloudSync.syncNow()
    fun refreshGoogleDriveStorage(force: Boolean = false) = cloudSync.refreshStorageQuota(force)
    fun disconnectGoogle() = viewModelScope.launch { cloudSync.disconnect() }
    fun deleteCloudData(activity: Activity) = viewModelScope.launch {
        cloudSync.deleteCloudData(activity)
            .onSuccess { _messages.emit("Google Drive 同步数据已删除") }
            .onFailure { _messages.emit(it.message ?: "删除云端数据失败") }
    }

    private suspend fun handleConnectResult(result: GoogleConnectResult) {
        when (result) {
            GoogleConnectResult.Connected -> _messages.emit("Google 账号已连接，正在自动同步")
            is GoogleConnectResult.NeedsAuthorization -> _authorizationRequests.send(result.pendingIntent)
            is GoogleConnectResult.Failed -> _messages.emit(result.message)
        }
    }
}
