package com.kixyu9527.kixyubook.core.common.configuration

import com.kixyu9527.kixyubook.core.common.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PortableConfigTest {
    @Test fun returningSliderToOriginalValueStillReplacesPendingWrite() {
        val requests = ReaderSettingsRequests()
        val stored = ReaderSettings(fontSize = 19f)
        requests.changes(stored) { it.copy(fontSize = 25f) }
        val back = requests.changes(stored) { it.copy(fontSize = 19f) }
        assertEquals(19f, applySettingsPatch(stored.copy(fontSize = 25f), back.getValue("fontSize")).fontSize)
    }

    @Test fun independentPendingFieldsSurviveDelayedRepositoryEmission() {
        val requests = ReaderSettingsRequests()
        val stored = ReaderSettings(fontSize = 19f)
        requests.changes(stored) { it.copy(fontSize = 25f) }
        val next = requests.changes(stored) { it.copy(lineHeight = 2f) }
        assertEquals(setOf("lineHeight"), next.keys)
        val later = requests.changes(stored.copy(fontSize = 25f)) { it.copy(fontSize = 27f) }
        assertEquals(setOf("fontSize"), later.keys)
    }
    @Test fun backupAndCloudRoundTripAllPortableGroupsWithoutCredentials() {
        val reader = ReaderSettings(fontSize = 25f, glassFrostLevel = 71f, appUiStyle = AppUiStyle.MIUIX)
        val library = LibraryPreferences(customOrder = listOf("b", "a"), hiddenCategories = setOf("private"))
        val reminder = ReadingReminderSettings(true, 23, 42)
        val overrides = mapOf("book" to "{\"fontSize\":27}")
        val serialized = settingsPayloadJson(reader, 50, library, reminder, bookOverrides = overrides)
        val restored = JSONObject(serialized.toString())
        assertEquals(reader, jsonToSettings(restored.getJSONObject("reader")))
        assertEquals(library, jsonToLibraryPreferences(restored.getJSONObject("library")))
        assertEquals(reminder, jsonToReadingReminder(restored.getJSONObject("readingReminder")))
        assertEquals(overrides, decodeBookSettings(restored.getJSONObject("bookOverrides")))
        assertFalse(serialized.has("token")); assertFalse(serialized.has("account"))
    }

    @Test fun bookPatchInheritsUntouchedFieldsAndSupportsExplicitDefaultFont() {
        val global = ReaderSettings(fontSize = 19f, fontUuid = "font", glassFrostLevel = 65f)
        val patch = mergeBookSetting(global, "{}", "fontSize", "{\"fontSize\":25}")
        val cleared = mergeBookSetting(global, patch, "fontUuid", "{\"fontUuid\":null}")
        assertNull(applySettingsPatch(global, cleared).fontUuid)
        val updated = applySettingsPatch(global.copy(lineHeight = 2f, glassFrostLevel = 95f), cleared)
        assertEquals(25f, updated.fontSize); assertEquals(2f, updated.lineHeight); assertEquals(95f, updated.glassFrostLevel)
        assertEquals(global, applySettingsPatch(global, null))
        assertEquals("{}", mergeBookSetting(global, patch, "fontSize", "{\"fontSize\":19}"))
    }

    @Test fun restoredBookOverridesCannotChangeGlobalNavigationOrGlass() {
        val input = JSONObject().put("book", "{\"fontSize\":24,\"predictiveBackEnabled\":true,\"glassFrostLevel\":0}")
        val patch = decodeBookSettings(input).getValue("book")
        assertEquals(setOf("fontSize"), JSONObject(patch).keys().asSequence().toSet())
    }
}
