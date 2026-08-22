package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Window-level system-bar request submitted by the currently visible immersive destination. */
@Immutable
data class KixyuSystemBarPolicy(
    val statusBarVisible: Boolean,
    val navigationBarVisible: Boolean,
    val useDarkIcons: Boolean,
)

/**
 * Single owner for system bars in the Activity window.
 *
 * Navigation 3 can keep more than one destination composed during a transition. Destinations must
 * therefore publish intent here instead of independently calling show/hide on the same window.
 */
@Stable
class KixyuSystemBarHost {
    private var activeOwner: Any? = null

    var policy by mutableStateOf<KixyuSystemBarPolicy?>(null)
        private set

    fun update(owner: Any, value: KixyuSystemBarPolicy) {
        activeOwner = owner
        policy = value
    }

    fun clear(owner: Any) {
        if (activeOwner !== owner) return
        activeOwner = null
        policy = null
    }
}

val LocalKixyuSystemBarHost = staticCompositionLocalOf<KixyuSystemBarHost?> { null }
