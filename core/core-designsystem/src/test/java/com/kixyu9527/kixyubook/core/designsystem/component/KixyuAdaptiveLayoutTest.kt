package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KixyuAdaptiveLayoutTest {
    @Test
    fun compactPhoneUsesSinglePaneAndBottomNavigation() {
        val size = classifyKixyuWindowSize(width = 412.dp, height = 915.dp)

        assertEquals(KixyuWindowWidthClass.COMPACT, size.widthClass)
        assertFalse(size.isLandscape)
        assertFalse(size.supportsTwoPane)
    }

    @Test
    fun landscapePhoneUsesRailWithoutLabels() {
        val size = classifyKixyuWindowSize(width = 780.dp, height = 412.dp)

        assertEquals(KixyuWindowWidthClass.MEDIUM, size.widthClass)
        assertEquals(KixyuWindowHeightClass.COMPACT, size.heightClass)
        assertTrue(size.isLandscape)
        assertTrue(size.supportsTwoPane)
        assertFalse(size.showNavigationLabels)
    }

    @Test
    fun expandedTabletUsesTwoPaneWithLabels() {
        val size = classifyKixyuWindowSize(width = 1280.dp, height = 800.dp)

        assertEquals(KixyuWindowWidthClass.EXPANDED, size.widthClass)
        assertTrue(size.supportsTwoPane)
        assertTrue(size.showNavigationLabels)
    }

    @Test
    fun splitTabletFallsBackToCompactLayout() {
        val size = classifyKixyuWindowSize(width = 540.dp, height = 800.dp)

        assertEquals(KixyuWindowWidthClass.COMPACT, size.widthClass)
        assertFalse(size.supportsTwoPane)
    }
}
