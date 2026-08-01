package com.kixyu9527.kixyubook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.common.repository.BackupRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.sync.CloudSyncManager
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.GoogleConnectResult
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
    val backupOperation: BackupOperation? = null,
    val cloudSync: CloudSyncState = CloudSyncState(),
)

enum class BackupOperation { EXPORT, RESTORE }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ReaderSettingsRepository,
    private val fonts: FontRepository,
    private val backups: BackupRepository,
    private val cloudSync: CloudSyncManager,
) : ViewModel() {
    private val backupOperation = MutableStateFlow<BackupOperation?>(null)
    val uiState = combine(repository.settings, fonts.observeFonts(), repository.readingGoalMinutes, backupOperation, cloudSync.state) { settings, fontList, goal, operation, sync ->
        SettingsUiState(settings, fontList, goal, operation, sync)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    private val _restoreCompleted = MutableSharedFlow<Unit>()
    val restoreCompleted = _restoreCompleted.asSharedFlow()
    private val _authorizationRequests = Channel<PendingIntent>(Channel.BUFFERED)
    val authorizationRequests = _authorizationRequests.receiveAsFlow()

    fun update(transform: (ReaderSettings) -> ReaderSettings) { viewModelScope.launch { repository.update(transform) } }
    fun setGoal(minutes: Int) { viewModelScope.launch { repository.setReadingGoalMinutes(minutes) } }
    fun importFont(uri: String) { viewModelScope.launch { fonts.importFont(uri).onFailure { _messages.emit(it.message ?: "字体导入失败") } } }
    fun deleteFont(font: UserFont) { viewModelScope.launch {
        if (uiState.value.settings.fontUuid == font.uuid) repository.update { it.copy(fontUuid = null) }
        fonts.deleteFont(font.uuid)
    } }

    fun exportBackup(uri: String) = viewModelScope.launch {
        backupOperation.value = BackupOperation.EXPORT
        try {
            backups.exportTo(uri)
                .onSuccess { _messages.emit("完整备份已保存：${it.bookCount} 本书") }
                .onFailure { _messages.emit(it.message ?: "备份失败") }
        } finally {
            backupOperation.value = null
        }
    }

    fun restoreBackup(uri: String) = viewModelScope.launch {
        backupOperation.value = BackupOperation.RESTORE
        try {
            backups.restoreFrom(uri)
                .onSuccess { _restoreCompleted.emit(Unit) }
                .onFailure { _messages.emit(it.message ?: "恢复失败") }
        } finally {
            backupOperation.value = null
        }
    }

    fun connectGoogle(activity: Activity) = viewModelScope.launch { handleConnectResult(cloudSync.connect(activity)) }
    fun finishGoogleAuthorization(activity: Activity, resultData: Intent?) = viewModelScope.launch {
        handleConnectResult(cloudSync.finishAuthorization(activity, resultData))
    }
    fun setCloudSyncEnabled(enabled: Boolean) = viewModelScope.launch { cloudSync.setEnabled(enabled) }
    fun setSyncOriginalFiles(enabled: Boolean) = viewModelScope.launch { cloudSync.setSyncOriginalFiles(enabled) }
    fun setSyncFonts(enabled: Boolean) = viewModelScope.launch { cloudSync.setSyncFonts(enabled) }
    fun setWifiOnlyForLargeFiles(enabled: Boolean) = viewModelScope.launch { cloudSync.setWifiOnlyForLargeFiles(enabled) }
    fun syncNow() = cloudSync.syncNow()
    fun disconnectGoogle() = viewModelScope.launch { cloudSync.disconnect() }
    fun deleteCloudData(activity: Activity) = viewModelScope.launch {
        cloudSync.deleteCloudData(activity)
            .onSuccess { _messages.emit("Google Drive 同步数据已删除") }
            .onFailure { _messages.emit(it.message ?: "删除云端数据失败") }
    }

    private suspend fun handleConnectResult(result: GoogleConnectResult) {
        when (result) {
            GoogleConnectResult.Connected -> _messages.emit("Google 同步已启用")
            is GoogleConnectResult.NeedsAuthorization -> _authorizationRequests.send(result.pendingIntent)
            is GoogleConnectResult.Failed -> _messages.emit(result.message)
        }
    }
}
