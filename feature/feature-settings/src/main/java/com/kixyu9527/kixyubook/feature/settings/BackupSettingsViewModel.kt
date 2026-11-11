package com.kixyu9527.kixyubook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.common.repository.BackupPreview
import com.kixyu9527.kixyubook.core.common.repository.BackupRepository
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.sync.BackupOperationType
import com.kixyu9527.kixyubook.core.sync.BackupTaskPhase
import com.kixyu9527.kixyubook.core.sync.BackupWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backup owns its WorkManager flow and preview dialogs; it does not expose reader settings. */
@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    private val backups: BackupWorkScheduler,
    private val backupRepository: BackupRepository,
    private val fonts: FontRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    val operation: StateFlow<BackupOperationType?> = backups.state
        .map { task -> task.operation.takeIf { task.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    // null means success; a message means recovery requires a restart, not a completed restore.
    private val _restoreCompleted = MutableSharedFlow<String?>(replay = 1)
    val restoreCompleted = _restoreCompleted.asSharedFlow()

    private val _preview = MutableStateFlow<BackupPreview?>(null)
    val preview = _preview.asStateFlow()

    private val _inspecting = MutableStateFlow(false)
    val inspecting = _inspecting.asStateFlow()

    init {
        viewModelScope.launch {
            var lastReportedWorkId: java.util.UUID? = null
            backups.state.collect { task ->
                val workId = task.workId ?: return@collect
                if (workId == lastReportedWorkId) return@collect
                when (task.phase) {
                    BackupTaskPhase.SUCCEEDED -> {
                        lastReportedWorkId = workId
                        if (task.requiresRestart) _restoreCompleted.emit(null)
                        else _messages.emit(context.getString(R.string.settings_backup_saved, task.bookCount ?: 0))
                    }
                    BackupTaskPhase.FAILED -> {
                        lastReportedWorkId = workId
                        val message = task.error ?: context.getString(R.string.settings_backup_failed)
                        SettingsDiagnostics.failure(
                            "settings_backup_failed",
                            task.operation?.name?.lowercase() ?: "backup",
                            "backup",
                        )
                        if (task.requiresRestart) _restoreCompleted.emit(message) else _messages.emit(message)
                    }
                    else -> Unit
                }
            }
        }
    }

    fun export(uri: String) = viewModelScope.launch {
        runCatching { fonts.repairPendingReferences() }
            .onFailure { error -> SettingsDiagnostics.failure("settings_backup_failed", "repair", "backup", error) }
        runCatching { backups.enqueue(BackupOperationType.EXPORT, uri) }
            .onFailure { error -> SettingsDiagnostics.failure("settings_backup_failed", "export", "backup", error) }
    }

    fun restore(uri: String) {
        runCatching { backups.enqueue(BackupOperationType.RESTORE, uri) }
            .onFailure { error -> SettingsDiagnostics.failure("settings_backup_failed", "restore", "backup", error) }
    }

    fun inspect(uri: String) = viewModelScope.launch {
        _inspecting.value = true
        backupRepository.inspect(uri)
            .onSuccess { _preview.value = it }
            .onFailure { error ->
                SettingsDiagnostics.failure("settings_backup_failed", "inspect", "backup", error)
                _messages.emit(error.message ?: context.getString(R.string.settings_backup_read_failed))
            }
        _inspecting.value = false
    }

    fun clearPreview() {
        _preview.value = null
    }
}
