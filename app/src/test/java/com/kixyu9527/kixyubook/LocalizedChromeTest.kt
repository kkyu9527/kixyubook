package com.kixyu9527.kixyubook

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuContextualAction
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuContextualActionBar
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationBar
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationRail
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuNavigationBackdrop
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.feature.home.R as HomeR
import com.kixyu9527.kixyubook.feature.library.R as LibraryR
import com.kixyu9527.kixyubook.feature.settings.R as SettingsR
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
@Config(sdk = [35], qualifiers = "en-rUS-w840dp-h1200dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalizedChromeTest {
    @get:Rule val compose = createComposeRule()
    private data class Case(val language: String, val scale: Float)

    @Test fun materialNavigationAndSelectionActionsFitAllLanguages() = verify(AppUiStyle.MATERIAL)
    @Test fun miuixNavigationAndSelectionActionsFitAllLanguages() = verify(AppUiStyle.MIUIX)

    private fun verify(style: AppUiStyle) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var case by mutableStateOf(Case("en", 1f))
        var navigations = 0
        var actions = 0
        fun resources(language: String) = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(language))
        }).resources
        val actionIds = listOf(LibraryR.string.library_action_edit, LibraryR.string.library_action_category,
            LibraryR.string.library_action_export, LibraryR.string.library_action_delete)
        compose.setContent {
            CompositionLocalProvider(LocalResources provides resources(case.language), LocalDensity provides Density(1f, case.scale)) {
                KixyuBookTheme(uiStyle = style) {
                    val backdrop = rememberKixyuNavigationBackdrop(MaterialTheme.colorScheme.background)
                    val items = listOf(
                        KixyuNavigationItem("home", stringResource(HomeR.string.home_title), KixyuSymbols.AutoStories),
                        KixyuNavigationItem("library", stringResource(LibraryR.string.library_title), KixyuSymbols.LibraryBooks),
                        KixyuNavigationItem("settings", stringResource(SettingsR.string.settings_title), KixyuSymbols.Settings),
                    )
                    Column(Modifier.width(320.dp)) {
                        KixyuNavigationBar(items, "home", { navigations++ }, backdrop)
                        KixyuNavigationRail(items, "home", { navigations++ }, backdrop)
                        KixyuContextualActionBar(
                            actions = actionIds.map { id -> KixyuContextualAction(id.toString(), stringResource(id), KixyuSymbols.Check) { actions++ } },
                            backdrop = backdrop,
                        )
                    }
                }
            }
        }
        for (language in listOf("zh-Hans", "zh-Hant", "en", "ja")) for (scale in listOf(1f, 1.3f)) {
            val next = Case(language, scale)
            compose.runOnIdle { case = next }
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes().forEach { node ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    layouts.forEach { layout ->
                        val message = "$style $next clipped: ${layout.layoutInput.text}"
                        assertFalse(message, layout.multiParagraph.didExceedMaxLines)
                        assertEquals("$message should remain a single-line label", 1, layout.lineCount)
                        assertTrue(message, layout.multiParagraph.height <= layout.size.height + 1f)
                        repeat(layout.lineCount) { line ->
                            assertFalse(message, layout.isLineEllipsized(line))
                            assertTrue(message, layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
                        }
                    }
                }
            val strings = resources(language)
            compose.onAllNodesWithText(strings.getString(SettingsR.string.settings_title)).onFirst().performClick()
            actionIds.forEach { compose.onNodeWithText(strings.getString(it)).performClick() }
        }
        compose.runOnIdle { assertEquals(8, navigations); assertEquals(32, actions) }
    }
}
