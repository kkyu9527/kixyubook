package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** A layout's key and its index belong to the same measurement; an external list's index does not. */
internal fun PagerState.settledReaderLeaves(
    navigationVersion: () -> Int,
    appliedNavigationVersion: () -> Int,
) = snapshotFlow {
    if (isScrollInProgress || appliedNavigationVersion() != navigationVersion()) null else {
        layoutInfo.visiblePagesInfo.firstOrNull { it.index == settledPage }
            ?.key?.toString()?.let { it to navigationVersion() }
    }
}.distinctUntilChanged()
