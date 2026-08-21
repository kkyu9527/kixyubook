package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.runtime.Composable
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPredictiveBackHandler
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPredictiveBackState
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuPredictiveBackState

/**
 * One predictive-back lifecycle for every reader-owned overlay.
 *
 * A committed gesture must keep its final visual progress until AnimatedVisibility (or the side
 * panel spring) has removed the old content. Resetting in the handler's `finally` block restores
 * the outgoing popup for one frame and produces a visible flash. A cancelled gesture, in contrast,
 * smoothly springs back to its resting position.
 */
internal typealias ReaderPredictiveBackState =
    KixyuPredictiveBackState<ReaderPredictiveBackTarget>

@Composable
internal fun rememberReaderPredictiveBackState(): ReaderPredictiveBackState =
    rememberKixyuPredictiveBackState()

/** Collects, cancels and commits predictive back consistently across all reader surfaces. */
@Composable
internal fun ReaderPredictiveBackHandler(
    target: ReaderPredictiveBackTarget?,
    state: ReaderPredictiveBackState,
    onBack: (ReaderPredictiveBackTarget) -> Unit,
) {
    KixyuPredictiveBackHandler(target = target, state = state, onBack = onBack)
}

internal enum class ReaderPredictiveBackTarget {
    BOOK_INFO,
    SHEET,
    SEARCH,
    POPUP_MENU,
    CONTROLS,
    SEARCH_RESULTS,
}
