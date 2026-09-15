package com.kixyu9527.kixyubook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.common.repository.BackupPreview
import com.kixyu9527.kixyubook.core.common.repository.BackupRepository
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.designsystem.component.resetReaderOwnedFields
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.reader.engine.LocalMetadata
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
import com.kixyu9527.kixyubook.core.common.operation.LatestOperationWriter
import com.kixyu9527.kixyubook.core.common.operation.UserOperationController
import com.kixyu9527.kixyubook.core.common.configuration.ReaderSettingsRequests
import com.kixyu9527.kixyubook.core.common.configuration.applySettingsPatch

data class SettingsUiState(
    val settings: ReaderSettings = ReaderSettings(),
    val fonts: List<UserFont> = emptyList(),
    val goalMinutes: Int = 30,
    val backupOperation: BackupOperationType? = null,
    val cloudSync: CloudSyncState = CloudSyncState(),
    val readingReminder: ReadingReminderSettings = ReadingReminderSettings(),
    val filenameRules: List<String> = emptyList(),
    val filenameRuleSpecs: List<FilenameRuleSpec> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ReaderSettingsRepository,
    private val fonts: FontRepository,
    private val backups: BackupWorkScheduler,
    private val backupRepository: BackupRepository,
    private val cloudSync: CloudSyncManager,
    private val readingReminders: ReadingReminderScheduler,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val libraryPreferences: LibraryPreferencesRepository,
) : ViewModel() {
    val operations = UserOperationController(viewModelScope)
    private val settingWrites = LatestOperationWriter(viewModelScope, operations)
    private val settingRequests = ReaderSettingsRequests()
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
        libraryPreferences.preferences,
    ) { basic, backup, sync, reminder, library ->
        SettingsUiState(
            settings = basic.settings,
            fonts = basic.fonts,
            goalMinutes = basic.goalMinutes,
            backupOperation = backup.operation.takeIf { backup.isActive },
            cloudSync = sync,
            readingReminder = reminder,
            filenameRules = library.filenameRules,
            filenameRuleSpecs = library.filenameRuleSpecs,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    // null means success; a message means recovery requires a restart, not a completed restore.
    private val _restoreCompleted = MutableSharedFlow<String?>(replay = 1)
    val restoreCompleted = _restoreCompleted.asSharedFlow()
    private val _backupPreview = MutableStateFlow<BackupPreview?>(null)
    val backupPreview = _backupPreview.asStateFlow()
    private val _backupInspectionActive = MutableStateFlow(false)
    val backupInspectionActive = _backupInspectionActive.asStateFlow()
    private val _authorizationRequests = Channel<PendingIntent>(Channel.BUFFERED)

    /** Adds a rule that already passed the builder/advanced validation. */
    fun addFilenameRule(pattern: String) {
        val rule = pattern.trim()
        if (rule.isEmpty() || LocalMetadata.parseFilenameRules(listOf(rule)).rules.isEmpty()) return
        viewModelScope.launch {
            runCatching { libraryPreferences.addFilenameRule(rule) }
                .onSuccess {
                    val count = libraryPreferences.preferences.first().filenameRules.size
                    _messages.emit(context.getString(R.string.settings_filename_rules_saved, count))
                }
                .onFailure { _messages.emit(context.getString(R.string.settings_filename_rules_save_failed)) }
        }
    }

    fun addFilenameRuleSpec(spec: FilenameRuleSpec) {
        viewModelScope.launch {
            runCatching { libraryPreferences.upsertFilenameRuleSpec(spec) }
                .onSuccess {
                    val count = libraryPreferences.preferences.first().filenameRuleSpecs.size
                    _messages.emit(context.getString(R.string.settings_filename_rules_saved, count))
                }
                .onFailure { _messages.emit(context.getString(R.string.settings_filename_rules_save_failed)) }
        }
    }

    fun setFilenameRuleSpecEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { libraryPreferences.setFilenameRuleSpecEnabled(id, enabled) }
                .onFailure { _messages.emit(context.getString(R.string.settings_filename_rules_save_failed)) }
        }
    }

    fun removeFilenameRuleSpec(id: String) {
        viewModelScope.launch {
            runCatching { libraryPreferences.removeFilenameRuleSpec(id) }
                .onFailure { _messages.emit(context.getString(R.string.settings_filename_rules_save_failed)) }
        }
    }

    fun removeFilenameRule(pattern: String) {
        viewModelScope.launch {
            runCatching { libraryPreferences.removeFilenameRule(pattern) }
                .onFailure { _messages.emit(context.getString(R.string.settings_filename_rules_save_failed)) }
        }
    }
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
                        if (task.requiresRestart) _restoreCompleted.emit(null)
                        else _messages.emit(context.getString(R.string.settings_backup_saved, task.bookCount ?: 0))
                    }
                    BackupTaskPhase.FAILED -> {
                        lastReportedWorkId = workId
                        val message = task.error ?: context.getString(R.string.settings_backup_failed)
                        if (task.requiresRestart) _restoreCompleted.emit(message) else _messages.emit(message)
                    }
                    else -> Unit
                }
            }
        }
    }

    fun update(transform: (ReaderSettings) -> ReaderSettings) {
        settingRequests.changes(uiState.value.settings, transform).forEach { (field, patch) ->
            settingWrites.submit("reader:$field") { repository.update { applySettingsPatch(it, patch) } }
        }
    }
    fun resetAllReaderSettings() = settingWrites.submit("resetAll") {
        settingRequests.clear()
        repository.update { current ->
            // Resets every reader-owned field through the canonical group mapping (including the
            // app-shared light/dark mode and glass effect) while keeping language, UI style,
            // accent colour, sync and reading habits untouched.
            current.resetReaderOwnedFields()
        }
        _messages.emit(context.getString(R.string.settings_reading_reset))
    }
    fun setGoal(minutes: Int) = settingWrites.submit("goal") { repository.setReadingGoalMinutes(minutes) }
    fun importFont(uri: String) {
        operations.submit {
            val font = fonts.importFont(uri).getOrThrow()
            repository.update { it.copy(fontUuid = font.uuid) }
            _messages.emit(context.getString(R.string.settings_font_applied, font.name))
        }
    }
    fun deleteFont(font: UserFont) { operations.confirmDelete(font.name) {
        fonts.deleteFont(font.uuid)
    } }

    fun exportBackup(uri: String) = backups.enqueue(BackupOperationType.EXPORT, uri)

    fun restoreBackup(uri: String) = backups.enqueue(BackupOperationType.RESTORE, uri)

    fun inspectBackup(uri: String) = viewModelScope.launch {
        _backupInspectionActive.value = true
        backupRepository.inspect(uri)
            .onSuccess { _backupPreview.value = it }
            .onFailure { _messages.emit(it.message ?: context.getString(R.string.settings_backup_read_failed)) }
        _backupInspectionActive.value = false
    }

    fun clearBackupPreview() {
        _backupPreview.value = null
    }

    fun setReadingReminderEnabled(enabled: Boolean) = settingWrites.submit("reminderEnabled") {
        readingReminders.setEnabled(enabled)
    }

    fun setReadingReminderTime(hour: Int, minute: Int) = settingWrites.submit("reminderTime") {
        readingReminders.setTime(hour, minute)
    }

    fun connectGoogle(activity: Activity) = viewModelScope.launch { handleConnectResult(cloudSync.connect(activity)) }
    fun switchGoogleAccount(activity: Activity) = viewModelScope.launch {
        handleConnectResult(cloudSync.switchAccount(activity))
    }
    fun finishGoogleAuthorization(activity: Activity, resultData: Intent?) = viewModelScope.launch {
        handleConnectResult(cloudSync.finishAuthorization(activity, resultData))
    }
    fun setCloudSyncEnabled(enabled: Boolean) = settingWrites.submit("syncEnabled") { cloudSync.setEnabled(enabled) }
    fun setSyncOriginalFiles(enabled: Boolean) = settingWrites.submit("syncOriginals") { cloudSync.setSyncOriginalFiles(enabled) }
    fun setSyncFonts(enabled: Boolean) = settingWrites.submit("syncFonts") { cloudSync.setSyncFonts(enabled) }
    fun setWifiOnlyForLargeFiles(enabled: Boolean) = settingWrites.submit("syncWifi") { cloudSync.setWifiOnlyForLargeFiles(enabled) }
    fun resolveInitialSync(choice: InitialSyncChoice) = viewModelScope.launch {
        cloudSync.resolveInitialSync(choice)
            .onFailure { _messages.emit(it.message ?: context.getString(R.string.settings_conflict_failed)) }
    }
    fun syncNow() = cloudSync.syncNow()
    fun refreshGoogleDriveStorage(force: Boolean = false) = cloudSync.refreshStorageQuota(force)
    fun disconnectGoogle() = viewModelScope.launch { cloudSync.disconnect() }
    fun deleteCloudData(activity: Activity) = viewModelScope.launch {
        cloudSync.deleteCloudData(activity)
            .onSuccess { _messages.emit(context.getString(R.string.settings_cloud_deleted)) }
            .onFailure { _messages.emit(it.message ?: context.getString(R.string.settings_cloud_delete_failed)) }
    }

    private suspend fun handleConnectResult(result: GoogleConnectResult) {
        when (result) {
            GoogleConnectResult.Connected -> _messages.emit(context.getString(R.string.settings_account_connected))
            is GoogleConnectResult.NeedsAuthorization -> _authorizationRequests.send(result.pendingIntent)
            is GoogleConnectResult.Failed -> _messages.emit(result.message)
        }
    }

}
