package com.kixyu9527.kixyubook.feature.reader.browser

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import com.kixyu9527.kixyubook.core.reader.engine.ReaderTextActionTarget

internal data class BrowserSelection(
    val target: ReaderTextActionTarget,
    val boundsInWindow: Rect,
    val copyToClipboard: () -> Unit,
    val clear: () -> Unit,
)

/** The browser supplies a source range; the existing reader host owns every menu and back action. */
internal val LocalBrowserSelectionHandler = compositionLocalOf<(BrowserSelection?) -> Unit> { {} }
