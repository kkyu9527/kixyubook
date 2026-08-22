package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalKixyuPredictiveBackEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * the overlay exit has completed, while a cancelled gesture springs back without flashing.
 */
@Stable
class KixyuPredictiveBackState<T> {
    private val animatedProgress = Animatable(0f)
    private var pendingReset: Job? = null
    private var activeTarget by mutableStateOf<T?>(null)

    val progress: Float
        get() = animatedProgress.value

    fun progressFor(target: T): Float = if (activeTarget == target) progress else 0f

    internal suspend fun update(target: T, value: Float) {
        pendingReset?.cancel()
        activeTarget = target
        animatedProgress.snapTo(value.coerceIn(0f, 1f))
    }

    internal suspend fun commit(target: T) {
        activeTarget = target
        animatedProgress.snapTo(1f)
    }

    internal suspend fun cancel() {
        animatedProgress.animateTo(0f, kixyuPopupSpring())
        activeTarget = null
    }

    internal suspend fun resetImmediately() {
        pendingReset?.cancel()
        animatedProgress.snapTo(0f)
        activeTarget = null
    }

    internal fun resetAfterOverlayExit(scope: CoroutineScope) {
        pendingReset?.cancel()
        pendingReset = scope.launch {
            delay(KIXYU_PREDICTIVE_BACK_RESET_MILLIS)
            animatedProgress.snapTo(0f)
            activeTarget = null
        }
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
) {
    val predictiveBackEnabled = LocalKixyuPredictiveBackEnabled.current
    val currentTarget = rememberUpdatedState(target)
    val currentOnBack = rememberUpdatedState(onBack)

    LaunchedEffect(predictiveBackEnabled) {
        if (!predictiveBackEnabled) state.resetImmediately()
    }

    if (!predictiveBackEnabled) {
        BackHandler(enabled = target != null) {
            currentTarget.value?.let(currentOnBack.value)
        }
        return
    }

    val resetScope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = target != null) { events ->
        val gestureTarget = currentTarget.value ?: return@PredictiveBackHandler
        var committed = false
        try {
            events.collect { event -> state.update(gestureTarget, event.progress) }
            committed = true
            state.commit(gestureTarget)
            currentOnBack.value(gestureTarget)
        } catch (_: CancellationException) {
            if (!committed) withContext(NonCancellable) { state.cancel() }
        } finally {
            if (committed) state.resetAfterOverlayExit(resetScope)
        }
    }
}

/** Shared visual response used by sheets, dialogs, menus and reader controls. */
fun Modifier.kixyuPredictivePopupTransform(progress: Float): Modifier = graphicsLayer {
    val fraction = progress.coerceIn(0f, 1f)
    alpha = 1f - fraction
    scaleX = 1f - fraction * .08f
    scaleY = scaleX
}

/** Covers AnimatedVisibility exits and the slower reader side-panel spring. */
private const val KIXYU_PREDICTIVE_BACK_RESET_MILLIS = 600L
