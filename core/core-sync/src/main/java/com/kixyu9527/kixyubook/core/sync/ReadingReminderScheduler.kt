package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.ReadingStatsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationSettingsDataStore by preferencesDataStore("notification_settings")

data class ReadingReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0,
)

@Singleton
class NotificationPreferencesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val readingReminder: Flow<ReadingReminderSettings> = context.notificationSettingsDataStore.data.map { values ->
        ReadingReminderSettings(
            enabled = values[REMINDER_ENABLED] ?: false,
            hour = (values[REMINDER_HOUR] ?: 20).coerceIn(0, 23),
            minute = (values[REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
        )
    }

    suspend fun current(): ReadingReminderSettings = readingReminder.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.notificationSettingsDataStore.edit { it[REMINDER_ENABLED] = enabled }
    }

    suspend fun setTime(hour: Int, minute: Int) {
        context.notificationSettingsDataStore.edit {
            it[REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun replace(settings: ReadingReminderSettings) {
        context.notificationSettingsDataStore.edit {
            it[REMINDER_ENABLED] = settings.enabled
            it[REMINDER_HOUR] = settings.hour.coerceIn(0, 23)
            it[REMINDER_MINUTE] = settings.minute.coerceIn(0, 59)
        }
    }

    private companion object {
        val REMINDER_ENABLED = booleanPreferencesKey("reading_reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reading_reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reading_reminder_minute")
    }
}

@Singleton
class ReadingReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: NotificationPreferencesStore,
    private val syncMutations: SyncMutationRecorder,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }
    val settings: Flow<ReadingReminderSettings> = preferences.readingReminder

    suspend fun setEnabled(enabled: Boolean) {
        preferences.setEnabled(enabled)
        if (enabled) schedule(preferences.current(), ExistingWorkPolicy.REPLACE)
        else workManager.cancelUniqueWork(WORK_NAME)
        recordChange()
    }

    suspend fun setTime(hour: Int, minute: Int) {
        preferences.setTime(hour, minute)
        val updated = preferences.current()
        if (updated.enabled) schedule(updated, ExistingWorkPolicy.REPLACE)
        recordChange()
    }

    suspend fun replace(settings: ReadingReminderSettings) {
        preferences.replace(settings)
        if (settings.enabled) schedule(preferences.current(), ExistingWorkPolicy.REPLACE)
        else workManager.cancelUniqueWork(WORK_NAME)
        recordChange()
    }

    suspend fun ensureScheduled() {
        val current = preferences.current()
        if (current.enabled) schedule(current, ExistingWorkPolicy.KEEP)
        else workManager.cancelUniqueWork(WORK_NAME)
    }

    internal suspend fun scheduleFollowingReminder() {
        val current = preferences.current()
        if (current.enabled) schedule(current, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun schedule(settings: ReadingReminderSettings, policy: ExistingWorkPolicy) {
        val now = ZonedDateTime.now()
        var next = now.withHour(settings.hour).withMinute(settings.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMillis = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReadingReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private suspend fun recordChange() = syncMutations.record(SyncEntityType.SETTINGS, "global")

    private companion object {
        const val WORK_NAME = "daily-reading-reminder"
    }
}

class ReadingReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            ReadingReminderWorkerEntryPoint::class.java,
        )
        val reminder = dependencies.notificationPreferences().current()
        if (!reminder.enabled) return Result.success()
        return try {
            val goal = dependencies.readerSettings().readingGoalMinutes.first()
            val todayMinutes = TimeUnit.MILLISECONDS.toMinutes(
                dependencies.readingStats().observeStats().first().todayMillis,
            ).toInt()
            if (todayMinutes < goal) {
                dependencies.notifications().showReadingReminder(goal, todayMinutes)
            }
            dependencies.reminderScheduler().scheduleFollowingReminder()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReadingReminderWorkerEntryPoint {
    fun notificationPreferences(): NotificationPreferencesStore
    fun notifications(): LocalNotificationManager
    fun readerSettings(): ReaderSettingsRepository
    fun readingStats(): ReadingStatsRepository
    fun reminderScheduler(): ReadingReminderScheduler
}
