package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally

/** Standard transition for a detail page pushed on top of its parent page. */
fun kixyuDetailPageEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(
            durationMillis = KixyuMotion.PageNavigationMillis,
            easing = FastOutSlowInEasing,
        ),
        initialOffsetX = { width -> width },
    )

/** One timeline for the outgoing layer and parent scrim; the system seeks it during a gesture. */
fun kixyuPageBackSpec(predictive: Boolean) = tween<Float>(
    durationMillis = KixyuMotion.BackNavigationMillis,
    easing = if (predictive) LinearEasing else FastOutSlowInEasing,
)
