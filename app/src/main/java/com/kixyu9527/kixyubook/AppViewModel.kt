package com.kixyu9527.kixyubook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.common.model.ReleaseNotesState
import com.kixyu9527.kixyubook.core.common.repository.AppUpdateRepository
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.CloudSyncCoordinator
import com.kixyu9527.kixyubook.core.sync.CloudSyncManager
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.ReadingReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kixyu9527.kixyubook.core.common.model.BookPreparationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: ReaderSettingsRepository,
    private val books: BookRepository,
    private val updates: AppUpdateRepository,
    private val cloudSync: CloudSyncCoordinator,
    cloudSyncManager: CloudSyncManager,
    private val readingReminders: ReadingReminderScheduler,
    val appMessages: AppTransientMessages,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
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
    val cloudSyncState: StateFlow<CloudSyncState> = cloudSyncManager.state

    init {
        viewModelScope.launch { updates.checkForUpdates(manual = false) }
        viewModelScope.launch { readingReminders.ensureScheduled() }
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

    /** Hydrates only already-persisted reader data while the navigation surface is animating. */
    fun prepareReader(bookUuid: String) {
        viewModelScope.launch { books.prepareReader(bookUuid) }
    }

    fun prioritizeBookSync(bookUuid: String) = cloudSync.prioritizeBook(bookUuid)

    suspend fun ensureBookAvailable(uuid: String, progress: (BookPreparationProgress) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val book = books.getBook(uuid) ?: error(context.getString(com.kixyu9527.kixyubook.feature.reader.R.string.reader_error_missing_book))
                if (book.originalPath.startsWith("google-drive://")) {
                    cloudSync.ensureBookAvailable(uuid, progress).getOrThrow()
                    check(books.getChapters(uuid).isNotEmpty()) {
                        context.getString(com.kixyu9527.kixyubook.feature.reader.R.string.reader_error_no_chapters)
                    }
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }

    override fun onCleared() {
        books.setAppAnimationActive(false)
    }
}
