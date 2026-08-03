package com.kixyu9527.kixyubook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.common.model.ReleaseNotesState
import com.kixyu9527.kixyubook.core.common.repository.AppUpdateRepository
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: ReaderSettingsRepository,
    private val books: BookRepository,
    private val updates: AppUpdateRepository,
) : ViewModel() {
    /**
     * `null` means that the persisted app appearance has not been read yet.
     * Rendering a default ReaderSettings here would briefly build the Material
     * component tree before a persisted MIUIX value arrives on every cold start.
     */
    val settings: StateFlow<ReaderSettings?> = settingsRepository.settings
        .map<ReaderSettings, ReaderSettings?> { it }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )

    val updateState: StateFlow<AppUpdateState> = updates.state
    val releaseNotesState: StateFlow<ReleaseNotesState> = updates.releaseNotesState

    init {
        viewModelScope.launch { updates.checkForUpdates(manual = false) }
    }

    fun checkForUpdates() {
        viewModelScope.launch { updates.checkForUpdates(manual = true) }
    }

    fun clearUpdateResult() {
        updates.clearResult()
    }

    fun loadCurrentReleaseNotes() {
        viewModelScope.launch { updates.loadReleaseNotes(BuildConfig.VERSION_NAME) }
    }

    fun setAnimationActive(active: Boolean) {
        books.setAppAnimationActive(active)
    }

    override fun onCleared() {
        books.setAppAnimationActive(false)
        super.onCleared()
    }
}
