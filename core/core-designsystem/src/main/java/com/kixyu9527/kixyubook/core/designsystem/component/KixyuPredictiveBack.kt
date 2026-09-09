package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuPredictiveBackEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * True only while a Navigation 3 scene is moving back to its parent.
 *
 * Expensive, non-interactive work may observe this signal to yield the frame budget to navigation.
 * The current chapter itself must remain available; consumers should pause only speculative work.
 */
val LocalKixyuNavigationBackTransitionActive = staticCompositionLocalOf { false }

/**
 * Shared highest-priority predictive-back lifecycle for app-owned overlays.
 *
 * Edge-to-edge defines where a surface is drawn; this state defines how that surface gives control
 * back. Both are design-system contracts. A committed gesture keeps its final visual state until
 * the overlay exit has completed, while a cancelled gesture settles back without flashing.
 */
@Stable
class KixyuPredictiveBackState<T> {
    // One animator per named surface (Unit for standalone overlays; a small enum for reader
    // chrome), not per gesture. These are released with this remembered controller. A retiring
    // child and the newly interactive parent may be composed at the same time.
    private val animations = mutableStateMapOf<T, Animatable<Float, AnimationVector1D>>()
    private var activeTarget by mutableStateOf<T?>(null)

    val progress: Float
        get() = activeTarget?.let(::progressFor) ?: 0f

    fun progressFor(target: T): Float = animations[target]?.value ?: 0f

    private fun animationFor(target: T) = animations.getOrPut(target) { Animatable(0f) }

    internal suspend fun prepareTarget(target: T) {
        activeTarget = target
        // Reopening this surface starts fresh, but must not reset any still-exiting sibling.
        animationFor(target).snapTo(0f)
    }

    internal suspend fun update(target: T, value: Float) {
        activeTarget = target
        animationFor(target).snapTo(value.coerceIn(0f, 1f))
    }

    internal suspend fun commit(target: T, animate: Boolean = true) {
        activeTarget = target
        val animation = animationFor(target)
        if (animate) animation.animateTo(1f, kixyuBackSettleSpec(1f - animation.value))
        else animation.snapTo(1f)
    }

    internal suspend fun cancel(target: T) {
        val animation = animationFor(target)
        animation.animateTo(0f, kixyuBackSettleSpec(animation.value))
    }
}

@Composable
fun <T> rememberKixyuPredictiveBackState(): KixyuPredictiveBackState<T> =
    remember { KixyuPredictiveBackState() }

/**
 * Register this after the overlay content so it is the last app callback and therefore owns Back
 * before the underlying NavDisplay or page. Platform Dialog/BottomSheet implementations may keep
 * their native handler only when they also own their complete predictive animation.
 */
@Composable
fun <T> KixyuPredictiveBackHandler(
    target: T?,
    state: KixyuPredictiveBackState<T>,
    onBack: (T) -> Unit,
    animateOnCommit: Boolean = true,
) {
    val predictiveBackEnabled = LocalKixyuPredictiveBackEnabled.current
    val currentTarget = rememberUpdatedState(target)
    val currentOnBack = rememberUpdatedState(onBack)

    LaunchedEffect(target, predictiveBackEnabled) {
        // Only the newly active target resets. Other targets retain their committed frame until
        // they are reopened or this controller is disposed, independent of exit duration/scale.
        if (target != null) state.prepareTarget(target)
    }

    if (!predictiveBackEnabled) {
        BackHandler(enabled = target != null) {
            currentTarget.value?.let(currentOnBack.value)
        }
        return
    }

    PredictiveBackHandler(enabled = target != null) { events ->
        val gestureTarget = currentTarget.value ?: return@PredictiveBackHandler
        var committed = false
        var receivedProgress = false
        try {
            events.collect { event ->
                receivedProgress = true
                state.update(gestureTarget, event.progress)
            }
            // Hardware/three-button Back completes an empty flow: let the surface run its
            // ordinary exit instead of flashing through a fabricated 100% gesture frame.
            if (receivedProgress) state.commit(gestureTarget, animateOnCommit) else state.prepareTarget(gestureTarget)
            committed = true
            if (currentTarget.value == gestureTarget) currentOnBack.value(gestureTarget)
        } catch (_: CancellationException) {
            if (!committed) withContext(NonCancellable) { state.cancel(gestureTarget) }
        }
    }
}

/** Shared visual response used by sheets, dialogs, menus and reader controls. */
fun Modifier.kixyuPredictivePopupTransform(progress: Float): Modifier =
    kixyuPredictivePopupTransform { progress }

/** Read frame-by-frame state in the layer, not in the whole reader/list composition. */
fun Modifier.kixyuPredictivePopupTransform(progress: () -> Float): Modifier = graphicsLayer {
    val fraction = progress().coerceIn(0f, 1f)
    alpha = 1f - fraction
    scaleX = 1f - fraction * KixyuMotion.BackPopupScaleReduction
    scaleY = scaleX
}

internal fun kixyuBackSettleSpec(distance: Float) = tween<Float>(
    durationMillis = if (distance <= 0f) 0 else
        (KixyuMotion.BackOverlaySettleMillis * distance.coerceIn(0f, 1f)).toInt()
            .coerceAtLeast(KixyuMotion.BackOverlayMinSettleMillis),
    easing = FastOutSlowInEasing,
)
