package com.kixyu9527.kixyubook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.operation.LatestOperationWriter
import com.kixyu9527.kixyubook.core.common.operation.UserOperationController
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.sync.ReadingReminderScheduler
import com.kixyu9527.kixyubook.core.sync.ReadingReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ReadingHabitsState(
    val goalMinutes: Int = 30,
    val reminder: ReadingReminderSettings = ReadingReminderSettings(),
)

/** Owns only reading-goal and reminder writes; overview reads the same repositories independently. */
@HiltViewModel
class ReadingHabitsViewModel @Inject constructor(
    private val repository: ReaderSettingsRepository,
    private val readingReminders: ReadingReminderScheduler,
) : ViewModel() {
    val operations = UserOperationController(viewModelScope)
    private val writes = LatestOperationWriter(viewModelScope, operations)

    val state: StateFlow<ReadingHabitsState> = combine(
        repository.readingGoalMinutes,
        readingReminders.settings,
    ) { goal, reminder ->
        ReadingHabitsState(goalMinutes = goal, reminder = reminder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingHabitsState())

    fun setGoal(minutes: Int) = writes.submit("goal") { repository.setReadingGoalMinutes(minutes) }

    fun setEnabled(enabled: Boolean) = writes.submit("reminderEnabled") {
        readingReminders.setEnabled(enabled)
    }

    fun setTime(hour: Int, minute: Int) = writes.submit("reminderTime") {
        readingReminders.setTime(hour, minute)
    }
}
