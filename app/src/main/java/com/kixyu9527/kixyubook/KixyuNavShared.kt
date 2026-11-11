package com.kixyu9527.kixyubook

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed

@Composable
internal fun DestinationWithBack(
    onBack: () -> Unit,
    content: @Composable ((() -> Unit) -> Unit),
) {
    KixyuNavigationBackHandler(onBack)
    // The toolbar is not a gesture callback. Ignore extra taps on a scene that is already
    // leaving; do not accidentally pop its parent while both are composed by Navigation 3.
    content(dropUnlessResumed { onBack() })
}
