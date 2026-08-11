package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog.Category
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.database.entity.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


internal fun bookMetadataJson(book: BookEntity) = JSONObject()
    .put("schema", 1).put("uuid", book.uuid).put("title", book.title).put("author", book.author)
    .put("description", book.description).put("format", book.format).put("createdTime", book.createdTime)
    .put("contentHash", book.contentHash).put("category", book.category)

internal fun progressJson(progress: ReadingProgressEntity, chapterKey: String) = JSONObject()
    .put("schema", 1).put("bookUuid", progress.bookUuid).put("chapterKey", chapterKey)
    .put("paragraphIndex", progress.paragraphIndex).put("charOffset", progress.charOffset)
    .put("progression", progress.fraction).put("quoteAnchor", progress.quoteAnchor)
    .put("updatedTime", progress.updatedTime)

internal fun bookmarksJson(bookUuid: String, values: List<BookmarkRow>, chapters: Map<Long, ChapterEntity>): JSONObject = JSONObject()
    .put("schema", 1)
    .put("bookUuid", bookUuid)
    .put("updatedAt", System.currentTimeMillis())
    .put("items", JSONArray().apply { values.forEach { value ->
        put(JSONObject().put("uuid", value.uuid).put("chapterKey", chapters[value.chapterId]?.chapterKey.orEmpty())
            .put("chapterIndex", value.chapterIndex).put("paragraphIndex", value.position)
            .put("preview", value.preview).put("createdTime", value.createdTime))
    } })

internal fun sessionJson(value: ReadingSessionEntity) = JSONObject()
    .put("schema", 1).put("uuid", value.syncUuid).put("bookUuid", value.bookUuid)
    .put("startedTime", value.startedTime).put("durationMillis", value.durationMillis).put("epochDay", value.epochDay)

internal fun fontJson(value: UserFontEntity) = JSONObject()
    .put("schema", 1).put("uuid", value.uuid).put("name", value.name).put("createdTime", value.createdTime)

internal fun settingsToJson(value: ReaderSettings) = JSONObject()
    .put("fontSize", value.fontSize).put("lineHeight", value.lineHeight).put("letterSpacing", value.letterSpacing)
    .put("margin", value.margin).put("theme", value.theme.name).put("pageMode", value.pageMode.name)
    .put("customThemeEnabled", value.customThemeEnabled).put("customDayTheme", customThemeJson(value.customDayTheme))
    .put("customNightTheme", customThemeJson(value.customNightTheme)).put("fontUuid", value.fontUuid)
    .put("appColorTheme", value.appColorTheme.name).put("appUiStyle", value.appUiStyle.name)
    .put("showStatusBar", value.showStatusBar).put("hideNavigationBar", value.hideNavigationBar)
    .put("showPageNumber", value.showPageNumber)
    .put("volumeKeyPageTurn", value.volumeKeyPageTurn).put("keepScreenOn", value.keepScreenOn)
    .put("showChapterTitle", value.showChapterTitle)

internal fun jsonToSettings(value: JSONObject) = ReaderSettings(
    fontSize = value.optDouble("fontSize", 19.0).toFloat(),
    lineHeight = value.optDouble("lineHeight", 1.72).toFloat(),
    letterSpacing = value.optDouble("letterSpacing", .01).toFloat(),
    margin = value.optDouble("margin", 24.0).toFloat(),
    theme = enumValue(value, "theme", ReaderTheme.SYSTEM),
    pageMode = enumValue(value, "pageMode", PageMode.SCROLL),
    customThemeEnabled = value.optBoolean("customThemeEnabled"),
    customDayTheme = jsonToCustomTheme(value.optJSONObject("customDayTheme"), CustomReaderTheme()),
    customNightTheme = jsonToCustomTheme(value.optJSONObject("customNightTheme"), ReaderSettings().customNightTheme),
    fontUuid = value.optString("fontUuid").takeIf { it.isNotBlank() && it != "null" },
    appColorTheme = enumValue(value, "appColorTheme", AppColorTheme.DEFAULT),
    appUiStyle = enumValue(value, "appUiStyle", AppUiStyle.MATERIAL),
    showStatusBar = value.optBoolean("showStatusBar", true),
    hideNavigationBar = value.optBoolean("hideNavigationBar", true),
    showPageNumber = value.optBoolean("showPageNumber", true),
    volumeKeyPageTurn = value.optBoolean("volumeKeyPageTurn"),
    keepScreenOn = value.optBoolean("keepScreenOn", true),
    showChapterTitle = value.optBoolean("showChapterTitle", true),
)

internal fun libraryPreferencesToJson(value: LibraryPreferences) = JSONObject()
    .put("sortMode", value.sortMode.name)
    .put("layoutMode", value.layoutMode.name)
    .put("customOrder", JSONArray(value.customOrder))
    .put("hiddenCategories", JSONArray(value.hiddenCategories.toList()))

internal fun jsonToLibraryPreferences(value: JSONObject) = LibraryPreferences(
    sortMode = enumValue(value, "sortMode", LibrarySortMode.RECENT),
    layoutMode = enumValue(value, "layoutMode", LibraryLayoutMode.LIST),
    customOrder = value.optJSONArray("customOrder").toStringList(),
    hiddenCategories = value.optJSONArray("hiddenCategories").toStringList().toSet(),
)

internal fun readingReminderToJson(value: ReadingReminderSettings) = JSONObject()
    .put("enabled", value.enabled)
    .put("hour", value.hour)
    .put("minute", value.minute)

internal fun jsonToReadingReminder(value: JSONObject) = ReadingReminderSettings(
    enabled = value.optBoolean("enabled"),
    hour = value.optInt("hour", 20).coerceIn(0, 23),
    minute = value.optInt("minute", 0).coerceIn(0, 59),
)

private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else buildList {
    repeat(length()) { index -> optString(index).takeIf(String::isNotBlank)?.let(::add) }
}

internal fun customThemeJson(value: CustomReaderTheme) = JSONObject()
    .put("background", value.backgroundHex).put("body", value.bodyHex).put("title", value.titleHex).put("accent", value.accentHex)

internal fun jsonToCustomTheme(value: JSONObject?, fallback: CustomReaderTheme) = value?.let {
    CustomReaderTheme(it.optString("background", fallback.backgroundHex), it.optString("body", fallback.bodyHex),
        it.optString("title", fallback.titleHex), it.optString("accent", fallback.accentHex))
} ?: fallback

internal inline fun <reified T : Enum<T>> enumValue(json: JSONObject, key: String, fallback: T): T =
    runCatching { enumValueOf<T>(json.optString(key)) }.getOrDefault(fallback)

internal fun parseBook(json: JSONObject) = SyncedBook(
    uuid = json.getString("uuid"), title = json.optString("title", "未命名书籍"),
    author = json.optString("author", "未知作者"), description = json.optString("description"),
    format = enumValue(json, "format", BookFormat.TXT), createdTime = json.optLong("createdTime"),
    contentHash = json.getString("contentHash"), category = json.optString("category", "未分类"),
)
