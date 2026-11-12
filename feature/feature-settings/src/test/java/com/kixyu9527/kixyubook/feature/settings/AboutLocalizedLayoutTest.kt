package com.kixyu9527.kixyubook.feature.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.R as CoreR
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
@Config(sdk = [35], qualifiers = "en-rUS-w840dp-h1200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutLocalizedLayoutTest {
    @get:Rule val compose = createComposeRule()
    private data class Case(val language: String, val width: Int, val scale: Float)

    @Test fun materialActionsFitAndStayClickableInEveryLanguage() = verify(AppUiStyle.MATERIAL)
    @Test fun miuixActionsFitAndStayClickableInEveryLanguage() = verify(AppUiStyle.MIUIX)

    private fun verify(style: AppUiStyle) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var case by mutableStateOf(Case("en", 320, 1f))
        var state: AppUpdateState by mutableStateOf(AppUpdateState.Idle)
        var checks = 0
        var notes = 0
        fun resources(language: String) = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(language))
        }).resources
        compose.setContent {
            CompositionLocalProvider(LocalResources provides resources(case.language), LocalDensity provides Density(1f, case.scale)) {
                KixyuBookTheme(uiStyle = style) {
                    Column(Modifier.width(case.width.dp)) {
                        Box(Modifier.weight(1f)) {
                            AboutRoute(
                                onBack = {}, updateState = state, currentVersion = "3.2.3", onCheckForUpdates = { checks++ },
                                onUpdateResultConsumed = {}, onShowReleaseNotes = { notes++ }, onOpenDiagnosticLog = {},
                                onOpenProjectSource = { true }, onContactTelegram = { true }, appLogo = {},
                            )
                        }
                        KixyuButton(stringResource(CoreR.string.kixyu_done), {}, Modifier.testTag("reference_button"))
                    }
                }
            }
        }
        for (language in listOf("zh-Hans", "zh-Hant", "en", "ja")) {
            for ((width, scale) in listOf(320 to 1f, 320 to 1.3f, 600 to 1.3f)) {
                val next = Case(language, width, scale)
                compose.runOnIdle { case = next; state = AppUpdateState.Idle }
                val strings = resources(language)
                val check = compose.onNodeWithText(strings.getString(R.string.settings_check_update_action))
                check.performScrollTo().performClick()
                val release = compose.onNodeWithText(strings.getString(R.string.settings_release_notes_action))
                release.performScrollTo().performClick()
                compose.onNodeWithContentDescription(strings.getString(R.string.settings_check_update)).assertExists()
                compose.onNodeWithContentDescription(strings.getString(R.string.settings_release_notes)).assertExists()
                val labels = listOf(R.string.settings_check_update_action, R.string.settings_release_notes_action,
                    R.string.settings_checking_update, R.string.settings_about_title).map(strings::getString).toSet()
                assertUnclipped("$style $next", labels)
                val first = check.fetchSemanticsNode().boundsInRoot
                val second = release.fetchSemanticsNode().boundsInRoot
                val reference = compose.onNodeWithTag("reference_button").fetchSemanticsNode().boundsInRoot
                assertEquals("The pair must stay on one row", first.top, second.top, 1f)
                assertEquals("Primary must retain native button height", reference.height, first.height, 1f)
                assertEquals("Secondary must retain native button height", reference.height, second.height, 1f)
                assertEquals("The pair must retain equal widths", first.width, second.width, 1f)
                compose.runOnIdle { state = AppUpdateState.Checking }
                val checking = compose.onNodeWithText(strings.getString(R.string.settings_checking_update))
                checking.performScrollTo().assertIsNotEnabled()
                assertEquals("Checking must not grow the button", reference.height, checking.fetchSemanticsNode().boundsInRoot.height, 1f)
                assertUnclipped("$style $next checking", labels)
            }
        }
        compose.runOnIdle { assertEquals(12, checks); assertEquals(12, notes) }
    }

    private fun assertUnclipped(case: String, labels: Set<String>) {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                layouts.filter { it.layoutInput.text.text in labels }.forEach { layout ->
                    val message = "$case clipped: ${layout.layoutInput.text}"
                    assertFalse(message, layout.multiParagraph.didExceedMaxLines)
                    assertTrue(message, layout.multiParagraph.height <= layout.size.height + 1f)
                    repeat(layout.lineCount) { line ->
                        assertFalse(message, layout.isLineEllipsized(line))
                        assertTrue(message, layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
                    }
                }
            }
    }
}
