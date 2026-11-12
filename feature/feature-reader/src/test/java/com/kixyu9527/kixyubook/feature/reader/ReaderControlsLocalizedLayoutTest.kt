package com.kixyu9527.kixyubook.feature.reader

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderSettingsGroup
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w320dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderControlsLocalizedLayoutTest {
    @get:Rule val compose = createComposeRule()
    private data class Case(val language: String, val scale: Float, val settings: Boolean)

    @Test fun materialMenusKeepTheirLabelsAndActions() = verify(AppUiStyle.MATERIAL)
    @Test fun miuixMenusKeepTheirLabelsAndActions() = verify(AppUiStyle.MIUIX)

    private fun verify(style: AppUiStyle) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var case by mutableStateOf(Case("en", 1f, false))
        var bookmarks = 0
        var settings = 0
        fun resources(language: String) = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(language))
        }).resources
        compose.setContent {
            CompositionLocalProvider(LocalResources provides resources(case.language), LocalDensity provides Density(1f, case.scale)) {
                KixyuBookTheme(uiStyle = style) {
                    val color = MaterialTheme.colorScheme.background
                    ReaderControls(
                        visible = true, toolsMenuVisible = !case.settings, settingsMenuVisible = case.settings,
                        controlsBackProgress = { 0f }, popupBackProgress = { 0f }, bookTitle = "Book",
                        accentColor = MaterialTheme.colorScheme.primary, backgroundColor = color,
                        backdrop = rememberKixyuNavigationBackdrop(color), currentPageBookmarked = false,
                        hasPreviousChapter = true, hasNextChapter = true, onPreviousChapter = {}, onNextChapter = {},
                        onExit = {}, onDirectory = {}, onBookInfo = {}, onSettings = {}, onTools = {},
                        onSheet = { settings++ }, onResetReadingConfiguration = {}, onToggleBookmark = { bookmarks++ },
                        onSearch = {}, canNavigateBack = true, canNavigateForward = true,
                        onNavigateBack = {}, onNavigateForward = {},
                    )
                }
            }
        }
        for (language in listOf("zh-Hans", "zh-Hant", "en", "ja")) for (scale in listOf(1f, 1.3f)) {
            for (settingsMenu in listOf(false, true)) {
                val next = Case(language, scale, settingsMenu)
                compose.runOnIdle { case = next }
                compose.mainClock.advanceTimeBy(600)
                compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                    .fetchSemanticsNodes().forEach { node ->
                        val layouts = mutableListOf<TextLayoutResult>()
                        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                        layouts.filterNot { it.layoutInput.text.text == "Book" }.forEach { layout ->
                            val message = "$style $next clipped: ${layout.layoutInput.text}"
                            assertFalse(message, layout.multiParagraph.didExceedMaxLines)
                            assertTrue(message, layout.multiParagraph.height <= layout.size.height + 1f)
                            assertEquals("$message must stay on one line", 1, layout.lineCount)
                            assertFalse(message, layout.isLineEllipsized(0))
                            assertTrue(message, layout.getLineRight(0) - layout.getLineLeft(0) <= layout.size.width + 1f)
                        }
                    }
                val target = if (settingsMenu) KixyuReaderSettingsGroup.FONT_LAYOUT.titleRes else R.string.reader_add_page_bookmark
                compose.onNodeWithText(resources(language).getString(target)).performClick()
            }
        }
        compose.runOnIdle { assertEquals(8, bookmarks); assertEquals(8, settings) }
    }
}
