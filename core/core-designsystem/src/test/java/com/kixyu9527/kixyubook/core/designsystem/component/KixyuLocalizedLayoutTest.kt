package com.kixyu9527.kixyubook.core.designsystem.component

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.designsystem.R
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tests measured text, not just the complete (but potentially clipped) semantics string. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w840dp-h1200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KixyuLocalizedLayoutTest {
    @get:Rule val compose = createComposeRule()
    private data class Case(val language: String, val width: Int, val fontScale: Float, val page: Int)

    @Test fun materialControlsKeepEveryLocalizedLabel() = verify(AppUiStyle.MATERIAL)
    @Test fun miuixControlsKeepEveryLocalizedLabel() = verify(AppUiStyle.MIUIX)

    private fun verify(style: AppUiStyle) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var case by mutableStateOf(Case("en", 320, 1f, 0))
        compose.setContent {
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(case.language))
            }).resources
            CompositionLocalProvider(LocalResources provides resources, LocalDensity provides Density(1f, case.fontScale)) {
                KixyuBookTheme(uiStyle = style) {
                    if (case.page > KixyuReaderSettingsGroup.entries.size) {
                        Box(Modifier.width(case.width.dp).height(900.dp)) {
                            KixyuPageScaffold(
                                title = stringResource(KixyuReaderSettingsGroup.entries[case.page - 5].titleRes),
                                navigationIcon = { KixyuIconButton({}) { Icon(KixyuSymbols.ArrowBack, null) } },
                            ) { Box(Modifier) }
                        }
                    } else Column(Modifier.width(case.width.dp).verticalScroll(rememberScrollState())) {
                        if (case.page < KixyuReaderSettingsGroup.entries.size) {
                            KixyuReaderSettingsGroupContent(
                                group = KixyuReaderSettingsGroup.entries[case.page],
                                settings = ReaderSettings(),
                                fonts = emptyList(),
                                onSettingsChange = {},
                                onManageFonts = {},
                                onEditColors = {},
                            )
                        } else {
                            KixyuSection(title = stringResource(R.string.kixyu_reader_group_page_turn)) {
                                KixyuThemeModeControl(ReaderSettings(), {})
                                KixyuAppColorControl(ReaderSettings(), {})
                                KixyuButton(stringResource(R.string.kixyu_done), {})
                                KixyuSecondaryButton(stringResource(R.string.kixyu_cancel), {})
                                KixyuTextButton(stringResource(R.string.kixyu_edit), {})
                            }
                        }
                    }
                }
            }
        }
        for (language in listOf("zh-Hans", "zh-Hant", "en", "ja")) {
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(language))
            }).resources
            // Functional labels must be complete. Long secondary explanations and user-data
            // previews keep the existing compact-row policy; this is not a prose-layout test.
            val labels = listOf(
                R.string.kixyu_reading_font, R.string.kixyu_system_default, R.string.kixyu_font_size,
                R.string.kixyu_line_spacing, R.string.kixyu_letter_spacing, R.string.kixyu_page_margin,
                R.string.kixyu_reading_mode, R.string.kixyu_page_turn_animation, R.string.kixyu_volume_page_turn,
                R.string.kixyu_mode_scroll, R.string.kixyu_mode_paged, R.string.kixyu_mode_slide, R.string.kixyu_mode_cover,
                R.string.kixyu_custom_colors, R.string.kixyu_reading_brightness, R.string.kixyu_follow_system,
                R.string.kixyu_automatic, R.string.kixyu_keep_screen_on, R.string.kixyu_glass_effect,
                R.string.kixyu_display_mode_system, R.string.kixyu_theme_day, R.string.kixyu_theme_night,
                R.string.kixyu_palette_default, R.string.kixyu_palette_white, R.string.kixyu_palette_dynamic,
                R.string.kixyu_palette_sage, R.string.kixyu_palette_ocean, R.string.kixyu_palette_violet, R.string.kixyu_palette_amber,
                R.string.kixyu_show_status_bar, R.string.kixyu_show_navigation_bar, R.string.kixyu_show_chapter_title,
                R.string.kixyu_show_page_number, R.string.kixyu_show_time, R.string.kixyu_show_battery,
                R.string.kixyu_reader_group_system_bars, R.string.kixyu_reader_group_page_info,
            ).plus(KixyuReaderSettingsGroup.entries.map { it.titleRes }).map(localized::getString).toSet()
            for ((width, scale) in listOf(320 to 1f, 320 to 1.3f, 600 to 1.3f)) {
                for (page in 0..KixyuReaderSettingsGroup.entries.size * 2) {
                    val next = Case(language, width, scale, page)
                    compose.runOnIdle { case = next }
                    val nodes = compose.onAllNodes(
                        SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes()
                    assertTrue("$style $next must render labels", nodes.isNotEmpty())
                    nodes.forEach { node ->
                        val layouts = mutableListOf<TextLayoutResult>()
                        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                        layouts.filter { it.layoutInput.text.text in labels }.forEach { layout ->
                            val message = "$style $next clipped: ${layout.layoutInput.text}"
                            // Paragraph line boxes can retain maxWidth after the text node is
                            // intrinsically sized. Check actual line extents, not that allocation.
                            assertFalse(message, layout.multiParagraph.didExceedMaxLines)
                            assertTrue("$message height=${layout.multiParagraph.height} size=${layout.size} constraints=${layout.layoutInput.constraints}", layout.multiParagraph.height <= layout.size.height + 1f)
                            repeat(layout.lineCount) { line ->
                                assertFalse(message, layout.isLineEllipsized(line))
                                assertTrue(message, layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
                            }
                            assertTrue("$message should fit one line in its existing slot", layout.lineCount == 1)
                        }
                    }
                }
            }
        }
    }
}
