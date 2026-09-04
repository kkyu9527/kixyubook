package com.kixyu9527.kixyubook.feature.reader

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
            menuVisible = true,
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
    fun allThreeSettingsSheetsReturnToTheReaderSettingsMenu() {
        assertTrue(ReaderSheet.THEME.returnsToSettingsMenu())
        assertTrue(ReaderSheet.LAYOUT.returnsToSettingsMenu())
        assertTrue(ReaderSheet.INFORMATION.returnsToSettingsMenu())
        assertFalse(ReaderSheet.DIRECTORY.returnsToSettingsMenu())
        assertFalse(null.returnsToSettingsMenu())
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
