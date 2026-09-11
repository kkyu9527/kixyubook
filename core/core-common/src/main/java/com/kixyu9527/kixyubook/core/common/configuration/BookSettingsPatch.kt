package com.kixyu9527.kixyubook.core.common.configuration

import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import org.json.JSONObject

/** App navigation, glass and system-wide appearance always remain global. */
val BOOK_SETTING_KEYS = setOf("fontSize", "lineHeight", "letterSpacing", "margin", "fontUuid",
    "pageMode", "pageTurnAnimation", "customThemeEnabled", "customDayTheme", "customNightTheme")

fun settingsChanges(before: ReaderSettings, after: ReaderSettings): Map<String, String> {
    val old = settingsToJson(before)
    val updated = settingsToJson(after)
    return (old.keys().asSequence().toSet() + updated.keys().asSequence().toSet()).mapNotNull { key ->
        val value = updated.opt(key) ?: JSONObject.NULL
        if (old.opt(key).toString() == value.toString()) null else key to JSONObject().put(key, value).toString()
    }.toMap()
}

fun applySettingsPatch(global: ReaderSettings, encoded: String?): ReaderSettings {
    if (encoded == null) return global
    val result = settingsToJson(global)
    val patch = JSONObject(encoded)
    patch.keys().forEach { key -> result.put(key, patch.get(key)) }
    return jsonToSettings(result)
}

fun mergeBookSetting(global: ReaderSettings, existing: String, field: String, encodedValue: String): String {
    require(field in BOOK_SETTING_KEYS)
    val patch = JSONObject(existing)
    val value = JSONObject(encodedValue).opt(field) ?: JSONObject.NULL
    val inherited = settingsToJson(global).opt(field) ?: JSONObject.NULL
    val same = if (value is Number && inherited is Number) value.toDouble() == inherited.toDouble()
        else value.toString() == inherited.toString()
    if (same) patch.remove(field) else patch.put(field, value)
    return patch.toString()
}

fun decodeBookSettings(value: JSONObject?): Map<String, String> = value?.keys()?.asSequence()?.associateWith { key ->
    // Only approved reader fields can be restored; never allow a per-book UI/navigation override.
    val source = JSONObject(value.getString(key))
    val safe = JSONObject()
    source.keys().forEach { field -> if (field in BOOK_SETTING_KEYS) safe.put(field, source.get(field)) }
    safe.toString()
}.orEmpty()
