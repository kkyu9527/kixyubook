package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuPredictiveBackEnabled
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KixyuPredictiveBackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private enum class Surface { SETTINGS, MENU }
    @Test fun dirtyEditorReturnsToVisibleStateBeforeDiscardConfirmation() {
        lateinit var state: KixyuPredictiveBackState<Unit>
        var requested = false
        compose.setContent {
            CompositionLocalProvider(LocalKixyuPredictiveBackEnabled provides true) {
                state = rememberKixyuPredictiveBackState()
                KixyuPredictiveBackHandler(Unit, state, onBack = { requested = true }, commitAllowed = { false })
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 300f, .7f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(requested); assertEquals(0f, state.progress, .001f) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun materialNestedReturnKeepsOutgoingSettingsHidden() = nestedReturn(AppUiStyle.MATERIAL)

    @Test fun miuixNestedReturnKeepsOutgoingSettingsHidden() = nestedReturn(AppUiStyle.MIUIX)

    private fun nestedReturn(style: AppUiStyle) {
        val target = mutableStateOf<Surface?>(Surface.SETTINGS)
        lateinit var state: KixyuPredictiveBackState<Surface>
        var backs = 0
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                CompositionLocalProvider(LocalKixyuPredictiveBackEnabled provides true) {
                    state = rememberKixyuPredictiveBackState()
                    // Deliberately longer than the old 600 ms cleanup: accessibility animation
                    // scaling and side-panel springs must not reveal an already dismissed layer.
                    AnimatedVisibility(
                        visible = target.value == Surface.SETTINGS,
                        exit = fadeOut(tween(1_600)),
                    ) {
                        Box(Modifier.fillMaxSize().testTag("outgoing_settings")
                            .kixyuPredictivePopupTransform { state.progressFor(Surface.SETTINGS) })
                    }
                    KixyuPredictiveBackHandler(target = target.value, state = state, onBack = {
                        backs += 1
                        target.value = if (it == Surface.SETTINGS) Surface.MENU else null
                    })
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 300f, .7f, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(320)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            assertEquals(Surface.MENU, target.value)
            assertEquals(1, backs)
            assertEquals("Outgoing settings must retain the committed frame", 1f, state.progressFor(Surface.SETTINGS), .001f)
            assertEquals("Incoming menu must start independently", 0f, state.progressFor(Surface.MENU), .001f)
        }
        repeat(50) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertEquals(1f, state.progressFor(Surface.SETTINGS), .001f) }
        }
        compose.onNodeWithTag("outgoing_settings").assertExists()
        // Starting/cancelling the newly revealed menu's gesture must also leave the old layer alone.
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(70f, 300f, .4f, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            assertEquals(.4f, state.progressFor(Surface.MENU), .001f)
            assertEquals(1f, state.progressFor(Surface.SETTINGS), .001f)
            dispatcher.dispatchOnBackCancelled()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0f, state.progressFor(Surface.MENU), .001f)
            assertEquals(1, backs)
            target.value = Surface.SETTINGS
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0f, state.progressFor(Surface.SETTINGS), .001f) }
    }

    @Test fun hardwareBackDoesNotFabricateAGesture() {
        var calls = 0
        var finalProgress = -1f
        compose.setContent {
            CompositionLocalProvider(LocalKixyuPredictiveBackEnabled provides true) {
                val state = rememberKixyuPredictiveBackState<Unit>()
                KixyuPredictiveBackHandler(target = Unit, state = state, onBack = {
                    calls += 1
                    finalProgress = state.progress
                })
            }
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle {
            assertEquals(1, calls)
            assertEquals(0f, finalProgress)
        }
    }

    @Test fun progressOnlyInvalidatesLayerAndReopeningDoesNotInheritCommittedFrame() {
        val target = mutableStateOf<Unit?>(Unit)
        lateinit var state: KixyuPredictiveBackState<Unit>
        var compositions = 0
        compose.setContent {
            CompositionLocalProvider(LocalKixyuPredictiveBackEnabled provides true) {
                state = rememberKixyuPredictiveBackState()
                SideEffect { compositions += 1 }
                Box(Modifier.fillMaxSize().kixyuPredictivePopupTransform { state.progress })
                KixyuPredictiveBackHandler(target = target.value, state = state, onBack = { target.value = null })
            }
        }
        compose.waitForIdle()
        val before = compositions
        compose.mainClock.autoAdvance = false
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 300f, .5f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            assertEquals(.5f, state.progress, .01f)
            assertEquals(before, compositions)
            dispatcher.onBackPressed()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertNotNull("Commit must settle rather than instantly hide", target.value) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            assertNull(target.value)
            assertEquals(1f, state.progress, .01f)
            target.value = Unit
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(0f, state.progress, .01f) }
    }

    @Test fun settleDurationIsBoundedAndUsesOnePolicy() {
        assertEquals(0, kixyuBackSettleSpec(0f).durationMillis)
        assertEquals(KixyuMotion.BackOverlayMinSettleMillis, kixyuBackSettleSpec(.01f).durationMillis)
        assertEquals(KixyuMotion.BackOverlaySettleMillis, kixyuBackSettleSpec(1f).durationMillis)
    }
}
