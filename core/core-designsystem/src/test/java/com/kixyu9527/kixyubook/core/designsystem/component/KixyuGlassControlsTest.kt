package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.designsystem.R
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuGlassFrostLevel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp")
class KixyuGlassControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun materialFrostControlsUpdateTheSharedMaterial() = verify(AppUiStyle.MATERIAL)
    @Test fun miuixFrostControlsUpdateTheSharedMaterial() = verify(AppUiStyle.MIUIX)

    private fun verify(style: AppUiStyle) {
        var settings by mutableStateOf(ReaderSettings(glassEffectEnabled = true))
        var materialLevel = -1f
        compose.setContent {
            KixyuBookTheme(uiStyle = style, glassFrostLevel = settings.glassFrostLevel) {
                materialLevel = LocalKixyuGlassFrostLevel.current
                Column { KixyuGlassEffectControls(settings, { update -> settings = update(settings) }) }
            }
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithText(context.getString(R.string.kixyu_glass_app_wide), substring = true).assertDoesNotExist()
        val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        compose.onNode(slider).performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        compose.onNodeWithText("75%").assertExists()
        compose.runOnIdle {
            assertEquals(75f, settings.glassFrostLevel, .001f)
            assertEquals(75f, materialLevel, .001f)
        }
        compose.onNodeWithText(context.getString(R.string.kixyu_glass_effect)).performClick()
        compose.onNode(slider).assertIsNotEnabled()
    }
}
