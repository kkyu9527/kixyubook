package com.kixyu9527.kixyubook.core.common.configuration

import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import org.json.JSONObject

/** Field-level diff between two settings snapshots, used by the debounced writers. */
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
