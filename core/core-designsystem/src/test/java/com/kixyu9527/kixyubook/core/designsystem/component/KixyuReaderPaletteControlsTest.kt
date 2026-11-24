package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderColorScheme
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.designsystem.R
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp")
class KixyuReaderPaletteControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun materialHasOneSharedPaletteSelector() = verify(AppUiStyle.MATERIAL)
    @Test fun miuixHasOneSharedPaletteSelector() = verify(AppUiStyle.MIUIX)

    private fun verify(style: AppUiStyle) {
        var settings by mutableStateOf(ReaderSettings())
        compose.setContent {
            KixyuBookTheme(uiStyle = style) {
                KixyuReaderColorSchemeControl(settings) { update -> settings = update(settings) }
            }
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithText(context.getString(R.string.kixyu_reader_color_scheme)).performClick()
        compose.onNodeWithText(context.getString(R.string.kixyu_reader_colors_dynamic)).performClick()
        compose.runOnIdle { assertEquals(ReaderColorScheme.DYNAMIC, settings.readerColorScheme) }
        compose.onNodeWithText(context.getString(R.string.kixyu_reader_color_scheme)).performClick()
        compose.onNodeWithText(context.getString(R.string.kixyu_custom_colors)).performClick()
        compose.runOnIdle { assertEquals(ReaderColorScheme.CUSTOM, settings.readerColorScheme) }
    }
}
