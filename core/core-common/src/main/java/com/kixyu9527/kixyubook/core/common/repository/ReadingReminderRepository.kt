package com.kixyu9527.kixyubook.core.common.repository

import com.kixyu9527.kixyubook.core.common.model.ReadingReminderSettings
import kotlinx.coroutines.flow.Flow

interface ReadingReminderRepository {
    val readingReminder: Flow<ReadingReminderSettings>
    suspend fun replace(settings: ReadingReminderSettings)
}
