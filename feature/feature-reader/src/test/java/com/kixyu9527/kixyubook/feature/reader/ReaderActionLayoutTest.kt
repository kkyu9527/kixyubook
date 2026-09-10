package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h720dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderActionLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothThemesKeepFourLanguageLabelsReadableAtDoubleFontScale() {
        var style by mutableStateOf(AppUiStyle.MATERIAL)
        var label by mutableStateOf("返回操作")
        var clicks = 0
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    ReaderTextActionButton(label, KixyuSymbols.ArrowBack) { clicks++ }
                }
            }
        }
        for (theme in listOf(AppUiStyle.MATERIAL, AppUiStyle.MIUIX)) {
            for (translation in listOf("返回操作", "返回操作選單", "Back to actions", "操作に戻る")) {
                compose.runOnIdle { style = theme; label = translation }
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(translation, useUnmergedTree = true)
                    .assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue("$theme: $translation", layouts.isNotEmpty())
                assertTrue("$theme: $translation", layouts.none { it.hasVisualOverflow })
                compose.onNodeWithText(translation).assertHasClickAction().performClick()
            }
        }
        compose.runOnIdle { assertEquals(8, clicks) }
    }
}
