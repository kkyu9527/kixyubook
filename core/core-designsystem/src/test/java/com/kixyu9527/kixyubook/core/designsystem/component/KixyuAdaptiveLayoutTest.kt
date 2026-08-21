package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KixyuAdaptiveLayoutTest {
    @Test
    fun floatingNavigationKeepsKsuGeometry() {
        assertEquals(76.dp, KixyuSize.bottomNavigationItemWidth)
        assertEquals(64.dp, KixyuSize.bottomNavigationBarHeight)
        assertEquals(56.dp, KixyuSize.bottomNavigationIndicatorHeight)
        assertEquals(4.dp, KixyuSize.bottomNavigationInnerPadding)
        assertEquals(12.dp, KixyuSize.bottomNavigationBottomGap)
        assertEquals(24.dp, KixyuSize.floatingSurfaceBottomGap)
        assertEquals(32.dp, KixyuSize.navigationContainerCornerRadius)
        assertEquals(236.dp, KixyuSize.bottomNavigationDefaultWidth)
    }

    @Test
    fun simulatedPopupFrostKeepsExactEndpointsAndCompensatesMidrange() {
        assertEquals(0f, 0f.kixyuSimulatedFrostFraction(), 0f)
        assertEquals(1f, 100f.kixyuSimulatedFrostFraction(), 0f)
        assertTrue(55f.kixyuSimulatedFrostFraction() > .75f)
    }

    @Test
    fun popupFrostUsesTheSharedLinearVeilWhenItsWindowCanBlur() {
        assertEquals(0f, 0f.kixyuPopupFrostFraction(windowBlurred = true), 0f)
        assertEquals(.55f, 55f.kixyuPopupFrostFraction(windowBlurred = true), 0f)
        assertEquals(1f, 100f.kixyuPopupFrostFraction(windowBlurred = true), 0f)
    }

    @Test
    fun popupBackdropPolicySeparatesSurfaceGlassFromWholeWindowBlur() {
        assertFalse(KixyuPopupBackdropEffect.SURFACE_ONLY.blursWindow)
        assertTrue(KixyuPopupBackdropEffect.BLUR_BEHIND.blursWindow)
    }

    @Test
    fun floatingNavigationDragFollowsDirectionAndClampsToTabs() {
        assertEquals(
            1.5f,
            updateNavigationDragPosition(
                currentPosition = 1f,
                dragAmountPx = 38f,
                itemWidthPx = 76f,
                itemCount = 3,
                isLtr = true,
            ),
        )
        assertEquals(
            0.5f,
            updateNavigationDragPosition(
                currentPosition = 1f,
                dragAmountPx = 38f,
                itemWidthPx = 76f,
                itemCount = 3,
                isLtr = false,
            ),
        )
        assertEquals(
            2f,
            updateNavigationDragPosition(1f, 500f, 76f, 3, isLtr = true),
        )
        assertEquals(
            0f,
            updateNavigationDragPosition(1f, -500f, 76f, 3, isLtr = true),
        )
    }

    @Test
    fun floatingNavigationDragSnapsToNearestTab() {
        assertEquals(0, navigationDragTargetIndex(0.49f, 3))
        assertEquals(1, navigationDragTargetIndex(0.5f, 3))
        assertEquals(2, navigationDragTargetIndex(1.6f, 3))
    }

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
