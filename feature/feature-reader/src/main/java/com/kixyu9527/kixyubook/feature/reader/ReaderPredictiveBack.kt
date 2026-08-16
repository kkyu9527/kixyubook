package com.kixyu9527.kixyubook.feature.reader

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPopupSpring
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One predictive-back lifecycle for every reader-owned overlay.
 *
 * A committed gesture must keep its final visual progress until AnimatedVisibility (or the side
 * panel spring) has removed the old content. Resetting in the handler's `finally` block restores
 * the outgoing popup for one frame and produces a visible flash. A cancelled gesture, in contrast,
 * smoothly springs back to its resting position.
 */
@Stable
internal class ReaderPredictiveBackState {
    private val animatedProgress = Animatable(0f)
    private var pendingReset: Job? = null
    private var activeTarget by mutableStateOf<ReaderPredictiveBackTarget?>(null)

    val progress: Float
        get() = animatedProgress.value

    fun progressFor(target: ReaderPredictiveBackTarget): Float =
        if (activeTarget == target) progress else 0f

    suspend fun update(target: ReaderPredictiveBackTarget, value: Float) {
        pendingReset?.cancel()
        activeTarget = target
        animatedProgress.snapTo(value.coerceIn(0f, 1f))
    }

    suspend fun commit(target: ReaderPredictiveBackTarget) {
        activeTarget = target
        animatedProgress.snapTo(1f)
    }

    suspend fun cancel() {
        animatedProgress.animateTo(0f, kixyuPopupSpring())
        activeTarget = null
    }

    fun resetAfterOverlayExit(scope: CoroutineScope) {
        pendingReset?.cancel()
        pendingReset = scope.launch {
            delay(READER_PREDICTIVE_BACK_RESET_MILLIS)
            animatedProgress.snapTo(0f)
            activeTarget = null
        }
    }
}

@Composable
internal fun rememberReaderPredictiveBackState(): ReaderPredictiveBackState =
    remember { ReaderPredictiveBackState() }

/** Collects, cancels and commits predictive back consistently across all reader surfaces. */
@Composable
internal fun ReaderPredictiveBackHandler(
    target: ReaderPredictiveBackTarget?,
    state: ReaderPredictiveBackState,
    onBack: (ReaderPredictiveBackTarget) -> Unit,
) {
    val resetScope = rememberCoroutineScope()
    val currentTarget = rememberUpdatedState(target)
    val currentOnBack = rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = target != null) { events ->
        val gestureTarget = currentTarget.value ?: return@PredictiveBackHandler
        var committed = false
        try {
            events.collect { event -> state.update(gestureTarget, event.progress) }
            committed = true
            state.commit(gestureTarget)
            currentOnBack.value(gestureTarget)
        } catch (_: CancellationException) {
            if (!committed) {
                withContext(NonCancellable) { state.cancel() }
            }
        } finally {
            if (committed) state.resetAfterOverlayExit(resetScope)
        }
    }
}

internal enum class ReaderPredictiveBackTarget {
    BOOK_INFO,
    SHEET,
    SEARCH,
    POPUP_MENU,
    CONTROLS,
    SEARCH_RESULTS,
    ROUTE,
}

/** Covers both AnimatedVisibility exits and the slower side-panel spring. */
private const val READER_PREDICTIVE_BACK_RESET_MILLIS = 600L
