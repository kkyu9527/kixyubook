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
        val library = LibraryPreferences(
            customOrder = listOf("b", "a"),
            hiddenCategories = setOf("private"),
            filenameRules = listOf("(?<author>.+?)的(?<title>.+)"),
            filenameRuleSpecs = listOf(
                FilenameRuleSpec(
                    id = "spec-1",
                    sample = "[精校]《三体》 - 刘慈欣（完本）.txt",
                    separator = "-",
                    roles = listOf(
                        FilenameSegmentRole.IGNORE,
                        FilenameSegmentRole.TITLE,
                        FilenameSegmentRole.AUTHOR,
                        FilenameSegmentRole.IGNORE,
                    ),
                ),
            ),
        )
        val reminder = ReadingReminderSettings(true, 23, 42)
        val serialized = settingsPayloadJson(reader, 50, library, reminder)
        val restored = JSONObject(serialized.toString())
        assertEquals(reader, jsonToSettings(restored.getJSONObject("reader")))
        assertEquals(library, jsonToLibraryPreferences(restored.getJSONObject("library")))
        assertEquals(reminder, jsonToReadingReminder(restored.getJSONObject("readingReminder")))
        assertFalse("the global payload never carries per-book settings", serialized.has("bookOverrides"))
        assertFalse(serialized.has("token")); assertFalse(serialized.has("account"))
    }

    @Test fun anOldPayloadWithBookOverridesRestoresItsGlobalSettingsWithoutFailing() {
        // Backups and cloud snapshots written before per-book settings were removed must stay
        // restorable: the unknown field is ignored, the global configuration still applies.
        val script = JSONObject()
            .put("reader", settingsToJson(ReaderSettings(fontSize = 24f)))
            .put("readingReminder", readingReminderToJson(ReadingReminderSettings(true, 23, 42)))
            .put("bookOverrides", JSONObject().put("book", "{\"fontSize\":30}"))
        assertEquals(24f, jsonToSettings(script.getJSONObject("reader")).fontSize, 0f)
        assertEquals(true, jsonToReadingReminder(script.getJSONObject("readingReminder")).enabled)
    }

    @Test fun hostileReaderPayloadFallsBackForUnknownAnimationAndNonFiniteTypography() {
        val reader = jsonToSettings(
            JSONObject()
                .put("pageTurnAnimation", "FLIP")
                .put("fontSize", "NaN")
                .put("lineHeight", "1e400")
                .put("letterSpacing", -100)
                .put("margin", "1e400"),
        )
        assertEquals(PageTurnAnimation.HORIZONTAL_SLIDE, reader.pageTurnAnimation)
        assertEquals(19f, reader.fontSize, 1e-4f)
        assertEquals(1.72f, reader.lineHeight, 1e-4f)
        assertEquals(-.1f, reader.letterSpacing, 1e-4f)
        assertEquals(24f, reader.margin, 1e-4f)
    }

}
