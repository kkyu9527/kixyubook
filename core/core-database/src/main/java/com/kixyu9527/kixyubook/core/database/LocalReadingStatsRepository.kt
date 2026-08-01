package com.kixyu9527.kixyubook.core.database

import com.kixyu9527.kixyubook.core.common.model.ReadingStats
import com.kixyu9527.kixyubook.core.common.repository.ReadingStatsRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.database.dao.BookDao
import com.kixyu9527.kixyubook.core.database.entity.ReadingSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalReadingStatsRepository @Inject constructor(
    private val dao: BookDao,
    private val syncMutations: SyncMutationRecorder,
) : ReadingStatsRepository {
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
            streakDays = streak,
        )
    }

    override suspend fun recordSession(bookUuid: String, durationMillis: Long) = withContext(Dispatchers.IO) {
        if (durationMillis >= 1_000) {
            val syncUuid = UUID.randomUUID().toString()
            dao.insertSession(ReadingSessionEntity(
                bookUuid = bookUuid,
                startedTime = System.currentTimeMillis() - durationMillis,
                durationMillis = durationMillis,
                epochDay = LocalDate.now().toEpochDay(),
                syncUuid = syncUuid,
            ))
            syncMutations.record(SyncEntityType.SESSION, syncUuid)
        }
    }
}
