package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KixyuAdaptiveLayoutTest {
    @Test
    fun compactPortraitUsesBottomNavigationAndSinglePane() {
        val window = classifyKixyuWindowSize(412.dp, 915.dp)
        assertFalse(window.usesNavigationRail)
        assertFalse(window.supportsTwoPane)
    }

    @Test
    fun compactLandscapeUsesRailButRemainsSinglePane() {
        val window = classifyKixyuWindowSize(590.dp, 400.dp)
        assertTrue(window.usesNavigationRail)
        assertFalse(window.supportsTwoPane)
    }

    @Test
    fun expandedPortraitKeepsBottomNavigationAndSupportsTwoPane() {
        val window = classifyKixyuWindowSize(1000.dp, 1400.dp)
        assertFalse(window.usesNavigationRail)
        assertTrue(window.supportsTwoPane)
    }

    @Test
    fun navigationLabelsCollapseBeforeLargeTextCanClipActions() {
        assertTrue(kixyuNavigationShowsLabels(1f))
        assertTrue(kixyuNavigationShowsLabels(1.3f))
        assertFalse(kixyuNavigationShowsLabels(1.31f))
        assertFalse(kixyuNavigationShowsLabels(2f))
    }
}
