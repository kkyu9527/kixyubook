package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kixyu9527.kixyubook.core.common.model.AppColorTheme
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
class KixyuAppearanceControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun themeModeIsSelectedDirectlyFromTheSegmentedControl() {
        var settings by mutableStateOf(ReaderSettings())
        compose.setContent {
            KixyuBookTheme(uiStyle = AppUiStyle.MATERIAL) {
                KixyuThemeModeControl(settings, { transform -> settings = transform(settings) })
            }
        }

        compose.onNodeWithText("Light").performClick().assertIsSelected()
        assertEquals(ReaderTheme.DAY, settings.theme)
    }

    @Test
    fun paletteSwatchUpdatesTheTheme() {
        var settings by mutableStateOf(ReaderSettings())
        compose.setContent {
            KixyuBookTheme(uiStyle = AppUiStyle.MATERIAL) {
                KixyuAppColorControl(settings, { transform -> settings = transform(settings) })
            }
        }

        compose.onNodeWithText("Ocean").performClick().assertIsSelected()
        assertEquals(AppColorTheme.OCEAN, settings.appColorTheme)
    }

    @Test
    fun miuixHidesTheMaterialOnlyWhitePalette() {
        var settings by mutableStateOf(ReaderSettings(appUiStyle = AppUiStyle.MIUIX))
        compose.setContent {
            KixyuBookTheme(uiStyle = AppUiStyle.MIUIX) {
                KixyuAppColorControl(settings, { transform -> settings = transform(settings) })
            }
        }

        compose.onNodeWithText("Pure white").assertDoesNotExist()
    }
}
