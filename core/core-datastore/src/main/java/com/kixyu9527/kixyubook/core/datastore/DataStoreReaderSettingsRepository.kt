package com.kixyu9527.kixyubook.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.repository.ReaderSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.readerSettingsDataStore by preferencesDataStore(name = "reader_settings")

@Singleton
class DataStoreReaderSettingsRepository @Inject constructor(@param:ApplicationContext private val context: Context) : ReaderSettingsRepository {
    override val settings: Flow<ReaderSettings> = context.readerSettingsDataStore.data.map { values ->
        ReaderSettings(
            fontSize = values[FONT_SIZE] ?: 19f,
            lineHeight = values[LINE_HEIGHT] ?: 1.72f,
            letterSpacing = values[LETTER_SPACING] ?: 0.01f,
            margin = values[MARGIN] ?: 24f,
            theme = values[THEME]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() } ?: ReaderTheme.SYSTEM,
            pageMode = values[PAGE_MODE]?.let { runCatching { PageMode.valueOf(it) }.getOrNull() } ?: PageMode.SCROLL,
            customTheme = CustomReaderTheme(
                values[CUSTOM_BACKGROUND] ?: "#F7F4EC", values[CUSTOM_BODY] ?: "#292722",
                values[CUSTOM_TITLE] ?: "#171713", values[CUSTOM_ACCENT] ?: "#52655A",
            ),
            fontUuid = values[FONT_UUID],
            appColorTheme = values[APP_COLOR_THEME]?.let { runCatching { AppColorTheme.valueOf(it) }.getOrNull() }
                ?: AppColorTheme.DYNAMIC,
        )
    }
    override val readingGoalMinutes: Flow<Int> = context.readerSettingsDataStore.data.map { it[READING_GOAL] ?: 30 }

    override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        val updated = transform(settings.first())
        context.readerSettingsDataStore.edit { values ->
            values[FONT_SIZE] = updated.fontSize; values[LINE_HEIGHT] = updated.lineHeight
            values[LETTER_SPACING] = updated.letterSpacing; values[MARGIN] = updated.margin
            values[THEME] = updated.theme.name; values[PAGE_MODE] = updated.pageMode.name
            values[CUSTOM_BACKGROUND] = updated.customTheme.backgroundHex; values[CUSTOM_BODY] = updated.customTheme.bodyHex
            values[CUSTOM_TITLE] = updated.customTheme.titleHex; values[CUSTOM_ACCENT] = updated.customTheme.accentHex
            updated.fontUuid?.let { values[FONT_UUID] = it } ?: values.remove(FONT_UUID)
            values[APP_COLOR_THEME] = updated.appColorTheme.name
        }
    }

    override suspend fun setReadingGoalMinutes(minutes: Int) {
        context.readerSettingsDataStore.edit { it[READING_GOAL] = minutes.coerceIn(5, 240) }
    }

    private companion object {
        val FONT_SIZE = floatPreferencesKey("font_size"); val LINE_HEIGHT = floatPreferencesKey("line_height")
        val LETTER_SPACING = floatPreferencesKey("letter_spacing"); val MARGIN = floatPreferencesKey("margin")
        val THEME = stringPreferencesKey("theme"); val PAGE_MODE = stringPreferencesKey("page_mode")
        val CUSTOM_BACKGROUND = stringPreferencesKey("custom_background"); val CUSTOM_BODY = stringPreferencesKey("custom_body")
        val CUSTOM_TITLE = stringPreferencesKey("custom_title"); val CUSTOM_ACCENT = stringPreferencesKey("custom_accent")
        val FONT_UUID = stringPreferencesKey("font_uuid"); val READING_GOAL = intPreferencesKey("reading_goal")
        val APP_COLOR_THEME = stringPreferencesKey("app_color_theme")
    }
}
