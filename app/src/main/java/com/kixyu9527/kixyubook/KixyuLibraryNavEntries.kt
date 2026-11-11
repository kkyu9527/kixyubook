package com.kixyu9527.kixyubook

import androidx.navigation3.runtime.EntryProviderScope
import com.kixyu9527.kixyubook.core.navigation.AppRoute
import com.kixyu9527.kixyubook.feature.library.LibraryRoute

internal fun EntryProviderScope<AppRoute>.kixyuLibraryEntries(dependencies: KixyuNavEntryDependencies) {
    with(dependencies) {
        entry<AppRoute.HiddenLibrary> {
            DestinationWithBack(popDestination) { onBack ->
                LibraryRoute(
                    onOpenBook = openBook,
                    hiddenOnly = true,
                    onBack = onBack,
                )
            }
        }
    }
}
