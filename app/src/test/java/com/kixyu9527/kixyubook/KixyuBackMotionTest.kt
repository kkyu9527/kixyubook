package com.kixyu9527.kixyubook

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavEntry
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuPredictiveBackEnabled
import com.kixyu9527.kixyubook.core.navigation.AppRoute
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class KixyuBackMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun hostHandlesLocaleWithoutRecreatingItsWindow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val flags = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0).configChanges
        assertTrue(flags and ActivityInfo.CONFIG_LOCALE != 0)
        assertTrue(flags and ActivityInfo.CONFIG_LAYOUT_DIRECTION != 0)
    }

    @Test fun sceneRoleRequiresGestureAndSurvivesCommitUntilSettled() {
        assertEquals(BackSceneRole.NONE, resolveBackSceneRole(BackSceneRole.NONE, true, false, false, true))
        assertEquals(BackSceneRole.PARENT, resolveBackSceneRole(BackSceneRole.NONE, true, true, false, true))
        assertEquals(BackSceneRole.PARENT, resolveBackSceneRole(BackSceneRole.PARENT, true, false, true, true))
        assertEquals(BackSceneRole.NONE, resolveBackSceneRole(BackSceneRole.PARENT, true, false, true, false))
        assertEquals(BackSceneRole.NONE, resolveBackSceneRole(BackSceneRole.PARENT, false, true, false, true))
    }

    @Test fun realGestureIsLinearCancelsCleanlyAndButtonBackDoesNotDrawScrim() {
        val stack = mutableStateListOf<AppRoute>(AppRoute.About, AppRoute.DiagnosticLog)
        compose.setContent {
            CompositionLocalProvider(LocalKixyuPredictiveBackEnabled provides true) {
                KixyuAnimatedNavDisplay(stack, Modifier.fillMaxSize(), { stack.removeAt(stack.lastIndex) }) { route ->
                    NavEntry(route) { Box(Modifier.fillMaxSize().testTag(if (route == AppRoute.About) "parent" else "current")) }
                }
            }
        }
        compose.waitForIdle()
        val width = compose.onNodeWithTag("current").fetchSemanticsNode().size.width.toFloat()
        compose.mainClock.autoAdvance = false
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 300f, .5f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("navigation_gesture_scrim").assertExists()
        val x = compose.onNodeWithTag("current").fetchSemanticsNode().positionInRoot.x
        assertEquals("Half gesture progress must mean half page translation", width * .5f, x, 3f)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("navigation_gesture_scrim").assertDoesNotExist()
        assertEquals(2, stack.size)
        assertEquals(0f, compose.onNodeWithTag("current").fetchSemanticsNode().positionInRoot.x, 1f)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { stack.removeAt(stack.lastIndex) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("navigation_gesture_scrim").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, stack.size)
    }

    @Test fun gestureCommitPopsOnceAndChangingSettingDoesNotAnimateThePage() {
        val stack = mutableStateListOf<AppRoute>(AppRoute.About, AppRoute.DiagnosticLog)
        val enabled = mutableStateOf(true)
        var pops = 0
        compose.setContent {
            CompositionLocalProvider(LocalKixyuPredictiveBackEnabled provides enabled.value) {
                KixyuAnimatedNavDisplay(stack, Modifier.fillMaxSize(), {
                    pops += 1
                    stack.removeAt(stack.lastIndex)
                }) { route ->
                    NavEntry(route) { Box(Modifier.fillMaxSize().testTag(if (route == AppRoute.About) "parent" else "current")) }
                }
            }
        }
        compose.waitForIdle()
        listOf(false, true).forEach { setting ->
            compose.runOnIdle { enabled.value = setting }
            compose.waitForIdle()
            compose.onNodeWithTag("navigation_gesture_scrim").assertDoesNotExist()
            assertEquals(0f, compose.onNodeWithTag("current").fetchSemanticsNode().positionInRoot.x, 1f)
            assertEquals(2, stack.size)
        }
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 300f, .5f, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("navigation_gesture_scrim").assertExists()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, pops)
        assertEquals(listOf(AppRoute.About), stack.toList())
        compose.onNodeWithTag("current").assertDoesNotExist()
        compose.onNodeWithTag("navigation_gesture_scrim").assertDoesNotExist()
    }
}
