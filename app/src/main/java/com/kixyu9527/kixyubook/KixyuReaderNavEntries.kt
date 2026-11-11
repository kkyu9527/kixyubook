package com.kixyu9527.kixyubook

import androidx.navigation3.runtime.EntryProviderScope
import com.kixyu9527.kixyubook.core.navigation.AppRoute
import com.kixyu9527.kixyubook.feature.reader.CorrectionManagementRoute
import com.kixyu9527.kixyubook.feature.reader.ReaderRoute

internal fun EntryProviderScope<AppRoute>.kixyuReaderEntries(dependencies: KixyuNavEntryDependencies) {
    with(dependencies) {
        entry<AppRoute.Reader> { route ->
            val onExitReader: () -> Unit = { exitReader(route.bookUuid) }
            KixyuNavigationBackHandler(onExitReader)
            ReaderRoute(
                bookUuid = route.bookUuid,
                initialSettings = initialReaderSettings.value,
                onManageCorrections = {
                    prioritizeAnimation()
                    navigator.push(AppRoute.TextCorrections(route.bookUuid))
                },
                onManageFonts = {
                    prioritizeAnimation()
                    navigator.push(AppRoute.FontManagement)
                },
                onExit = onExitReader,
            )
        }
        entry<AppRoute.TextCorrections> { route ->
            DestinationWithBack(popDestination) { onBack ->
                CorrectionManagementRoute(bookUuid = route.bookUuid, onBack = onBack)
            }
        }
    }
}
