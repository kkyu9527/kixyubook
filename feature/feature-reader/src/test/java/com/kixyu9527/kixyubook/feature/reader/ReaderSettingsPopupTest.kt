package com.kixyu9527.kixyubook.feature.reader

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderSettingsGroup
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w412dp-h915dp")
class ReaderSettingsPopupTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aCategoryPopupShowsTheCurrentContentAndBackReturnsToTheMenu() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var backInvoked = false
        compose.setContent {
            KixyuBookTheme {
                ReaderGroupSettingsPopup(
                    group = KixyuReaderSettingsGroup.THEME_SCREEN,
                    state = ReaderUiState(),
                    update = {},
                    onBack = { backInvoked = true },
                    onManageFonts = {},
                    onEditColors = {},
                    previewBrightness = {},
                )
            }
        }

        compose.onAllNodesWithText(context.getString(KixyuReaderSettingsGroup.THEME_SCREEN.titleRes))
            .onFirst()
            .assertExists()
        compose.onAllNodesWithContentDescription(context.getString(R.string.reader_back_to_settings))
            .assertCountEquals(1)
        compose.onAllNodesWithContentDescription(context.getString(R.string.reader_back_to_settings))
            .onFirst()
            .performClick()
        compose.waitForIdle()
        assertTrue("the back arrow must return to the settings menu", backInvoked)
    }

    @Test
    fun theSettingsMenuOffersEveryCategoryAndAsksBeforeResetting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var resetRequested = false
        compose.setContent {
            val backdrop = rememberKixyuNavigationBackdrop(Color.Black)
            KixyuBookTheme {
                ReaderControls(
                    visible = true,
                    toolsMenuVisible = false,
                    settingsMenuVisible = true,
                    controlsBackProgress = { 0f },
                    popupBackProgress = { 0f },
                    bookTitle = "",
                    accentColor = Color.White,
                    backgroundColor = Color.Black,
                    backdrop = backdrop,
                    currentPageBookmarked = false,
                    hasPreviousChapter = true,
                    hasNextChapter = true,
                    onPreviousChapter = {},
                    onNextChapter = {},
                    onExit = {},
                    onDirectory = {},
                    onBookInfo = {},
                    onSettings = {},
                    onTools = {},
                    onSheet = {},
                    onResetReadingConfiguration = { resetRequested = true },
                    onToggleBookmark = {},
                    onSearch = {},
                    canNavigateBack = false,
                    canNavigateForward = false,
                    onNavigateBack = {},
                    onNavigateForward = {},
                )
            }
        }

        compose.onAllNodesWithText(context.getString(KixyuReaderSettingsGroup.INFORMATION.titleRes))
            .onFirst()
            .assertExists()
        compose.onAllNodesWithText(
            context.getString(com.kixyu9527.kixyubook.core.designsystem.R.string.kixyu_reader_reset_group),
        ).onFirst().performClick()
        compose.waitForIdle()
        assertTrue("the menu item must open the confirmation instead of resetting", resetRequested)
    }

    @Test
    fun resettingTheReaderSettingsNeedsASecondConfirmation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var confirmed = false
        compose.setContent {
            KixyuBookTheme {
                ReaderResetReadingSettingsDialog(
                    show = true,
                    onDismissRequest = {},
                    onConfirm = { confirmed = true },
                )
            }
        }

        compose.onAllNodesWithText(context.getString(R.string.reader_reset_settings)).onFirst().assertExists()
        assertTrue("showing the dialog must not reset anything", !confirmed)
        compose.onAllNodesWithText(context.getString(R.string.reader_reset_settings_confirm))
            .onFirst()
            .performClick()
        compose.waitForIdle()
        assertTrue("only the confirmation resets", confirmed)
    }

    @Test
    fun theColourEditorIsItsOwnPopup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            KixyuBookTheme {
                ReaderColorsPopup(
                    settings = ReaderSettings(),
                    update = {},
                    onBack = {},
                )
            }
        }

        compose.onAllNodesWithText(
            context.getString(com.kixyu9527.kixyubook.core.designsystem.R.string.kixyu_custom_colors),
        ).onFirst().assertExists()
        compose.onAllNodesWithContentDescription(context.getString(R.string.reader_back_to_settings))
            .assertCountEquals(1)
    }
}
