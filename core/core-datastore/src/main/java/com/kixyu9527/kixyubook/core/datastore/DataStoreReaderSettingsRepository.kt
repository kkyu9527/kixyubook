package com.kixyu9527.kixyubook.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import com.kixyu9527.kixyubook.core.common.repository.SyncEntityType
import com.kixyu9527.kixyubook.core.common.repository.SyncMutationRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.readerSettingsDataStore by preferencesDataStore(name = "reader_settings")

@Singleton
class DataStoreReaderSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val syncMutations: SyncMutationRecorder,
) : ReaderSettingsRepository {
    override val settings: Flow<ReaderSettings> = context.readerSettingsDataStore.data.map { values ->
        val storedTheme = values[THEME]
        ReaderSettings(
            fontSize = values[FONT_SIZE] ?: 19f,
            lineHeight = values[LINE_HEIGHT] ?: 1.72f,
            letterSpacing = values[LETTER_SPACING] ?: 0.01f,
            margin = values[MARGIN] ?: 24f,
            theme = storedTheme?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() } ?: ReaderTheme.SYSTEM,
            pageMode = values[PAGE_MODE]?.let { runCatching { PageMode.valueOf(it) }.getOrNull() } ?: PageMode.SCROLL,
            customThemeEnabled = values[CUSTOM_THEME_ENABLED] ?: (storedTheme == "CUSTOM"),
            customDayTheme = CustomReaderTheme(
                values[CUSTOM_DAY_BACKGROUND] ?: values[LEGACY_CUSTOM_BACKGROUND] ?: "#F7F4EC",
                values[CUSTOM_DAY_BODY] ?: values[LEGACY_CUSTOM_BODY] ?: "#292722",
                values[CUSTOM_DAY_TITLE] ?: values[LEGACY_CUSTOM_TITLE] ?: "#171713",
                values[CUSTOM_DAY_ACCENT] ?: values[LEGACY_CUSTOM_ACCENT] ?: "#52655A",
            ),
            customNightTheme = CustomReaderTheme(
                values[CUSTOM_NIGHT_BACKGROUND] ?: "#11120F",
                values[CUSTOM_NIGHT_BODY] ?: "#D9D9D0",
                values[CUSTOM_NIGHT_TITLE] ?: "#F0F0E7",
                values[CUSTOM_NIGHT_ACCENT] ?: "#B8CCBD",
            ),
            fontUuid = values[FONT_UUID],
            appColorTheme = values[APP_COLOR_THEME]?.let { runCatching { AppColorTheme.valueOf(it) }.getOrNull() }
                ?: AppColorTheme.DEFAULT,
            appUiStyle = values[APP_UI_STYLE]?.let { runCatching { AppUiStyle.valueOf(it) }.getOrNull() }
                ?: AppUiStyle.MATERIAL,
            showStatusBar = values[SHOW_STATUS_BAR] ?: true,
            showPageNumber = values[SHOW_PAGE_NUMBER] ?: true,
            volumeKeyPageTurn = values[VOLUME_KEY_PAGE_TURN] ?: false,
            keepScreenOn = values[KEEP_SCREEN_ON] ?: true,
            showChapterTitle = values[SHOW_CHAPTER_TITLE] ?: true,
        )
    }
    override val readingGoalMinutes: Flow<Int> = context.readerSettingsDataStore.data.map { it[READING_GOAL] ?: 30 }

    override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        val updated = transform(settings.first())
        context.readerSettingsDataStore.edit { values ->
            values[FONT_SIZE] = updated.fontSize; values[LINE_HEIGHT] = updated.lineHeight
            values[LETTER_SPACING] = updated.letterSpacing; values[MARGIN] = updated.margin
            values[THEME] = updated.theme.name; values[PAGE_MODE] = updated.pageMode.name
            values[CUSTOM_THEME_ENABLED] = updated.customThemeEnabled
            values[CUSTOM_DAY_BACKGROUND] = updated.customDayTheme.backgroundHex
            values[CUSTOM_DAY_BODY] = updated.customDayTheme.bodyHex
            values[CUSTOM_DAY_TITLE] = updated.customDayTheme.titleHex
            values[CUSTOM_DAY_ACCENT] = updated.customDayTheme.accentHex
            values[CUSTOM_NIGHT_BACKGROUND] = updated.customNightTheme.backgroundHex
            values[CUSTOM_NIGHT_BODY] = updated.customNightTheme.bodyHex
            values[CUSTOM_NIGHT_TITLE] = updated.customNightTheme.titleHex
            values[CUSTOM_NIGHT_ACCENT] = updated.customNightTheme.accentHex
            updated.fontUuid?.let { values[FONT_UUID] = it } ?: values.remove(FONT_UUID)
            values[APP_COLOR_THEME] = updated.appColorTheme.name
            values[APP_UI_STYLE] = updated.appUiStyle.name
            values[SHOW_STATUS_BAR] = updated.showStatusBar
            values[SHOW_PAGE_NUMBER] = updated.showPageNumber
            values[VOLUME_KEY_PAGE_TURN] = updated.volumeKeyPageTurn
            values[KEEP_SCREEN_ON] = updated.keepScreenOn
            values[SHOW_CHAPTER_TITLE] = updated.showChapterTitle
        }
        syncMutations.record(SyncEntityType.SETTINGS, "global")
    }

    override suspend fun setReadingGoalMinutes(minutes: Int) {
        context.readerSettingsDataStore.edit { it[READING_GOAL] = minutes.coerceIn(5, 240) }
        syncMutations.record(SyncEntityType.SETTINGS, "global")
    }

    private companion object {
        val FONT_SIZE = floatPreferencesKey("font_size"); val LINE_HEIGHT = floatPreferencesKey("line_height")
        val LETTER_SPACING = floatPreferencesKey("letter_spacing"); val MARGIN = floatPreferencesKey("margin")
        val THEME = stringPreferencesKey("theme"); val PAGE_MODE = stringPreferencesKey("page_mode")
        val CUSTOM_THEME_ENABLED = booleanPreferencesKey("custom_theme_enabled")
        val CUSTOM_DAY_BACKGROUND = stringPreferencesKey("custom_day_background")
        val CUSTOM_DAY_BODY = stringPreferencesKey("custom_day_body")
        val CUSTOM_DAY_TITLE = stringPreferencesKey("custom_day_title")
        val CUSTOM_DAY_ACCENT = stringPreferencesKey("custom_day_accent")
        val CUSTOM_NIGHT_BACKGROUND = stringPreferencesKey("custom_night_background")
        val CUSTOM_NIGHT_BODY = stringPreferencesKey("custom_night_body")
        val CUSTOM_NIGHT_TITLE = stringPreferencesKey("custom_night_title")
        val CUSTOM_NIGHT_ACCENT = stringPreferencesKey("custom_night_accent")
        val LEGACY_CUSTOM_BACKGROUND = stringPreferencesKey("custom_background")
        val LEGACY_CUSTOM_BODY = stringPreferencesKey("custom_body")
        val LEGACY_CUSTOM_TITLE = stringPreferencesKey("custom_title")
        val LEGACY_CUSTOM_ACCENT = stringPreferencesKey("custom_accent")
        val FONT_UUID = stringPreferencesKey("font_uuid"); val READING_GOAL = intPreferencesKey("reading_goal")
        val APP_COLOR_THEME = stringPreferencesKey("app_color_theme")
        val APP_UI_STYLE = stringPreferencesKey("app_ui_style")
        val SHOW_STATUS_BAR = booleanPreferencesKey("show_status_bar")
        val SHOW_PAGE_NUMBER = booleanPreferencesKey("show_page_number")
        val VOLUME_KEY_PAGE_TURN = booleanPreferencesKey("volume_key_page_turn")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SHOW_CHAPTER_TITLE = booleanPreferencesKey("show_chapter_title")
    }
}
