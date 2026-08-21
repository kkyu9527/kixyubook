package com.kixyu9527.kixyubook.core.sync

import com.kixyu9527.kixyubook.core.common.model.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.assertEquals

class CloudReaderSettingsJsonTest {
    @Test
    fun globalReaderSettingsRoundTripIncludesEveryConfigurableField() {
        val expected = ReaderSettings(
            fontSize = 23f,
            lineHeight = 1.9f,
            letterSpacing = .04f,
            margin = 36f,
            theme = ReaderTheme.NIGHT,
            pageMode = PageMode.PAGED,
            pageTurnAnimation = PageTurnAnimation.COVER,
            customThemeEnabled = true,
            customDayTheme = CustomReaderTheme("#102030", "#203040", "#304050", "#405060"),
            customNightTheme = CustomReaderTheme("#506070", "#607080", "#708090", "#8090A0"),
            fontUuid = "custom-font",
            appColorTheme = AppColorTheme.VIOLET,
            appUiStyle = AppUiStyle.MIUIX,
            glassEffectEnabled = false,
            glassFrostLevel = 85f,
            showStatusBar = false,
            hideNavigationBar = false,
            showPageNumber = false,
            volumeKeyPageTurn = true,
            keepScreenOn = false,
            showChapterTitle = false,
            showReadingTime = true,
            showBatteryLevel = true,
            brightnessMode = ReaderBrightnessMode.MANUAL,
            brightness = .72f,
        )

        val actual = jsonToSettings(settingsToJson(expected))

        assertEquals(expected, actual)
    }

    @Test
    fun libraryPreferencesRoundTripIncludesEveryConfigurableField() {
        val expected = LibraryPreferences(
            sortMode = LibrarySortMode.TITLE,
            layoutMode = LibraryLayoutMode.GRID,
            customOrder = listOf("book-b", "book-a"),
            hiddenCategories = setOf("归档", "测试"),
        )

        assertEquals(expected, jsonToLibraryPreferences(libraryPreferencesToJson(expected)))
    }

    @Test
    fun readingReminderRoundTripIncludesEveryConfigurableField() {
        val expected = ReadingReminderSettings(enabled = true, hour = 7, minute = 35)

        assertEquals(expected, jsonToReadingReminder(readingReminderToJson(expected)))
    }

    @Test
    fun settingsPayloadIncludesEveryConfigurationGroup() {
        val reader = ReaderSettings(glassFrostLevel = 85f)
        val library = LibraryPreferences(layoutMode = LibraryLayoutMode.GRID)
        val reminder = ReadingReminderSettings(enabled = true, hour = 7, minute = 35)

        val payload = settingsPayloadJson(
            reader = reader,
            readingGoalMinutes = 55,
            library = library,
            readingReminder = reminder,
            updatedAt = 123L,
        )

        assertEquals(reader, jsonToSettings(payload.getJSONObject("reader")))
        assertEquals(55, payload.getInt("readingGoalMinutes"))
        assertEquals(library, jsonToLibraryPreferences(payload.getJSONObject("library")))
        assertEquals(reminder, jsonToReadingReminder(payload.getJSONObject("readingReminder")))
    }

    @Test
    fun legacyBlurRadiusMigratesToFrostPercentage() {
        val actual = jsonToSettings(JSONObject().put("glassBlurRadius", 20f))

        assertEquals(50f, actual.glassFrostLevel)
    }

    @Test
    fun materialWhiteThemeSurvivesCloudRoundTrip() {
        val expected = ReaderSettings(
            appUiStyle = AppUiStyle.MATERIAL,
            appColorTheme = AppColorTheme.WHITE,
        )

        assertEquals(expected, jsonToSettings(settingsToJson(expected)))
    }
}
