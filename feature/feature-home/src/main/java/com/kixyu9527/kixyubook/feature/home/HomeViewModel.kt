package com.kixyu9527.kixyubook.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.ReadingStats
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.ReadingStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(val recent: List<LibraryBook> = emptyList(), val stats: ReadingStats = ReadingStats())

@HiltViewModel
class HomeViewModel @Inject constructor(
    books: BookRepository,
    stats: ReadingStatsRepository,
    settings: ReaderSettingsRepository,
) : ViewModel() {
    val uiState = combine(books.observeLibrary(), stats.observeStats(), settings.readingGoalMinutes) { library, readingStats, goal ->
        HomeUiState(library.filter { it.progress != null }.take(5), readingStats.copy(goalMinutes = goal))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
