package com.kixyu9527.kixyubook.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.ReadingStats
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.LibraryPreferencesRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.ReadingStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(val current: LibraryBook? = null, val stats: ReadingStats = ReadingStats())

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val books: BookRepository,
    stats: ReadingStatsRepository,
    settings: ReaderSettingsRepository,
    libraryPreferences: LibraryPreferencesRepository,
) : ViewModel() {
    private val preparedBooks = mutableSetOf<String>()
    private val prewarmDispatcher = Dispatchers.IO.limitedParallelism(1)

    val uiState = combine(
        books.observeLibrary(),
        stats.observeStats(),
        settings.readingGoalMinutes,
        libraryPreferences.preferences,
    ) { library, readingStats, goal, preferences ->
        HomeUiState(
            current = selectContinueReadingBook(library, preferences.hiddenCategories),
            stats = readingStats.copy(goalMinutes = goal),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun prewarmReader(bookUuid: String) {
        if (!preparedBooks.add(bookUuid)) return
        viewModelScope.launch(prewarmDispatcher) {
            books.prepareReader(bookUuid)
        }
    }
}

internal fun selectContinueReadingBook(
    library: List<LibraryBook>,
    hiddenCategories: Set<String>,
): LibraryBook? = library.firstOrNull {
    it.progress != null && it.book.category !in hiddenCategories
}
