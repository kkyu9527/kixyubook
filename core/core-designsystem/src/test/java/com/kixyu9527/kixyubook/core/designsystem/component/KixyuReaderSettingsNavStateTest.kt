package com.kixyu9527.kixyubook.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KixyuReaderSettingsNavStateTest {
    @Test fun backPopsExactlyOneLevelThenDismisses() {
        val nav = KixyuReaderSettingsNavState()
        assertFalse("nothing open still asks the caller to dismiss", nav.back())

        nav.open(KixyuReaderSettingsGroup.THEME_SCREEN)
        nav.openColors()
        assertTrue(nav.back())
        assertEquals(KixyuReaderSettingsGroup.THEME_SCREEN, nav.openGroup)
        assertFalse(nav.colorsOpen)

        assertTrue(nav.back())
        assertNull(nav.openGroup)
        assertFalse(nav.back())
    }

    @Test fun openingAGroupAlwaysStartsAtLevelTwo() {
        val nav = KixyuReaderSettingsNavState()
        nav.open(KixyuReaderSettingsGroup.THEME_SCREEN)
        nav.openColors()
        nav.open(KixyuReaderSettingsGroup.INFORMATION)
        assertFalse(nav.colorsOpen)
        assertEquals(KixyuReaderSettingsGroup.INFORMATION, nav.openGroup)
    }

    @Test fun eachGroupResetsOnlyItsOwnFields() {
        val modified = com.kixyu9527.kixyubook.core.common.model.ReaderSettings(
            fontSize = 28f,
            lineHeight = 2.2f,
            pageMode = com.kixyu9527.kixyubook.core.common.model.PageMode.PAGED,
            theme = com.kixyu9527.kixyubook.core.common.model.ReaderTheme.NIGHT,
            volumeKeyPageTurn = true,
            showBatteryLevel = true,
        )
        val layoutReset = KixyuReaderSettingsGroup.FONT_LAYOUT.resetToDefaults(modified)
        assertEquals(19f, layoutReset.fontSize, 0f)
        assertEquals(1.72f, layoutReset.lineHeight, 0f)
        assertEquals(com.kixyu9527.kixyubook.core.common.model.ReaderTheme.NIGHT, layoutReset.theme)

        val pageReset = KixyuReaderSettingsGroup.PAGE_TURN.resetToDefaults(modified)
        assertEquals(false, pageReset.volumeKeyPageTurn)
        assertEquals(28f, pageReset.fontSize, 0f)

        val infoReset = KixyuReaderSettingsGroup.INFORMATION.resetToDefaults(modified)
        assertEquals(false, infoReset.showBatteryLevel)
        assertEquals(2.2f, infoReset.lineHeight, 0f)
    }
}
