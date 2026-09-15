package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.designsystem.R
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Both surfaces render this group, so one assertion locks the single-font-size-editor rule. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w412dp-h915dp")
class KixyuReaderSettingsGroupContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fontAndLayoutGroupOwnsTheOnlyFontSizeEditor() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fontSizeLabel = context.getString(R.string.kixyu_font_size)
        val lineSpacingLabel = context.getString(R.string.kixyu_line_spacing)

        compose.setContent {
            KixyuBookTheme {
                KixyuReaderSettingsGroupContent(
                    group = KixyuReaderSettingsGroup.FONT_LAYOUT,
                    settings = ReaderSettings(),
                    fonts = emptyList(),
                    onSettingsChange = {},
                    onManageFonts = {},
                    onEditColors = {},
                )
            }
        }

        compose.onNodeWithText(lineSpacingLabel).assertExists()
        compose.onAllNodesWithText(fontSizeLabel).assertCountEquals(1)
    }

    @Test
    fun fontGroupOpensTheSharedFontManagementPage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var manageInvoked = false

        compose.setContent {
            KixyuBookTheme {
                KixyuReaderSettingsGroupContent(
                    group = KixyuReaderSettingsGroup.FONT_LAYOUT,
                    settings = ReaderSettings(),
                    fonts = emptyList(),
                    onSettingsChange = {},
                    onManageFonts = { manageInvoked = true },
                    onEditColors = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.kixyu_reading_font)).performClick()
        compose.waitForIdle()
        org.junit.Assert.assertTrue(manageInvoked)
    }

    @Test
    fun themeScreenGroupOwnsTheOnlyBrightnessEditor() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val brightnessLabel = context.getString(R.string.kixyu_reading_brightness)
        val keepScreenOnLabel = context.getString(R.string.kixyu_keep_screen_on)

        compose.setContent {
            KixyuBookTheme {
                KixyuReaderSettingsGroupContent(
                    group = KixyuReaderSettingsGroup.THEME_SCREEN,
                    settings = ReaderSettings(),
                    fonts = emptyList(),
                    onSettingsChange = {},
                    onManageFonts = {},
                    onEditColors = {},
                )
            }
        }

        compose.onNodeWithText(keepScreenOnLabel).assertExists()
        compose.onAllNodesWithText(brightnessLabel).assertCountEquals(1)
    }
}
