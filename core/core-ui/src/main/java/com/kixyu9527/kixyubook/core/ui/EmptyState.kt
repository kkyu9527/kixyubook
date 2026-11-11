package com.kixyu9527.kixyubook.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuEmptyState
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

@Composable
fun LibraryEmptyState(modifier: Modifier = Modifier) {
    KixyuEmptyState(
        icon = KixyuSymbols.AutoStories,
        title = stringResource(R.string.library_empty_title),
        description = stringResource(R.string.library_empty_description),
        modifier = modifier,
    )
}
