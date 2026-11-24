package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kixyu9527.kixyubook.core.common.model.CustomReaderTheme
import com.kixyu9527.kixyubook.core.common.model.ReaderColorScheme
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.reader.engine.ReaderRenderPalette
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderPaletteTest {
    @Test fun everyPresetHasReadableTextInBothModes() {
        for (scheme in ReaderColorScheme.entries - setOf(ReaderColorScheme.CUSTOM, ReaderColorScheme.DYNAMIC, ReaderColorScheme.APP)) {
            for (night in listOf(false, true)) {
                val colors = readerPalette(ReaderSettings(readerColorScheme = scheme), night)
                for (foreground in listOf(colors.body, colors.title, colors.secondary, colors.accent)) {
                    val contrast = (maxOf(foreground.luminance(), colors.background.luminance()) + .05f) /
                        (minOf(foreground.luminance(), colors.background.luminance()) + .05f)
                    assertTrue("$scheme night=$night contrast=$contrast", contrast >= 4.5f)
                }
                assertEquals(night, colors.background.luminance() < .5f)
            }
        }
    }

    @Test fun explicitLightOrDarkOverridesTheSystemAndPreservesTheOriginalDefaults() {
        assertEquals(Color.Black, readerPalette(ReaderSettings(theme = ReaderTheme.NIGHT), false).background)
        assertEquals(readerPalette(ReaderSettings(theme = ReaderTheme.DAY), false),
            readerPalette(ReaderSettings(theme = ReaderTheme.DAY), true))
    }

    @Test fun customColoursSurviveSelectingAnotherPaletteAndInvalidColoursFallBackSafely() {
        val settings = ReaderSettings(readerColorScheme = ReaderColorScheme.CUSTOM,
            customDayTheme = CustomReaderTheme(backgroundHex = "#123456", bodyHex = "bad"))
        assertEquals(Color(0xFF123456), readerPalette(settings, false).background)
        assertEquals(readerPalette(ReaderSettings(), false).body, readerPalette(settings, false).body)
        val paper = settings.copy(readerColorScheme = ReaderColorScheme.PAPER)
        assertNotEquals(readerPalette(paper, false), readerPalette(settings, false))
        assertEquals(settings.customDayTheme, paper.customDayTheme)
    }

    @Test fun appSchemeFollowsTheApplicationPaletteIncludingDynamicColour() {
        val appDay = ReaderRenderPalette(
            background = Color(0xFFF1EAD8),
            body = Color(0xFF231F18),
            title = Color(0xFF17130E),
            accent = Color(0xFF6D4C2F),
            secondary = Color(0xFF5F574B),
        )
        val appNight = appDay.copy(
            background = Color(0xFF15120F),
            body = Color(0xFFE7DDCE),
            title = Color(0xFFF6EDE1),
            accent = Color(0xFFD2AC83),
            secondary = Color(0xFFB9AD9D),
        )
        val day = ReaderSettings(readerColorScheme = ReaderColorScheme.APP)
        assertEquals(appDay, readerPalette(day, systemDark = false, app = appDay))
        val night = day.copy(theme = ReaderTheme.NIGHT)
        assertEquals(appNight, readerPalette(night, systemDark = false, app = appNight))
        // Without a supplied app scheme the safe paper fallback is used.
        assertEquals(readerPalette(ReaderSettings(), false), readerPalette(day, false))
    }

    @Test fun dynamicColoursUseTheSuppliedSystemPaletteAndNeverTheAppAccentChoice() {
        val dynamic = readerPalette(ReaderSettings(readerColorScheme = ReaderColorScheme.GREEN), false)
        val settings = ReaderSettings(readerColorScheme = ReaderColorScheme.DYNAMIC)
        assertEquals(dynamic, readerPalette(settings, false, dynamic))
        assertEquals(readerPalette(ReaderSettings(), false), readerPalette(settings, false))
    }
}
