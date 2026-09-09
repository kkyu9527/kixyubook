package com.kixyu9527.kixyubook.core.designsystem.component

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.R
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w412dp-h915dp")
class KixyuLocalizationTest {
    @get:Rule val compose = createComposeRule()

    private fun resources(tag: String): android.content.res.Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        }
        return context.createConfigurationContext(config).resources
    }

    @Test fun regionalLocalesResolveTheCorrectScriptAndUnknownLanguagesFallBackToEnglish() {
        listOf(
            "zh-CN" to "跟随系统",
            "zh-SG" to "跟随系统",
            "zh-Hans" to "跟随系统",
            "zh-TW" to "跟隨系統",
            "zh-HK" to "跟隨系統",
            "zh-Hant" to "跟隨系統",
            "en-US" to "Follow system",
            "ja-JP" to "システムに従う",
            "fr-FR" to "Follow system",
        ).forEach { (tag, expected) ->
            assertEquals(tag, expected, resources(tag).getString(R.string.kixyu_follow_system))
        }
    }

    @Test fun bothThemesUpdateLabelsWithoutRetainingThePreviousLanguage() {
        val language = mutableStateOf("en")
        compose.setContent {
            CompositionLocalProvider(LocalResources provides resources(language.value)) {
                Column {
                    AppUiStyle.entries.forEach { style ->
                        KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                            KixyuThemeModeControl(ReaderSettings(), {})
                        }
                    }
                }
            }
        }
        listOf(
            "en" to "Follow system",
            "zh-Hans" to "跟随系统",
            "zh-Hant" to "跟隨系統",
            "ja" to "システムに従う",
        ).forEach { (tag, expected) ->
            compose.runOnIdle { language.value = tag }
            compose.onAllNodesWithText(expected).assertCountEquals(AppUiStyle.entries.size)
        }
    }

    @Test fun formattedAccessibilityLabelsKeepTheirArgumentsInAllLanguages() {
        listOf("en", "zh-Hans", "zh-Hant", "ja").forEach { tag ->
            val text = resources(tag).getString(R.string.kixyu_decrease_value, "FONT")
            assertEquals(tag, 1, Regex("FONT").findAll(text).count())
        }
    }
}
