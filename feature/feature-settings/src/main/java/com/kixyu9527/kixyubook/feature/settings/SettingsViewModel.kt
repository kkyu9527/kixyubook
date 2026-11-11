package com.kixyu9527.kixyubook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.designsystem.component.resetReaderOwnedFields
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.reader.engine.LocalMetadata
import com.kixyu9527.kixyubook.core.sync.BackupWorkScheduler
import com.kixyu9527.kixyubook.core.sync.BackupOperationType
import com.kixyu9527.kixyubook.core.sync.CloudSyncManager
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.ReadingReminderScheduler
import com.kixyu9527.kixyubook.core.sync.ReadingReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kixyu9527.kixyubook.core.common.operation.LatestOperationWriter
import com.kixyu9527.kixyubook.core.common.operation.UserOperationController
import com.kixyu9527.kixyubook.core.common.configuration.ReaderSettingsRequests
import com.kixyu9527.kixyubook.core.common.configuration.applySettingsPatch

data class SettingsUiState(
    /** False until the first persisted snapshot arrives; updates must not use default values. */
    val loaded: Boolean = false,
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
            loaded = true,
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
                .onFailure { error ->
                    SettingsDiagnostics.failure("settings_filename_rule_failed", "persist", "filename_rule", error)
                    _messages.emit(context.getString(R.string.settings_filename_rules_save_failed))
                }
        }
    }

    fun addFilenameRuleSpec(spec: FilenameRuleSpec) {
        viewModelScope.launch {
            runCatching { libraryPreferences.upsertFilenameRuleSpec(spec) }
                .onSuccess {
                    val count = libraryPreferences.preferences.first().filenameRuleSpecs.size
                    _messages.emit(context.getString(R.string.settings_filename_rules_saved, count))
                }
                .onFailure { error ->
                    SettingsDiagnostics.failure("settings_filename_rule_failed", "persist", "filename_rule", error)
                    _messages.emit(context.getString(R.string.settings_filename_rules_save_failed))
                }
        }
    }

    fun setFilenameRuleSpecEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { libraryPreferences.setFilenameRuleSpecEnabled(id, enabled) }
                .onFailure { error ->
                    SettingsDiagnostics.failure("settings_filename_rule_failed", "persist", "filename_rule", error)
                    _messages.emit(context.getString(R.string.settings_filename_rules_save_failed))
                }
        }
    }

    fun removeFilenameRuleSpec(id: String) {
        viewModelScope.launch {
            runCatching { libraryPreferences.removeFilenameRuleSpec(id) }
                .onFailure { error ->
                    SettingsDiagnostics.failure("settings_filename_rule_failed", "persist", "filename_rule", error)
                    _messages.emit(context.getString(R.string.settings_filename_rules_save_failed))
                }
        }
    }

    fun removeFilenameRule(pattern: String) {
        viewModelScope.launch {
            runCatching { libraryPreferences.removeFilenameRule(pattern) }
                .onFailure { error ->
                    SettingsDiagnostics.failure("settings_filename_rule_failed", "persist", "filename_rule", error)
                    _messages.emit(context.getString(R.string.settings_filename_rules_save_failed))
                }
        }
    }

    fun update(transform: (ReaderSettings) -> ReaderSettings) {
        // A tap before the persisted snapshot loads would otherwise be computed against defaults
        // and written back over the real value.
        if (!uiState.value.loaded) return
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

}
