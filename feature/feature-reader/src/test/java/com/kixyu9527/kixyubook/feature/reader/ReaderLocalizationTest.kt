package com.kixyu9527.kixyubook.feature.reader

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderLocalizationTest {
    private fun resources(tag: String): android.content.res.Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        }).resources
    }

    @Test fun englishResultCountsUseSingularAndPlural() {
        val resources = resources("en")
        assertEquals("0 matches", resources.getQuantityString(R.plurals.reader_search_result_count, 0, 0))
        assertEquals("1 match", resources.getQuantityString(R.plurals.reader_search_result_count, 1, 1))
        assertEquals("2 matches", resources.getQuantityString(R.plurals.reader_search_result_count, 2, 2))
        assertEquals("Contents · 1 chapter", resources.getQuantityString(R.plurals.reader_directory_title, 1, 1))
    }

    @Test fun bookSearchProgressKeepsCountsAndPercentInAllFourLanguages() {
        listOf("en", "zh-Hans", "zh-Hant", "ja").forEach { tag ->
            val text = resources(tag).getString(R.string.reader_search_scanning_progress, 7, 14, 50)
            org.junit.Assert.assertTrue(tag, text.contains("7/14"))
            org.junit.Assert.assertTrue(tag, text.contains("50%"))
        }
    }

    @Test fun traditionalAndJapaneseReaderActionsAreNotSimplifiedFallbacks() {
        assertEquals("新增目前頁面書籤", resources("zh-TW").getString(R.string.reader_add_page_bookmark))
        assertEquals("このページにしおりを追加", resources("ja").getString(R.string.reader_add_page_bookmark))
        assertEquals("すべて", resources("ja").getString(R.string.reader_annotation_filter_all))
    }
}
