package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/** Standard transition for a detail page pushed on top of its parent page. */
fun kixyuDetailPageEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(
            durationMillis = KixyuMotion.PageNavigationMillis,
            easing = FastOutSlowInEasing,
        ),
        initialOffsetX = { width -> width },
    )

/** Standard transition for a detail page popped back to its parent page. */
fun kixyuDetailPageExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(
            durationMillis = KixyuMotion.PageNavigationMillis,
            easing = FastOutSlowInEasing,
        ),
        targetOffsetX = { width -> width },
    )
