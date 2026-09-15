package com.kixyu9527.kixyubook.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderSettingsGroup
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w412dp-h915dp")
class FilenameRuleEditorDialogTest {
    @get:Rule val compose = createComposeRule()

    /** A preset writes its roles and its sample at once; the side effect must not re-infer them. */
    @Test
    fun presetRolesSurviveTheComposeSideEffects() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            KixyuBookTheme {
                FilenameRuleEditorDialog(
                    pickedSample = null,
                    onPickFile = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        compose.onAllNodesWithText(context.getString(R.string.settings_filename_rules_preset_title_author))
            .onFirst()
            .performClick()
        compose.waitForIdle()

        val expected = context.getString(R.string.settings_filename_rules_preview, "三体", "刘慈欣")
        assertTrue(
            "the preset's own title/author must still be shown after recomposition",
            compose.onAllNodesWithText(expected).onFirst().assertExists() != null,
        )
    }

    /** Sub-pages are standalone pages: their own toolbar with a title and one back entry. */
    @Test
    fun groupPageIsAFullPageWithItsOwnToolbar() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var backInvoked = false
        compose.setContent {
            KixyuBookTheme {
                ReadingGroupSettingsPage(
                    group = KixyuReaderSettingsGroup.FONT_LAYOUT,
                    settings = ReaderSettings(),
                    fonts = emptyList(),
                    onBack = { backInvoked = true },
                    onSettingsChange = {},
                    onManageFonts = {},
                    onEditColors = {},
                )
            }
        }

        compose.onAllNodesWithText(context.getString(KixyuReaderSettingsGroup.FONT_LAYOUT.titleRes))
            .onFirst()
            .assertExists()
        compose.onAllNodesWithContentDescription(context.getString(R.string.settings_back))
            .assertCountEquals(1)
        compose.onAllNodesWithContentDescription(context.getString(R.string.settings_back))
            .onFirst()
            .performClick()
        compose.waitForIdle()
        assertTrue(backInvoked)
    }

    @Test
    fun colorsPageIsAFullPageWithTheColorEditor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var backInvoked = false
        compose.setContent {
            KixyuBookTheme {
                ReadingColorsPage(
                    settings = ReaderSettings(),
                    onBack = { backInvoked = true },
                    onSettingsChange = {},
                )
            }
        }

        val title = context.getString(
            com.kixyu9527.kixyubook.core.designsystem.R.string.kixyu_custom_colors,
        )
        compose.onAllNodesWithText(title).onFirst().assertExists()
        compose.onAllNodesWithContentDescription(context.getString(R.string.settings_back))
            .onFirst()
            .performClick()
        compose.waitForIdle()
        assertTrue(backInvoked)
    }
}
