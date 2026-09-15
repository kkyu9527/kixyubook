package com.kixyu9527.kixyubook.core.common.configuration

import com.kixyu9527.kixyubook.core.common.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned portable settings. Shared by manual backup and cloud sync; contains no credentials. */
fun settingsToJson(value: ReaderSettings) = JSONObject()
    .put("fontSize", value.fontSize).put("lineHeight", value.lineHeight).put("letterSpacing", value.letterSpacing)
    .put("margin", value.margin).put("theme", value.theme.name).put("pageMode", value.pageMode.name)
    .put("pageTurnAnimation", value.pageTurnAnimation.name)
    .put("customThemeEnabled", value.customThemeEnabled).put("customDayTheme", customThemeJson(value.customDayTheme))
    .put("customNightTheme", customThemeJson(value.customNightTheme)).put("fontUuid", value.fontUuid)
    .put("appColorTheme", value.appColorTheme.name).put("appUiStyle", value.appUiStyle.name)
    .put("glassEffectEnabled", value.glassEffectEnabled)
    .put("glassFrostLevel", value.glassFrostLevel)
    .put("predictiveBackEnabled", value.predictiveBackEnabled)
    .put("showStatusBar", value.showStatusBar).put("hideNavigationBar", value.hideNavigationBar)
    .put("showPageNumber", value.showPageNumber)
    .put("volumeKeyPageTurn", value.volumeKeyPageTurn).put("keepScreenOn", value.keepScreenOn)
    .put("showChapterTitle", value.showChapterTitle)
    .put("showReadingTime", value.showReadingTime)
    .put("showBatteryLevel", value.showBatteryLevel)
    .put("brightnessMode", value.brightnessMode.name)
    .put("brightness", value.brightness)

fun jsonToSettings(value: JSONObject) = ReaderSettings(
    fontSize = value.sanitizedFloat("fontSize", 19f, 8f..96f),
    lineHeight = value.sanitizedFloat("lineHeight", 1.72f, 1f..4f),
    letterSpacing = value.sanitizedFloat("letterSpacing", .01f, -.1f..0.5f),
    margin = value.sanitizedFloat("margin", 24f, 0f..128f),
    theme = enumValue(value, "theme", ReaderTheme.SYSTEM),
    pageMode = enumValue(value, "pageMode", PageMode.SCROLL),
    pageTurnAnimation = enumValue(value, "pageTurnAnimation", PageTurnAnimation.HORIZONTAL_SLIDE),
    customThemeEnabled = value.optBoolean("customThemeEnabled"),
    customDayTheme = jsonToCustomTheme(value.optJSONObject("customDayTheme"), CustomReaderTheme()),
    customNightTheme = jsonToCustomTheme(value.optJSONObject("customNightTheme"), ReaderSettings().customNightTheme),
    fontUuid = value.optString("fontUuid").takeIf { it.isNotBlank() && it != "null" },
    appColorTheme = enumValue(value, "appColorTheme", AppColorTheme.DEFAULT),
    appUiStyle = enumValue(value, "appUiStyle", AppUiStyle.MATERIAL),
    glassEffectEnabled = value.optBoolean("glassEffectEnabled", true),
    glassFrostLevel = if (value.has("glassFrostLevel")) {
        value.optDouble("glassFrostLevel", DEFAULT_GLASS_FROST_LEVEL.toDouble()).toFloat()
    } else {
        legacyGlassBlurRadiusToFrostLevel(value.optDouble("glassBlurRadius", 22.0).toFloat())
    }.coerceIn(MIN_GLASS_FROST_LEVEL, MAX_GLASS_FROST_LEVEL),
    predictiveBackEnabled = value.optBoolean("predictiveBackEnabled", false),
    showStatusBar = value.optBoolean("showStatusBar", true),
    hideNavigationBar = value.optBoolean("hideNavigationBar", true),
    showPageNumber = value.optBoolean("showPageNumber", true),
    volumeKeyPageTurn = value.optBoolean("volumeKeyPageTurn"),
    keepScreenOn = value.optBoolean("keepScreenOn", true),
    showChapterTitle = value.optBoolean("showChapterTitle", true),
    showReadingTime = value.optBoolean("showReadingTime"),
    showBatteryLevel = value.optBoolean("showBatteryLevel"),
    brightnessMode = enumValue(value, "brightnessMode", ReaderBrightnessMode.SYSTEM),
    brightness = value.optDouble("brightness", .5).toFloat().coerceIn(.05f, 1f),
)

fun filenameRuleSpecToJson(value: FilenameRuleSpec) = JSONObject()
    .put("id", value.id)
    .put("sample", value.sample)
    .put("separator", value.separator ?: JSONObject.NULL)
    .put("roles", JSONArray(value.roles.map { it.name }))
    .put("version", value.version)
    .put("enabled", value.enabled)

fun jsonToFilenameRuleSpec(value: JSONObject): FilenameRuleSpec? = runCatching {
    FilenameRuleSpec(
        id = value.getString("id"),
        sample = value.getString("sample"),
        separator = if (value.isNull("separator")) null else value.getString("separator"),
        roles = value.getJSONArray("roles").toStringList()
            .map { FilenameSegmentRole.valueOf(it) },
        version = value.optInt("version", 1),
        enabled = value.optBoolean("enabled", true),
    )
}.getOrNull()

fun libraryPreferencesToJson(value: LibraryPreferences) = JSONObject()
    .put("sortMode", value.sortMode.name)
    .put("layoutMode", value.layoutMode.name)
    .put("customOrder", JSONArray(value.customOrder))
    .put("hiddenCategories", JSONArray(value.hiddenCategories.toList()))
    .put("filenameRules", JSONArray(value.filenameRules))
    .put("filenameRuleSpecs", JSONArray().apply {
        value.filenameRuleSpecs.forEach { put(filenameRuleSpecToJson(it)) }
    })

fun jsonToLibraryPreferences(value: JSONObject) = LibraryPreferences(
    sortMode = enumValue(value, "sortMode", LibrarySortMode.RECENT),
    layoutMode = enumValue(value, "layoutMode", LibraryLayoutMode.LIST),
    customOrder = value.optJSONArray("customOrder").toStringList(),
    hiddenCategories = value.optJSONArray("hiddenCategories").toStringList().toSet(),
    filenameRules = value.optJSONArray("filenameRules").toStringList(),
    filenameRuleSpecs = value.optJSONArray("filenameRuleSpecs")
        ?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::jsonToFilenameRuleSpec) } }
        .orEmpty(),
)

fun readingReminderToJson(value: ReadingReminderSettings) = JSONObject()
    .put("enabled", value.enabled)
    .put("hour", value.hour)
    .put("minute", value.minute)

fun jsonToReadingReminder(value: JSONObject) = ReadingReminderSettings(
    enabled = value.optBoolean("enabled"),
    hour = value.optInt("hour", 20).coerceIn(0, 23),
    minute = value.optInt("minute", 0).coerceIn(0, 59),
)

fun settingsPayloadJson(
    reader: ReaderSettings,
    readingGoalMinutes: Int,
    library: LibraryPreferences,
    readingReminder: ReadingReminderSettings,
    updatedAt: Long = System.currentTimeMillis(),
) = JSONObject()
    .put("schema", 4)
    .put("updatedAt", updatedAt)
    .put("reader", settingsToJson(reader))
    .put("readingGoalMinutes", readingGoalMinutes)
    .put("library", libraryPreferencesToJson(library))
    .put("readingReminder", readingReminderToJson(readingReminder))

private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else buildList {
    repeat(length()) { index -> optString(index).takeIf(String::isNotBlank)?.let(::add) }
}

fun customThemeJson(value: CustomReaderTheme) = JSONObject()
    .put("background", value.backgroundHex).put("body", value.bodyHex).put("title", value.titleHex).put("accent", value.accentHex)

fun jsonToCustomTheme(value: JSONObject?, fallback: CustomReaderTheme) = value?.let {
    CustomReaderTheme(it.optString("background", fallback.backgroundHex), it.optString("body", fallback.bodyHex),
        it.optString("title", fallback.titleHex), it.optString("accent", fallback.accentHex))
} ?: fallback

inline fun <reified T : Enum<T>> enumValue(json: JSONObject, key: String, fallback: T): T =
    runCatching { enumValueOf<T>(json.optString(key)) }.getOrDefault(fallback)

/** Rejects NaN/Infinity and clamps out-of-range typography so a bad payload cannot break layout. */
private fun JSONObject.sanitizedFloat(key: String, default: Float, range: ClosedFloatingPointRange<Float>): Float {
    val value = optDouble(key, default.toDouble()).toFloat()
    return if (value.isFinite()) value.coerceIn(range) else default
}
