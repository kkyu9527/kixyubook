package com.kixyu9527.kixyubook

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageBackSpec
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationBackTransitionActive
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuPredictiveBackEnabled
import com.kixyu9527.kixyubook.core.navigation.AppRoute

/** Keeps ordinary Back handling available when gesture-progress animation is disabled. */
@Composable
internal fun KixyuNavigationBackHandler(onBack: () -> Unit) {
    BackHandler(
        enabled = !LocalKixyuPredictiveBackEnabled.current,
        onBack = onBack,
    )
}

private data class WindowCornerRadii(
    val topLeft: Int = 0,
    val topRight: Int = 0,
    val bottomRight: Int = 0,
    val bottomLeft: Int = 0,
) {
    val hasRoundedCorners: Boolean
        get() = topLeft > 0 || topRight > 0 || bottomRight > 0 || bottomLeft > 0
}

private fun View.readWindowCornerRadii(): WindowCornerRadii {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return WindowCornerRadii()
    val insets = rootWindowInsets ?: return WindowCornerRadii()
    return WindowCornerRadii(
        topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0,
        topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0,
        bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
        bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
    )
}

@Composable
private fun rememberWindowCornerRadii(view: View): WindowCornerRadii {
    var radii by remember(view) { mutableStateOf(view.readWindowCornerRadii()) }
    DisposableEffect(view) {
        val updateRadii = { radii = view.readWindowCornerRadii() }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateRadii()
        }
        view.addOnLayoutChangeListener(layoutListener)
        view.post(updateRadii)
        onDispose { view.removeOnLayoutChangeListener(layoutListener) }
    }
    return radii
}

@Composable
internal fun rememberKixyuPredictiveBackSceneDecorator(
    entryContentKeys: List<Any>,
): SceneDecoratorStrategy<AppRoute> {
    val enabled = LocalKixyuPredictiveBackEnabled.current
    val enabledState = rememberUpdatedState(enabled)
    val entryContentKeysState = rememberUpdatedState(entryContentKeys)
    val view = LocalView.current
    val density = LocalDensity.current
    val cornerRadii = rememberWindowCornerRadii(view)
    val pageShape = remember(cornerRadii, density) {
        with(density) {
            AbsoluteRoundedCornerShape(
                topLeft = cornerRadii.topLeft.toDp(),
                topRight = cornerRadii.topRight.toDp(),
                bottomRight = cornerRadii.bottomRight.toDp(),
                bottomLeft = cornerRadii.bottomLeft.toDp(),
            )
        }
    }
    // Do not key this strategy on the setting. A new decorator changes the Scene identity and
    // Navigation 3 correctly interprets that as navigation, which used to animate the whole page
    // when the switch was toggled.
    return remember(cornerRadii, pageShape) {
        object : SceneDecoratorStrategy<AppRoute> {
            override fun SceneDecoratorStrategyScope<AppRoute>.decorateScene(
                scene: Scene<AppRoute>,
            ): Scene<AppRoute> {
                return object : Scene<AppRoute> by scene {
                    override val content: @Composable () -> Unit = {
                        val animatedContentScope = LocalNavAnimatedContentScope.current
                        val enterExitTransition = animatedContentScope.transition
                        val sceneRoute = scene.entries.lastOrNull()?.contentKey
                        val gestureActive = LocalKixyuNavigationGestureActive.current
                        var retainedRole by remember(scene.key) { mutableStateOf(BackSceneRole.NONE) }
                        val moving = enterExitTransition.currentState != enterExitTransition.targetState ||
                            enterExitTransition.isRunning
                        val role = resolveBackSceneRole(
                            previous = retainedRole,
                            enabled = enabledState.value,
                            gestureActive = gestureActive,
                            isCurrentRoute = sceneRoute == entryContentKeysState.value.lastOrNull(),
                            moving = moving,
                        )
                        SideEffect { retainedRole = role }
                        // Retain the role through commit/cancel settling, not just until the
                        // backStack changes. Ordinary navigation never acquires a gesture role.
                        val isBackTarget = role == BackSceneRole.PARENT
                        val isCurrentBackScene = role == BackSceneRole.CURRENT
                        val poppedScene = sceneRoute !in entryContentKeysState.value
                        val backTransitionActive = isBackTarget || isCurrentBackScene || (poppedScene && moving)
                        // A size-dependent slide animation is registered during layout. Starting
                        // another child timeline earlier in composition can shift its origin and
                        // inflate the seek duration. Use ONE size-independent float timeline and
                        // read width only in graphicsLayer: no per-frame list relayout is needed.
                        val visibility = enterExitTransition.animateFloat(
                            transitionSpec = {
                                if (role != BackSceneRole.NONE || poppedScene) {
                                    kixyuPageBackSpec(predictive = role != BackSceneRole.NONE)
                                } else snap()
                            },
                            label = "kixyuBackVisibility",
                        ) { state ->
                            if (state == EnterExitState.Visible) 1f else 0f
                        }
                        val clipToPhysicalCorners = enabledState.value &&
                            cornerRadii.hasRoundedCorners
                        val pageModifier = Modifier.fillMaxSize().graphicsLayer {
                            if (clipToPhysicalCorners) {
                                shape = pageShape
                                clip = true
                            }
                            if (isCurrentBackScene || poppedScene) {
                                translationX = size.width * (1f - visibility.value)
                            }
                        }
                        CompositionLocalProvider(
                            LocalKixyuNavigationBackTransitionActive provides backTransitionActive,
                        ) {
                            Box(pageModifier) {
                                scene.content()
                                if (isBackTarget) {
                                    Box(
                                        Modifier.fillMaxSize()
                                            .testTag("navigation_gesture_scrim")
                                            .graphicsLayer {
                                                alpha = KixyuMotion.BackParentScrimAlpha * (1f - visibility.value)
                                            }
                                            .background(Color.Black),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal enum class BackSceneRole { NONE, CURRENT, PARENT }

internal fun resolveBackSceneRole(
    previous: BackSceneRole,
    enabled: Boolean,
    gestureActive: Boolean,
    isCurrentRoute: Boolean,
    moving: Boolean,
): BackSceneRole = when {
    !enabled -> BackSceneRole.NONE
    gestureActive -> if (isCurrentRoute) BackSceneRole.CURRENT else BackSceneRole.PARENT
    moving -> previous
    else -> BackSceneRole.NONE
}
