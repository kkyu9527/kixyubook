package com.kixyu9527.kixyubook.update

import com.kixyu9527.kixyubook.BuildConfig
import com.kixyu9527.kixyubook.core.common.model.AppUpdateInfo
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.common.model.ReleaseNotesState
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubUpdateRepository @Inject constructor() : AppUpdateRepository {
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    override val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()
    private val mutableReleaseNotesState = MutableStateFlow<ReleaseNotesState>(ReleaseNotesState.Idle)
    override val releaseNotesState: StateFlow<ReleaseNotesState> = mutableReleaseNotesState.asStateFlow()

    private val checkMutex = Mutex()
    private val releaseNotesMutex = Mutex()

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

    override suspend fun loadReleaseNotes(versionName: String) {
        releaseNotesMutex.withLock {
            mutableReleaseNotesState.value = ReleaseNotesState.Loading
            mutableReleaseNotesState.value = runCatching { fetchRelease(versionName) }.fold(
                onSuccess = { release ->
                    if (release == null) {
                        ReleaseNotesState.Unavailable("GitHub 尚未发布 v$versionName 的 Release Note")
                    } else {
                        ReleaseNotesState.Available(release)
                    }
                },
                onFailure = { error -> ReleaseNotesState.Unavailable(error.toUserMessage()) },
            )
        }
    }

    private fun manualResult(manual: Boolean): AppUpdateState =
        if (manual) AppUpdateState.UpToDate(BuildConfig.VERSION_NAME) else AppUpdateState.Idle

    private suspend fun fetchLatestRelease(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching { fetchLatestReleaseFromApi() }
            .getOrElse {
                runCatching { fetchLatestReleaseFromFeed() }
                    .getOrElse { fetchLatestReleaseFromRedirect() }
                    ?: fetchLatestReleaseFromRedirect()
            }
    }

    private suspend fun fetchRelease(versionName: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val normalizedVersion = versionName.trim().removePrefix("v").removePrefix("V")
        require(normalizedVersion.isNotBlank()) { "当前版本号无效" }
        runCatching {
            fetchReleaseFromApi("v$normalizedVersion")
                ?: fetchReleaseFromApi(normalizedVersion)
        }.getOrElse { apiError ->
            runCatching { fetchReleaseFromFeed(normalizedVersion) }
                .getOrElse { throw apiError }
        }
    }

    private fun fetchReleaseFromApi(tag: String): AppUpdateInfo? {
        val encodedTag = URLEncoder.encode(tag, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val connection = URL("$RELEASE_BY_TAG_API$encodedTag").openConnection() as HttpURLConnection
        return try {
            configureGitHubApiConnection(connection)
            when (val responseCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> error("GitHub 返回 HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchLatestReleaseFromApi(): AppUpdateInfo? {
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        return try {
            configureGitHubApiConnection(connection)

            when (val responseCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> fetchLatestReleaseFromRedirect()
                else -> error("GitHub 返回 HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun configureGitHubApiConnection(connection: HttpURLConnection) {
        connection.requestMethod = "GET"
        connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
        connection.readTimeout = NETWORK_TIMEOUT_MILLIS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "KixyuBook/${BuildConfig.VERSION_NAME}")
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

    /**
     * GitHub's unauthenticated REST quota can be exhausted by users sharing the same public IP.
     * The public Atom feed carries the same release body without consuming that API quota, so it
     * is the preferred fallback before the metadata-only redirect endpoint.
     */
    private fun fetchLatestReleaseFromFeed(): AppUpdateInfo? {
        val connection = URL(RELEASES_FEED).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/atom+xml")
            connection.setRequestProperty("User-Agent", "KixyuBook/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            parseLatestReleaseFeed(connection.inputStream.bufferedReader().use { it.readText() })
                ?.let { release ->
                    val encodedTag = release.releaseUrl
                        .substringAfter(RELEASE_TAG_URL_PREFIX)
                        .substringBefore('?')
                    release.copy(downloadUrl = fetchApkDownloadUrl(encodedTag))
                }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchReleaseFromFeed(versionName: String): AppUpdateInfo? {
        val connection = URL(RELEASES_FEED).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/atom+xml")
            connection.setRequestProperty("User-Agent", "KixyuBook/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            parseReleaseFeed(connection.inputStream.bufferedReader().use { it.readText() }, versionName)
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
        const val RELEASE_BY_TAG_API = "https://api.github.com/repos/kkyu9527/kixyubook/releases/tags/"
        const val LATEST_RELEASE_PAGE = "https://github.com/kkyu9527/kixyubook/releases/latest"
        const val RELEASES_FEED = "https://github.com/kkyu9527/kixyubook/releases.atom"
        const val RELEASE_URL_PREFIX = "https://github.com/kkyu9527/kixyubook/releases/"
        const val RELEASE_TAG_URL_PREFIX = "https://github.com/kkyu9527/kixyubook/releases/tag/"
        const val EXPANDED_ASSETS_URL_PREFIX = "https://github.com/kkyu9527/kixyubook/releases/expanded_assets/"
        const val APK_DOWNLOAD_URL_PREFIX = "https://github.com/kkyu9527/kixyubook/releases/download/"
        const val NETWORK_TIMEOUT_MILLIS = 8_000
        val APK_ASSET_REGEX = Regex("href=\\\"(/kkyu9527/kixyubook/releases/download/[^\\\"]+\\.apk(?:\\?[^\\\"]*)?)\\\"")
    }
}

internal fun parseLatestReleaseFeed(payload: String): AppUpdateInfo? {
    val entry = ATOM_ENTRY_REGEX.find(payload)?.groupValues?.get(1) ?: return null
    return parseReleaseFeedEntry(entry)
}

internal fun parseReleaseFeed(payload: String, versionName: String): AppUpdateInfo? {
    val normalizedVersion = versionName.trim().removePrefix("v").removePrefix("V")
    return ATOM_ENTRY_REGEX.findAll(payload)
        .mapNotNull { match -> parseReleaseFeedEntry(match.groupValues[1]) }
        .firstOrNull { release -> release.versionName.equals(normalizedVersion, ignoreCase = true) }
}

private fun parseReleaseFeedEntry(entry: String): AppUpdateInfo? {
    val releaseUrl = ATOM_RELEASE_URL_REGEX.find(entry)?.groupValues?.get(1)
        ?.decodeXmlEntities()
        ?: return null
    require(releaseUrl.startsWith(RELEASE_TAG_URL)) { "GitHub Release 地址无效" }
    val encodedTag = releaseUrl.substringAfter(RELEASE_TAG_URL).substringBefore('?')
    val version = URLDecoder.decode(encodedTag, StandardCharsets.UTF_8.name())
        .removePrefix("v")
        .removePrefix("V")
    require(version.isNotBlank()) { "GitHub Release 缺少版本号" }
    val releaseName = ATOM_TITLE_REGEX.find(entry)?.groupValues?.get(1)
        ?.decodeXmlEntities()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Kixyu Book $version"
    val releaseNotes = ATOM_CONTENT_REGEX.find(entry)?.groupValues?.get(1)
        ?.let(::githubReleaseHtmlToMarkdown)
        .orEmpty()
    return AppUpdateInfo(
        versionName = version,
        releaseName = releaseName,
        releaseNotes = releaseNotes,
        releaseUrl = releaseUrl,
        downloadUrl = null,
    )
}

internal fun githubReleaseHtmlToMarkdown(encodedHtml: String): String {
    var markdown = encodedHtml.decodeXmlEntities()
    markdown = HTML_LINK_REGEX.replace(markdown) { match ->
        val label = HTML_TAG_REGEX.replace(match.groupValues[2], "").decodeXmlEntities().trim()
        val url = match.groupValues[1].decodeXmlEntities()
        "[$label]($url)"
    }
    markdown = HTML_HEADING_OPEN_REGEX.replace(markdown) { match ->
        "${"#".repeat(match.groupValues[1].toInt())} "
    }
    markdown = HTML_HEADING_CLOSE_REGEX.replace(markdown, "\n\n")
    markdown = HTML_LIST_ITEM_OPEN_REGEX.replace(markdown, "- ")
    markdown = HTML_LIST_ITEM_CLOSE_REGEX.replace(markdown, "\n")
    markdown = HTML_PARAGRAPH_CLOSE_REGEX.replace(markdown, "\n\n")
    markdown = HTML_BREAK_REGEX.replace(markdown, "\n")
    markdown = HTML_STRONG_OPEN_REGEX.replace(markdown, "**")
    markdown = HTML_STRONG_CLOSE_REGEX.replace(markdown, "**")
    markdown = HTML_EMPHASIS_OPEN_REGEX.replace(markdown, "*")
    markdown = HTML_EMPHASIS_CLOSE_REGEX.replace(markdown, "*")
    markdown = HTML_CODE_OPEN_REGEX.replace(markdown, "`")
    markdown = HTML_CODE_CLOSE_REGEX.replace(markdown, "`")
    markdown = HTML_TAG_REGEX.replace(markdown, "")
    return markdown.decodeXmlEntities()
        .replace(NON_BREAKING_SPACE, ' ')
        .replace(EXCESS_BLANK_LINES_REGEX, "\n\n")
        .trim()
}

private fun String.decodeXmlEntities(): String {
    val named = replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
    return NUMERIC_ENTITY_REGEX.replace(named) { match ->
        val value = match.groupValues[1]
        val codePoint = if (value.startsWith('x', ignoreCase = true)) {
            value.drop(1).toIntOrNull(16)
        } else {
            value.toIntOrNull()
        }
        codePoint?.takeIf(Character::isValidCodePoint)
            ?.let { String(Character.toChars(it)) }
            ?: match.value
    }
}

private const val RELEASE_TAG_URL = "https://github.com/kkyu9527/kixyubook/releases/tag/"
private const val NON_BREAKING_SPACE = '\u00A0'
private val ATOM_ENTRY_REGEX = Regex("<entry\\b[^>]*>(.*?)</entry>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val ATOM_RELEASE_URL_REGEX = Regex("href=\"(https://github\\.com/kkyu9527/kixyubook/releases/tag/[^\"]+)\"", RegexOption.IGNORE_CASE)
private val ATOM_TITLE_REGEX = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val ATOM_CONTENT_REGEX = Regex("<content\\b[^>]*type=\"html\"[^>]*>(.*?)</content>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_LINK_REGEX = Regex("<a\\b[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_HEADING_OPEN_REGEX = Regex("<h([1-6])\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HTML_HEADING_CLOSE_REGEX = Regex("</h[1-6]>", RegexOption.IGNORE_CASE)
private val HTML_LIST_ITEM_OPEN_REGEX = Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HTML_LIST_ITEM_CLOSE_REGEX = Regex("</li>", RegexOption.IGNORE_CASE)
private val HTML_PARAGRAPH_CLOSE_REGEX = Regex("</p>", RegexOption.IGNORE_CASE)
private val HTML_BREAK_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val HTML_STRONG_OPEN_REGEX = Regex("<(?:strong|b)\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HTML_STRONG_CLOSE_REGEX = Regex("</(?:strong|b)>", RegexOption.IGNORE_CASE)
private val HTML_EMPHASIS_OPEN_REGEX = Regex("<(?:em|i)\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HTML_EMPHASIS_CLOSE_REGEX = Regex("</(?:em|i)>", RegexOption.IGNORE_CASE)
private val HTML_CODE_OPEN_REGEX = Regex("<code\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HTML_CODE_CLOSE_REGEX = Regex("</code>", RegexOption.IGNORE_CASE)
private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val NUMERIC_ENTITY_REGEX = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")
private val EXCESS_BLANK_LINES_REGEX = Regex("\\n[\\t ]*\\n(?:[\\t ]*\\n)+")

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
