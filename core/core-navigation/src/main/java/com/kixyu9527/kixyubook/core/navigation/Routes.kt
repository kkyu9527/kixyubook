package com.kixyu9527.kixyubook.core.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize

/** Stable keys for the three sibling pages hosted inside the HOME destination. */
object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
}

/** Type-safe, saveable Navigation 3 destination keys. */
sealed interface AppRoute : NavKey, Parcelable {
    @Parcelize data object Home : AppRoute
    @Parcelize data object HiddenLibrary : AppRoute
    @Parcelize data object Appearance : AppRoute
    @Parcelize data object ReadingSettings : AppRoute
    @Parcelize data object ReadingInformation : AppRoute
    @Parcelize data object FontManagement : AppRoute
    @Parcelize data object CloudSync : AppRoute
    @Parcelize data object GoogleAccount : AppRoute
    @Parcelize data object About : AppRoute
    @Parcelize data object DiagnosticLog : AppRoute
    @Parcelize data class DiagnosticLogCategory(val category: String) : AppRoute
    @Parcelize data class Reader(val bookUuid: String) : AppRoute
    @Parcelize data class TextCorrections(val bookUuid: String) : AppRoute
}
