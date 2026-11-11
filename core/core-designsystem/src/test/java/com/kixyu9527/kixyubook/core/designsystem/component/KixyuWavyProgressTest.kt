package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KixyuWavyProgressTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun determinateProgressPublishesRange() {
        compose.setContent {
            KixyuBookTheme(uiStyle = AppUiStyle.MATERIAL) {
                KixyuWavyProgressBar(progress = .42f)
            }
        }

        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(.42f, 0f..1f)))
            .assertIsDisplayed()
    }

    @Test
    fun sharedProgressBarRoutesWavyRequestsToTheWaveImplementation() {
        compose.setContent {
            KixyuBookTheme(uiStyle = AppUiStyle.MATERIAL) {
                KixyuProgressBar(progress = .42f, wavy = true)
            }
        }

        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(.42f, 0f..1f)))
            .assertIsDisplayed()
    }

    @Test
    fun indeterminateProgressPublishesIndeterminateRange() {
        compose.setContent {
            KixyuBookTheme(uiStyle = AppUiStyle.MIUIX) {
                KixyuWavyProgressBar(progress = null)
            }
        }

        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }
}
