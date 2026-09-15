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

/** Snapshot of reader-owned chrome used by both system-bar and Back priority decisions. */
internal data class ReaderChromeState(
    val controlsVisible: Boolean = false,
    val toolsMenuVisible: Boolean = false,
    val searchVisible: Boolean = false,
    val bookInfoVisible: Boolean = false,
    val sheet: ReaderSheet? = null,
    val settingsMenuVisible: Boolean = false,
    val directoryPanelComposed: Boolean = false,
    val hasSearchResults: Boolean = false,
) {
    val overlayVisible: Boolean
        get() = controlsVisible || toolsMenuVisible || searchVisible ||
            bookInfoVisible || sheet != null || directoryPanelComposed
}

/**
 * Reader Back order is a public UX contract: dismiss the topmost owned surface before navigation.
 * Keeping it pure makes ordinary and predictive Back use the same priority and regression tests.
 */
internal fun ReaderChromeState.predictiveBackTarget(): ReaderPredictiveBackTarget? = when {
    bookInfoVisible -> ReaderPredictiveBackTarget.BOOK_INFO
    sheet != null -> ReaderPredictiveBackTarget.SHEET
    searchVisible -> ReaderPredictiveBackTarget.SEARCH
    settingsMenuVisible || toolsMenuVisible -> ReaderPredictiveBackTarget.POPUP_MENU
    controlsVisible -> ReaderPredictiveBackTarget.CONTROLS
    hasSearchResults -> ReaderPredictiveBackTarget.SEARCH_RESULTS
    else -> null
}

internal data class ReaderSystemBarVisibility(
    val statusBarVisible: Boolean,
    val navigationBarVisible: Boolean,
)

internal fun readerSystemBarVisibility(
    showStatusBar: Boolean,
    hideNavigationBar: Boolean,
    overlayVisible: Boolean,
): ReaderSystemBarVisibility = ReaderSystemBarVisibility(
    statusBarVisible = showStatusBar || overlayVisible,
    navigationBarVisible = !hideNavigationBar || overlayVisible,
)
