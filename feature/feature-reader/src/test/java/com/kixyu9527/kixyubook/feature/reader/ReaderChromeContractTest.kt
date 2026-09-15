package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderSettingsGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChromeContractTest {
    @Test
    fun backAlwaysDismissesTheTopmostReaderSurfaceFirst() {
        val everythingVisible = ReaderChromeState(
            controlsVisible = true,
            toolsMenuVisible = true,
            searchVisible = true,
            bookInfoVisible = true,
            sheet = ReaderSheet.DIRECTORY,
            hasSearchResults = true,
        )

        assertEquals(ReaderPredictiveBackTarget.BOOK_INFO, everythingVisible.predictiveBackTarget())
        assertEquals(
            ReaderPredictiveBackTarget.SHEET,
            everythingVisible.copy(bookInfoVisible = false).predictiveBackTarget(),
        )
        assertEquals(
            ReaderPredictiveBackTarget.SEARCH,
            everythingVisible.copy(bookInfoVisible = false, sheet = null).predictiveBackTarget(),
        )
        assertEquals(
            ReaderPredictiveBackTarget.POPUP_MENU,
            everythingVisible.copy(bookInfoVisible = false, sheet = null, searchVisible = false)
                .predictiveBackTarget(),
        )
        assertEquals(
            ReaderPredictiveBackTarget.CONTROLS,
            ReaderChromeState(controlsVisible = true, hasSearchResults = true).predictiveBackTarget(),
        )
        assertEquals(
            ReaderPredictiveBackTarget.SEARCH_RESULTS,
            ReaderChromeState(hasSearchResults = true).predictiveBackTarget(),
        )
        assertNull(ReaderChromeState().predictiveBackTarget())
    }

    @Test
    fun theCanonicalReaderSettingsOrderIsSharedByEverySurface() {
        // The settings page and the in-reader panel both iterate this list; changing the order or
        // dropping a group must fail here first.
        assertEquals(
            listOf("FONT_LAYOUT", "PAGE_TURN", "THEME_SCREEN", "INFORMATION"),
            KixyuReaderSettingsGroup.entries.map { it.name },
        )
    }

    @Test
    fun theSettingsSheetIsTheOnlySettingsSurfaceAndClosesDirectly() {
        assertEquals(
            ReaderPredictiveBackTarget.SHEET,
            ReaderChromeState(sheet = ReaderSheet.SETTINGS).predictiveBackTarget(),
        )
        assertEquals(
            ReaderPredictiveBackTarget.SHEET,
            ReaderChromeState(sheet = ReaderSheet.DIRECTORY).predictiveBackTarget(),
        )
        assertEquals(listOf("DIRECTORY", "SETTINGS"), ReaderSheet.entries.map { it.name })
    }

    @Test
    fun immersiveReaderHidesBothBarsUntilAnOwnedOverlayAppears() {
        assertEquals(
            ReaderSystemBarVisibility(false, false),
            readerSystemBarVisibility(
                showStatusBar = false,
                hideNavigationBar = true,
                overlayVisible = false,
            ),
        )
        assertEquals(
            ReaderSystemBarVisibility(true, true),
            readerSystemBarVisibility(
                showStatusBar = false,
                hideNavigationBar = true,
                overlayVisible = true,
            ),
        )
    }

    @Test
    fun persistentSystemBarPreferencesAreNotOverriddenWhenReaderIsIdle() {
        assertEquals(
            ReaderSystemBarVisibility(true, true),
            readerSystemBarVisibility(
                showStatusBar = true,
                hideNavigationBar = false,
                overlayVisible = false,
            ),
        )
    }

    @Test
    fun searchResultsAloneDoNotForceSystemBarsVisible() {
        val state = ReaderChromeState(hasSearchResults = true)

        assertFalse(state.overlayVisible)
        assertEquals(ReaderPredictiveBackTarget.SEARCH_RESULTS, state.predictiveBackTarget())
    }
}
