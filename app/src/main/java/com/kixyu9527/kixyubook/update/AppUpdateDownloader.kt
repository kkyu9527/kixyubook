package com.kixyu9527.kixyubook.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.kixyu9527.kixyubook.BuildConfig
import com.kixyu9527.kixyubook.core.common.model.AppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun download(update: AppUpdateInfo): Boolean = runCatching {
        enqueue(update)
    }.getOrElse {
        Toast.makeText(context, "下载启动失败，请稍后重试", Toast.LENGTH_LONG).show()
        false
    }

    private fun enqueue(update: AppUpdateInfo): Boolean {
        val downloadUrl = update.downloadUrl?.takeIf(::isTrustedDownloadUrl) ?: return false
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
        directory.mkdirs()
        directory.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.extension.equals("apk", ignoreCase = true) }
            ?.forEach(File::delete)

        val apk = File(directory, "$FILE_PREFIX${update.versionName.safeFileName()}.apk")
        val request = DownloadManager.Request(downloadUrl.toUri())
            .setTitle("Kixyu Book ${update.versionName}")
            .setDescription("正在下载应用更新")
            .setMimeType(APK_MIME_TYPE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, apk.name)

        val id = downloadManager(context).enqueue(request)
        preferences(context).edit {
            putLong(KEY_DOWNLOAD_ID, id)
            putString(KEY_APK_PATH, apk.absolutePath)
            putString(KEY_VERSION, update.versionName)
            putBoolean(KEY_INSTALL_LAUNCHED, false)
        }
        Toast.makeText(context, "已在后台下载，完成后将打开安装页面", Toast.LENGTH_LONG).show()
        return true
    }

    /** Resumes the installer after the user grants "install unknown apps" permission. */
    fun resumePendingInstallIfPermitted() {
        if (!pendingVersionIsNewer(context)) {
            clearPendingDownload(context)
            return
        }
        if (context.packageManager.canRequestPackageInstalls()) {
            launchPendingInstaller(context)
        }
    }

    companion object {
        private const val TRUSTED_DOWNLOAD_PREFIX =
            "https://github.com/kkyu9527/kixyubook/releases/download/"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val FILE_PREFIX = "KixyuBook-"
        private const val PREFS_NAME = "app_update_download"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_APK_PATH = "apk_path"
        private const val KEY_VERSION = "version"
        private const val KEY_INSTALL_LAUNCHED = "install_launched"

        internal fun handleDownloadCompleted(context: Context, completedId: Long) {
            val prefs = preferences(context)
            if (completedId <= 0L || completedId != prefs.getLong(KEY_DOWNLOAD_ID, -1L)) return
            if (!downloadSucceeded(context, completedId)) return
            if (!pendingVersionIsNewer(context)) {
                clearPendingDownload(context)
                return
            }

            if (context.packageManager.canRequestPackageInstalls()) {
                launchPendingInstaller(context)
            } else {
                val permissionIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(permissionIntent) }
            }
        }

        private fun launchPendingInstaller(context: Context) {
            val prefs = preferences(context)
            if (prefs.getBoolean(KEY_INSTALL_LAUNCHED, false)) return
            val downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
            val apk = prefs.getString(KEY_APK_PATH, null)?.let(::File) ?: return
            if (!apk.isFile || !downloadSucceeded(context, downloadId)) return

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val installIntent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit { putBoolean(KEY_INSTALL_LAUNCHED, true) }
            runCatching { context.startActivity(installIntent) }
                .onFailure { prefs.edit { putBoolean(KEY_INSTALL_LAUNCHED, false) } }
        }

        private fun downloadSucceeded(context: Context, downloadId: Long): Boolean {
            if (downloadId <= 0L) return false
            val cursor: Cursor = downloadManager(context).query(
                DownloadManager.Query().setFilterById(downloadId),
            ) ?: return false
            return cursor.use {
                it.moveToFirst() &&
                    it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                    DownloadManager.STATUS_SUCCESSFUL
            }
        }

        private fun downloadManager(context: Context) =
            context.getSystemService(DownloadManager::class.java)

        private fun preferences(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun pendingVersionIsNewer(context: Context): Boolean {
            val version = preferences(context).getString(KEY_VERSION, null) ?: return false
            return isNewerVersion(version, BuildConfig.VERSION_NAME)
        }

        private fun clearPendingDownload(context: Context) {
            val prefs = preferences(context)
            prefs.getString(KEY_APK_PATH, null)?.let(::File)?.takeIf(File::isFile)?.delete()
            prefs.edit { clear() }
        }

        private fun isTrustedDownloadUrl(url: String): Boolean =
            url.startsWith(TRUSTED_DOWNLOAD_PREFIX) &&
                url.substringBefore('?').endsWith(".apk", ignoreCase = true)

        private fun String.safeFileName(): String = replace(Regex("[^0-9A-Za-z._-]"), "_")
    }
}

class AppUpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        AppUpdateDownloader.handleDownloadCompleted(
            context.applicationContext,
            intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L),
        )
    }
}
