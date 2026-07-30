package com.kixyu9527.kixyubook.update

import com.kixyu9527.kixyubook.BuildConfig
import com.kixyu9527.kixyubook.core.common.model.AppUpdateInfo
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.common.repository.AppUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubUpdateRepository @Inject constructor() : AppUpdateRepository {
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    override val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    private val checkMutex = Mutex()

    override suspend fun checkForUpdates(manual: Boolean) {
        checkMutex.withLock {
            mutableState.value = AppUpdateState.Checking
            val result = runCatching { fetchLatestRelease() }
            mutableState.value = result.fold(
                onSuccess = { release ->
                    when {
                        release == null -> manualResult(manual)
                        isNewerVersion(release.versionName, BuildConfig.VERSION_NAME) -> {
                            AppUpdateState.Available(release)
                        }
                        else -> manualResult(manual)
                    }
                },
                onFailure = { error ->
                    if (manual) {
                        AppUpdateState.Failed(error.toUserMessage())
                    } else {
                        AppUpdateState.Idle
                    }
                },
            )
        }
    }

    override fun clearResult() {
        mutableState.value = AppUpdateState.Idle
    }

    private fun manualResult(manual: Boolean): AppUpdateState =
        if (manual) AppUpdateState.UpToDate(BuildConfig.VERSION_NAME) else AppUpdateState.Idle

    private suspend fun fetchLatestRelease(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching { fetchLatestReleaseFromApi() }
            .getOrElse { fetchLatestReleaseFromRedirect() }
    }

    private fun fetchLatestReleaseFromApi(): AppUpdateInfo? {
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "KixyuBook/${BuildConfig.VERSION_NAME}")

            when (val responseCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> fetchLatestReleaseFromRedirect()
                else -> error("GitHub 返回 HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchLatestReleaseFromRedirect(): AppUpdateInfo? {
        val connection = URL(LATEST_RELEASE_PAGE).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "HEAD"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "KixyuBook/${BuildConfig.VERSION_NAME}")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307,
                308,
                -> {
                    val releaseUrl = URL(URL(LATEST_RELEASE_PAGE), connection.getHeaderField("Location")).toString()
                    require(releaseUrl.startsWith(RELEASE_TAG_URL_PREFIX)) { "GitHub Release 地址无效" }
                    val encodedTag = releaseUrl.substringAfter(RELEASE_TAG_URL_PREFIX).substringBefore('?')
                    val version = URLDecoder.decode(encodedTag, StandardCharsets.UTF_8.name())
                        .removePrefix("v")
                        .removePrefix("V")
                    require(version.isNotBlank()) { "GitHub Release 缺少版本号" }
                    AppUpdateInfo(
                        versionName = version,
                        releaseName = "Kixyu Book $version",
                        releaseNotes = "新版本已经发布，可前往 GitHub 查看完整发版说明并下载。",
                        releaseUrl = releaseUrl,
                        downloadUrl = fetchApkDownloadUrl(encodedTag),
                    )
                }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> error("GitHub 返回 HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(payload: String): AppUpdateInfo {
        val json = JSONObject(payload)
        val version = json.getString("tag_name").trim().removePrefix("v").removePrefix("V")
        require(version.isNotBlank()) { "Release 缺少版本号" }
        val releaseUrl = json.getString("html_url")
        require(releaseUrl.startsWith(RELEASE_URL_PREFIX)) { "Release 地址无效" }
        val assets = json.optJSONArray("assets")
        val downloadUrl = (0 until (assets?.length() ?: 0))
            .asSequence()
            .mapNotNull { index -> assets?.optJSONObject(index) }
            .firstOrNull { asset -> asset.optString("name").endsWith(".apk", ignoreCase = true) }
            ?.optString("browser_download_url")
            ?.takeIf(::isTrustedApkUrl)
        return AppUpdateInfo(
            versionName = version,
            releaseName = json.optString("name").takeIf { it.isNotBlank() } ?: "Kixyu Book $version",
            releaseNotes = json.optString("body").trim(),
            releaseUrl = releaseUrl,
            downloadUrl = downloadUrl,
        )
    }

    private fun fetchApkDownloadUrl(encodedTag: String): String? {
        val connection = URL("$EXPANDED_ASSETS_URL_PREFIX$encodedTag").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", "KixyuBook/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            APK_ASSET_REGEX.findAll(html)
                .map { match -> "https://github.com${match.groupValues[1].replace("&amp;", "&")}" }
                .firstOrNull(::isTrustedApkUrl)
        } finally {
            connection.disconnect()
        }
    }

    private fun isTrustedApkUrl(url: String): Boolean =
        url.startsWith(APK_DOWNLOAD_URL_PREFIX) && url.substringBefore('?').endsWith(".apk", ignoreCase = true)

    private fun Throwable.toUserMessage(): String = when (this) {
        is java.net.SocketTimeoutException -> "连接 GitHub 超时，请稍后重试"
        is java.net.UnknownHostException -> "无法连接网络，请检查网络设置"
        else -> message?.takeIf { it.isNotBlank() } ?: "检查更新失败"
    }

    private companion object {
        const val LATEST_RELEASE_API = "https://api.github.com/repos/kkyu9527/kixyubook/releases/latest"
        const val LATEST_RELEASE_PAGE = "https://github.com/kkyu9527/kixyubook/releases/latest"
        const val RELEASE_URL_PREFIX = "https://github.com/kkyu9527/kixyubook/releases/"
        const val RELEASE_TAG_URL_PREFIX = "https://github.com/kkyu9527/kixyubook/releases/tag/"
        const val EXPANDED_ASSETS_URL_PREFIX = "https://github.com/kkyu9527/kixyubook/releases/expanded_assets/"
        const val APK_DOWNLOAD_URL_PREFIX = "https://github.com/kkyu9527/kixyubook/releases/download/"
        const val NETWORK_TIMEOUT_MILLIS = 8_000
        val APK_ASSET_REGEX = Regex("href=\\\"(/kkyu9527/kixyubook/releases/download/[^\\\"]+\\.apk(?:\\?[^\\\"]*)?)\\\"")
    }
}

internal fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
    val remote = remoteVersion.toVersionParts() ?: return false
    val current = currentVersion.toVersionParts() ?: return false
    val size = maxOf(remote.size, current.size)
    repeat(size) { index ->
        val comparison = (remote.getOrElse(index) { 0 }).compareTo(current.getOrElse(index) { 0 })
        if (comparison != 0) return comparison > 0
    }
    return false
}

private fun String.toVersionParts(): List<Int>? {
    val stableVersion = trim().removePrefix("v").removePrefix("V").substringBefore('-')
    if (!stableVersion.matches(Regex("\\d+(?:\\.\\d+)*"))) return null
    return stableVersion.split('.').map { part -> part.toIntOrNull() ?: return null }
}
