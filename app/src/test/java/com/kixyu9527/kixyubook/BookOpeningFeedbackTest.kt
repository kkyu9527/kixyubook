package com.kixyu9527.kixyubook

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuPredictiveBackEnabled
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w800dp-h360dp-land", application = Application::class)
class BookOpeningFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun preparationCanBeDismissedWithoutNavigatingInMaterial() = checkDismiss(AppUiStyle.MATERIAL)
    @Test fun preparationCanBeDismissedWithoutNavigatingInMiuix() = checkDismiss(AppUiStyle.MIUIX)
    @Test fun aSystemBackGestureOnlyCancelsThePendingOpenInMaterial() = checkSystemBack(AppUiStyle.MATERIAL)
    @Test fun aSystemBackGestureOnlyCancelsThePendingOpenInMiuix() = checkSystemBack(AppUiStyle.MIUIX)

    private fun checkDismiss(style: AppUiStyle) {
        var shown by mutableStateOf(true)
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                BookOpeningFeedback(if (shown) BookOpeningState("book", BookPreparationProgress(
                    BookPreparationStage.DOWNLOADING, 50, 100), feedbackVisible = true) else null,
                    onCancel = { shown = false }, onRetry = {})
            }
        }
        compose.onNodeWithText("Preparing book").assertIsDisplayed()
        compose.onNodeWithText("Stay on bookshelf").assertIsDisplayed().performClick()
        compose.onNodeWithText("Preparing book").assertDoesNotExist()
    }

    private fun checkSystemBack(style: AppUiStyle) {
        var shown by mutableStateOf(true)
        var appBacks = 0
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                CompositionLocalProvider(LocalKixyuPredictiveBackEnabled provides true) {
                    BackHandler { appBacks++ }
                    BookOpeningFeedback(if (shown) BookOpeningState("book", feedbackVisible = true) else null,
                        onCancel = { shown = false }, onRetry = {})
                }
            }
        }
        compose.waitForIdle()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(70f, 100f, .6f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(false, shown); assertEquals(0, appBacks) }
    }

    @Test fun preparationFeedbackDoesNotBlockOtherBooksOrShelfControls() {
        var clicks = 0
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                Box(Modifier.fillMaxSize()) {
                    com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton("Other book", { clicks++ },
                        modifier = Modifier.align(Alignment.BottomCenter))
                    BookOpeningFeedback(BookOpeningState("book", feedbackVisible = true), onCancel = {}, onRetry = {})
                }
            }
        }
        compose.onNodeWithText("Other book").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun aFailedPreparationHasAVisibleRetryInAShortWindow() {
        var retries = 0
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                BookOpeningFeedback(BookOpeningState("book", feedbackVisible = true, error = java.io.IOException("Offline")),
                    onCancel = {}, onRetry = { retries++ })
            }
        }
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun cancellingDoesNotClearOrShrinkTheProgressContentDuringItsExitAnimation() {
        var shown by mutableStateOf(true)
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                BookOpeningFeedback(if (shown) BookOpeningState("book", BookPreparationProgress(
                    BookPreparationStage.DOWNLOADING, 50, 100), feedbackVisible = true) else null,
                    onCancel = { shown = false }, onRetry = {})
            }
        }
        compose.onNodeWithText("50%", substring = true).assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Stay on bookshelf").performClick()
        compose.mainClock.advanceTimeBy(16)
        compose.onNodeWithText("50%", substring = true).assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("50%", substring = true).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }
}
