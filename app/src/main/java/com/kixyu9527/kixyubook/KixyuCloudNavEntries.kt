package com.kixyu9527.kixyubook

import androidx.navigation3.runtime.EntryProviderScope
import com.kixyu9527.kixyubook.core.navigation.AppRoute
import com.kixyu9527.kixyubook.feature.cloud.BookDownloadsRoute
import com.kixyu9527.kixyubook.feature.cloud.CloudSyncRoute
import com.kixyu9527.kixyubook.feature.cloud.GoogleAccountRoute

internal fun EntryProviderScope<AppRoute>.kixyuCloudEntries(dependencies: KixyuNavEntryDependencies) {
    with(dependencies) {
        entry<AppRoute.CloudSync> {
            DestinationWithBack(popDestination) { onBack ->
                CloudSyncRoute(
                    onBack = onBack,
                    onGoogleAccount = {
                        prioritizeAnimation()
                        navigator.push(AppRoute.GoogleAccount)
                    },
                    onBookDownloads = {
                        prioritizeAnimation()
                        navigator.push(AppRoute.BookDownloads)
                    },
                )
            }
        }
        entry<AppRoute.BookDownloads> {
            DestinationWithBack(popDestination) { onBack ->
                BookDownloadsRoute(onBack = onBack)
            }
        }
        entry<AppRoute.GoogleAccount> {
            DestinationWithBack(popDestination) { onBack ->
                GoogleAccountRoute(onBack = onBack)
            }
        }
    }
}
