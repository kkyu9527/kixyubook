package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kixyu9527.kixyubook.core.common.model.CustomReaderTheme
import com.kixyu9527.kixyubook.core.common.model.ReaderColorScheme
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderPaletteTest {
    @Test fun everyPresetHasReadableTextInBothModes() {
        for (scheme in ReaderColorScheme.entries - setOf(ReaderColorScheme.CUSTOM, ReaderColorScheme.DYNAMIC)) {
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

    @Test fun dynamicColoursUseTheSuppliedSystemPaletteAndNeverTheAppAccentChoice() {
        val dynamic = readerPalette(ReaderSettings(readerColorScheme = ReaderColorScheme.GREEN), false)
        val settings = ReaderSettings(readerColorScheme = ReaderColorScheme.DYNAMIC)
        assertEquals(dynamic, readerPalette(settings, false, dynamic))
        assertEquals(readerPalette(ReaderSettings(), false), readerPalette(settings, false))
    }
}
