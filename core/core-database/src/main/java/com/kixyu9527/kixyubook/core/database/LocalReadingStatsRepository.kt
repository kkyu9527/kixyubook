package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.model.ReadingStats
import com.kixyu9527.kixyubook.core.common.repository.ReadingStatsRepository
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.entity.ReadingSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalReadingStatsRepository @Inject constructor(private val dao: BookDao) : ReadingStatsRepository {
    override fun observeStats() = dao.observeSessions().map { sessions ->
        val today = LocalDate.now().toEpochDay()
        val todaySessions = sessions.filter { it.epochDay == today }
        val days = sessions.map { it.epochDay }.distinct().sortedDescending()
        var streak = 0
        var expected = today
        if (days.firstOrNull() != today) expected = today - 1
        for (day in days) {
            if (day == expected) { streak++; expected-- } else if (day < expected) break
        }
        ReadingStats(
            todayMillis = todaySessions.sumOf { it.durationMillis },
            totalMillis = sessions.sumOf { it.durationMillis },
            todayCharacters = todaySessions.sumOf { it.charactersRead },
            totalCharacters = sessions.sumOf { it.charactersRead },
            streakDays = streak,
        )
    }

    override suspend fun recordSession(bookUuid: String, durationMillis: Long, charactersRead: Long) = withContext(Dispatchers.IO) {
        if (durationMillis >= 1_000) dao.insertSession(
            ReadingSessionEntity(bookUuid = bookUuid, startedTime = System.currentTimeMillis() - durationMillis, durationMillis = durationMillis, charactersRead = charactersRead.coerceAtLeast(0), epochDay = LocalDate.now().toEpochDay()),
        )
    }
}
