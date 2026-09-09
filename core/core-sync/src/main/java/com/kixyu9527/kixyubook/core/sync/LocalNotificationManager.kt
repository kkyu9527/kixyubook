package com.kixyu9527.kixyubook.core.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the app's local-notification policy. Notifications are never required for the underlying
 * work to complete: when permission is denied (or HyperOS suppresses background local
 * notifications), the persisted WorkManager/sync state remains the source of truth.
 */
@Singleton
class LocalNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var appInForeground = false
    private val appForegroundState = MutableStateFlow(false)

    init {
        refreshLocalizedChannels()
    }

    fun onAppForeground() {
        // Refresh localized names after a system app-language change; IDs stay unchanged.
        refreshLocalizedChannels()
        appInForeground = true
        appForegroundState.value = true
        manager.cancel(NOTIFICATION_AUTH_REQUIRED)
        manager.cancel(NOTIFICATION_SYNC_CONFLICT)
    }

    fun onAppBackground() {
        appInForeground = false
        appForegroundState.value = false
    }

    suspend fun awaitBackgroundOrDelay(delayMillis: Long) {
        if (!appInForeground) return
        withTimeoutOrNull(delayMillis) {
            appForegroundState.filter { foreground -> !foreground }.first()
        }
    }

    fun canPostNotifications(): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return runtimeGranted && manager.areNotificationsEnabled()
    }

    fun clearAuthorizationFailure() {
        manager.cancel(NOTIFICATION_AUTH_REQUIRED)
    }

    fun showSyncConflict(count: Int, fingerprint: String) {
        if (appInForeground || !canPostNotifications()) return
        if (preferences.getString(KEY_CONFLICT_FINGERPRINT, null) == fingerprint) return
        preferences.edit { putString(KEY_CONFLICT_FINGERPRINT, fingerprint) }
        post(
            NOTIFICATION_SYNC_CONFLICT,
            NotificationCompat.Builder(context, CHANNEL_ACTION_REQUIRED)
                .setSmallIcon(R.drawable.ic_stat_cloud_sync)
                .setContentTitle(context.getString(R.string.notification_sync_conflict_title))
                .setContentText(context.getString(R.string.notification_sync_conflict_text, count))
                .setContentIntent(contentIntent(DESTINATION_CLOUD_SYNC, NOTIFICATION_SYNC_CONFLICT))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    fun clearSyncConflict() {
        preferences.edit { remove(KEY_CONFLICT_FINGERPRINT) }
        manager.cancel(NOTIFICATION_SYNC_CONFLICT)
    }

    fun syncProgressNotification(
        title: String = context.getString(R.string.notification_sync_title),
        text: String = context.getString(R.string.notification_sync_data),
        completed: Int? = null,
        total: Int? = null,
        workerId: java.util.UUID? = null,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_stat_cloud_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(DESTINATION_CLOUD_SYNC, NOTIFICATION_SYNC_PROGRESS))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (completed != null && total != null && total > 0) {
            builder.setProgress(total, completed.coerceIn(0, total), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        workerId?.let { id ->
            builder.addAction(
                0,
                context.getString(R.string.notification_cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(id),
            )
        }
        return builder.build()
    }

    fun backupProgressNotification(operation: BackupOperationType, workerId: java.util.UUID): Notification =
        NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_stat_cloud_sync)
            .setContentTitle(context.getString(if (operation == BackupOperationType.EXPORT) R.string.notification_backup_export_title else R.string.notification_backup_restore_title))
            .setContentText(context.getString(R.string.notification_background_task))
            .setContentIntent(contentIntent(DESTINATION_DATA_BACKUP, NOTIFICATION_BACKUP_PROGRESS))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, context.getString(R.string.notification_cancel), WorkManager.getInstance(context).createCancelPendingIntent(workerId))
            .build()

    fun showBackupResult(operation: BackupOperationType, bookCount: Int?, error: String?) {
        if (appInForeground || !canPostNotifications()) return
        val succeeded = error == null
        val title = when {
            !succeeded -> if (operation == BackupOperationType.EXPORT) context.getString(R.string.notification_export_failed) else context.getString(R.string.notification_restore_failed)
            operation == BackupOperationType.EXPORT -> context.getString(R.string.notification_export_complete)
            else -> context.getString(R.string.notification_restore_complete)
        }
        val text = error ?: when (operation) {
            BackupOperationType.EXPORT -> context.getString(R.string.notification_books_saved, bookCount ?: 0)
            BackupOperationType.RESTORE -> context.getString(R.string.notification_books_restored, bookCount ?: 0)
        }
        post(
            NOTIFICATION_BACKUP_RESULT,
            NotificationCompat.Builder(context, CHANNEL_TASK_RESULTS)
                .setSmallIcon(R.drawable.ic_stat_cloud_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent(DESTINATION_DATA_BACKUP, NOTIFICATION_BACKUP_RESULT))
                .setAutoCancel(true)
                .setCategory(if (succeeded) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    fun showReadingReminder(goalMinutes: Int, todayMinutes: Int) {
        if (!canPostNotifications()) return
        val remaining = (goalMinutes - todayMinutes).coerceAtLeast(1)
        post(
            NOTIFICATION_READING_REMINDER,
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_book)
                .setContentTitle(context.getString(R.string.notification_reading_remaining, remaining))
                .setContentText(context.getString(R.string.notification_reading_goal, goalMinutes))
                .setContentIntent(contentIntent(DESTINATION_HOME, NOTIFICATION_READING_REMINDER))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        // OEM policy or a permission transition must never fail the underlying sync/backup work.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission or OEM notification policy changed between the check and posting.
        }
    }

    private fun contentIntent(destination: String, requestCode: Int): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra(EXTRA_NOTIFICATION_DESTINATION, destination)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun refreshLocalizedChannels() {
        val systemManager = context.getSystemService(NotificationManager::class.java)
        systemManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_TRANSFERS,
                    context.getString(R.string.notification_channel_tasks),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_tasks_hint)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_ACTION_REQUIRED,
                    context.getString(R.string.notification_channel_attention),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_attention_hint)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    enableLights(true)
                },
                NotificationChannel(
                    CHANNEL_TASK_RESULTS,
                    context.getString(R.string.notification_channel_results),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_results_hint)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    enableLights(true)
                },
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.notification_channel_reading),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_reading_hint)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    enableLights(true)
                },
            ),
        )
    }

    companion object {
        const val EXTRA_NOTIFICATION_DESTINATION = "notification_destination"
        const val DESTINATION_HOME = "home"
        const val DESTINATION_CLOUD_SYNC = "cloud_sync"
        const val DESTINATION_DATA_BACKUP = "data_and_backup"

        const val NOTIFICATION_SYNC_PROGRESS = 2107
        const val NOTIFICATION_BACKUP_PROGRESS = 2108
        private const val NOTIFICATION_BACKUP_RESULT = 2109
        private const val NOTIFICATION_AUTH_REQUIRED = 2110
        private const val NOTIFICATION_SYNC_CONFLICT = 2111
        private const val NOTIFICATION_READING_REMINDER = 2112

        const val CHANNEL_TRANSFERS = "background_transfers_v2"
        private const val CHANNEL_ACTION_REQUIRED = "action_required_v2"
        private const val CHANNEL_TASK_RESULTS = "task_results_v1"
        private const val CHANNEL_REMINDERS = "reading_reminders_v2"
        private const val PREFERENCES_NAME = "notification_state"
        private const val KEY_CONFLICT_FINGERPRINT = "conflict_fingerprint"
    }
}
