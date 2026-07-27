package com.kixyu9527.kixyubook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.UserFont
import com.kixyu9527.kixyubook.core.common.repository.FontRepository
import com.kixyu9527.kixyubook.core.common.repository.BackupRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: ReaderSettings = ReaderSettings(),
    val fonts: List<UserFont> = emptyList(),
    val goalMinutes: Int = 30,
    val backupBusy: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ReaderSettingsRepository,
    private val fonts: FontRepository,
    private val backups: BackupRepository,
) : ViewModel() {
    private val backupBusy = MutableStateFlow(false)
    val uiState = combine(repository.settings, fonts.observeFonts(), repository.readingGoalMinutes, backupBusy) { settings, fontList, goal, busy -> SettingsUiState(settings, fontList, goal, busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    private val _restoreCompleted = MutableSharedFlow<Unit>()
    val restoreCompleted = _restoreCompleted.asSharedFlow()

    fun update(transform: (ReaderSettings) -> ReaderSettings) { viewModelScope.launch { repository.update(transform) } }
    fun setGoal(minutes: Int) { viewModelScope.launch { repository.setReadingGoalMinutes(minutes) } }
    fun importFont(uri: String) { viewModelScope.launch { fonts.importFont(uri).onFailure { _messages.emit(it.message ?: "字体导入失败") } } }
    fun deleteFont(font: UserFont) { viewModelScope.launch {
        if (uiState.value.settings.fontUuid == font.uuid) repository.update { it.copy(fontUuid = null) }
        fonts.deleteFont(font.uuid)
    } }

    fun exportBackup(uri: String) = viewModelScope.launch {
        backupBusy.value = true
        backups.exportTo(uri)
            .onSuccess { _messages.emit("完整备份已保存：${it.bookCount} 本书") }
            .onFailure { _messages.emit(it.message ?: "备份失败") }
        backupBusy.value = false
    }

    fun restoreBackup(uri: String) = viewModelScope.launch {
        backupBusy.value = true
        backups.restoreFrom(uri)
            .onSuccess { _restoreCompleted.emit(Unit) }
            .onFailure { _messages.emit(it.message ?: "恢复失败") }
        backupBusy.value = false
    }
}
