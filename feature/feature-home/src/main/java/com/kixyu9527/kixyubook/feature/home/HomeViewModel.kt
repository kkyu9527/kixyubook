package com.kixyu9527.kixyubook.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.ReadingStats
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.ReadingStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(val recent: List<LibraryBook> = emptyList(), val stats: ReadingStats = ReadingStats())

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val books: BookRepository,
    stats: ReadingStatsRepository,
    settings: ReaderSettingsRepository,
) : ViewModel() {
    private val preparedBooks = mutableSetOf<String>()
    private val prewarmDispatcher = Dispatchers.IO.limitedParallelism(1)

    val uiState = combine(books.observeLibrary(), stats.observeStats(), settings.readingGoalMinutes) { library, readingStats, goal ->
        HomeUiState(library.filter { it.progress != null }.take(5), readingStats.copy(goalMinutes = goal))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun prewarmReader(bookUuid: String) {
        if (!preparedBooks.add(bookUuid)) return
        viewModelScope.launch(prewarmDispatcher) {
            books.prepareReader(bookUuid)
        }
    }
}
