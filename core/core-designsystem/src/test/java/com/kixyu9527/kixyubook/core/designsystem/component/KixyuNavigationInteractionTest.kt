package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp")
class KixyuNavigationInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val selected = mutableStateOf("home")

    @Test fun materialLensStaysCentredOnTheSelectedTab() = geometry(AppUiStyle.MATERIAL)
    @Test fun miuixLensStaysCentredOnTheSelectedTab() = geometry(AppUiStyle.MIUIX)
    @Test fun rtlLensFollowsTheVisualTabOrder() = geometry(AppUiStyle.MATERIAL, rtl = true)
    @Test fun materialGlassLensStaysCentred() = geometry(AppUiStyle.MATERIAL, glassEnabled = true)
    @Test fun miuixGlassLensStaysCentred() = geometry(AppUiStyle.MIUIX, glassEnabled = true)

    private fun geometry(style: AppUiStyle, rtl: Boolean = false, glassEnabled: Boolean = false) {
        content(style, rtl = rtl, glassEnabled = glassEnabled)
        assertCentred()
        compose.onNodeWithText("Settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Settings").assertIsSelected()
        assertCentred()
        val tabs = compose.onNodeWithTag("navigation_tabs").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val expectedGap = with(compose.density) { KixyuSize.bottomNavigationBottomGap.toPx() }
        assertEquals(expectedGap, root.bottom - tabs.bottom, 1f)
    }

    private fun assertCentred() {
        val tab = compose.onNode(isSelected()).fetchSemanticsNode().boundsInRoot
        val lens = compose.onNodeWithTag("navigation_selection").fetchSemanticsNode().boundsInRoot
        assertEquals(tab.center.x, lens.center.x, 1f)
        assertEquals(tab.center.y, lens.center.y, 1f)
        assertEquals(with(compose.density) { KixyuSize.bottomNavigationIndicatorHeight.toPx() }, lens.height, 1f)
    }

    @Test fun draggingCommitsOneSelectionAndExternalNavigationCanStillUpdateIt() {
        content()
        compose.onNodeWithText("Home").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("Library").assertIsSelected()
        compose.runOnIdle { selected.value = "settings" }
        compose.waitForIdle()
        compose.onNodeWithText("Settings").assertIsSelected()
        assertCentred()
    }

    @Test fun rtlDraggingUsesTheMirroredDirection() {
        content(rtl = true)
        compose.onNodeWithText("Home").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Library").assertIsSelected()
        assertCentred()
    }

    @Test fun cancelledDragRestoresTheSelectedLens() {
        content()
        compose.onNodeWithText("Home").performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
            cancel()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Home").assertIsSelected()
        assertCentred()
    }

    @Test fun largeTextKeepsIconLabelsAccessibleAndLensCentred() {
        content(fontScale = 2f)
        compose.onNodeWithContentDescription("Settings")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Settings").assertIsSelected()
        assertCentred()
    }

    @Test fun disabledNavigationDoesNotRespondToTapsOrDrags() {
        content(enabled = false)
        compose.onNodeWithText("Library").assertIsNotEnabled().performTouchInput { swipeLeft() }
        compose.onNodeWithText("Home").assertIsSelected()
    }

    @Test fun backdropRetainsItsIdentityAndNotifiesGlassWhenThePageChanges() {
        val page = mutableStateOf("First page")
        lateinit var source: KixyuNavigationBackdrop
        compose.setContent {
            source = rememberKixyuNavigationBackdrop(androidx.compose.ui.graphics.Color.White)
            Box(Modifier.fillMaxSize().kixyuNavigationBackdrop(source)) { Text(page.value) }
        }
        compose.waitForIdle()
        val firstSource = source
        var recordings = 0
        lateinit var releaseObservation: () -> Unit
        compose.runOnIdle {
            releaseObservation = source.value.observeSourceChanges { recordings++ }
            page.value = "Next page"
        }
        compose.onNodeWithText("Next page").assertExists()
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(firstSource, source)
            assertTrue("A page update must invalidate the sampled glass", recordings > 0)
            releaseObservation()
        }
    }

    private fun content(
        style: AppUiStyle = AppUiStyle.MATERIAL,
        rtl: Boolean = false,
        fontScale: Float = 1f,
        enabled: Boolean = true,
        glassEnabled: Boolean = false,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                // Test interaction/geometry independently of hardware-only shader rendering.
                KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style, glassEffectEnabled = glassEnabled) {
                    val backdrop = rememberKixyuNavigationBackdrop(androidx.compose.ui.graphics.Color.White)
                    Box(Modifier.fillMaxSize()) {
                        KixyuNavigationBar(
                            items = listOf(
                                KixyuNavigationItem("home", "Home", KixyuSymbols.AutoStories),
                                KixyuNavigationItem("library", "Library", KixyuSymbols.LibraryBooks),
                                KixyuNavigationItem("settings", "Settings", KixyuSymbols.Settings),
                            ),
                            selectedKey = selected.value,
                            onSelected = { selected.value = it.route },
                            backdrop = backdrop,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            enabled = enabled,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
