package com.kixyu9527.kixyubook.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Only translate the UI filter token. User-created category names stay verbatim. */
@Composable
internal fun categoryFilterLabel(category: String): String =
    if (category == ALL_LIBRARY_CATEGORIES) stringResource(R.string.library_all_categories) else category
