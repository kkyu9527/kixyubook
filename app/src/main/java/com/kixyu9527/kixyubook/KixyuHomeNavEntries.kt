package com.kixyu9527.kixyubook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuContextualBarController
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationBackTransitionActive
import com.kixyu9527.kixyubook.core.navigation.AppRoute
import com.kixyu9527.kixyubook.core.navigation.Routes
import com.kixyu9527.kixyubook.feature.home.HomeRoute
import com.kixyu9527.kixyubook.feature.library.LibraryRoute
import com.kixyu9527.kixyubook.feature.settings.SettingsRoute

internal fun EntryProviderScope<AppRoute>.kixyuHomeEntries(dependencies: KixyuNavEntryDependencies) {
    with(dependencies) {
        entry<AppRoute.Home> {
            val navigationBackTransitionActive = LocalKixyuNavigationBackTransitionActive.current
            val contextualBarActive = LocalKixyuContextualBarController.current?.state != null
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !contextualBarActive,
                // The previous top-level page is built on the first predictive-back frame.
                // Do not also build its adjacent sibling until the gesture has settled.
                beyondViewportPageCount = if (navigationBackTransitionActive) 0 else 1,
                key = { page -> topDestinations.value[page].route },
            ) { page ->
                when (topDestinations.value[page].route) {
                    Routes.HOME -> HomeRoute(onOpenBook = openBook)
                    Routes.LIBRARY -> LibraryRoute(
                        onOpenBook = openBook,
                        onOpenHiddenLibrary = {
                            prioritizeAnimation()
                            navigator.push(AppRoute.HiddenLibrary)
                        },
                        externalImportRequestId = externalImportRequestId.value,
                        externalImportUris = externalImportUris.value,
                        onExternalImportConsumed = onExternalImportConsumed,
                    )
                    Routes.SETTINGS -> SettingsRoute(
                        currentVersion = BuildConfig.VERSION_NAME,
                        onAppearance = {
                            prioritizeAnimation()
                            navigator.push(AppRoute.Appearance)
                        },
                        onCloudSync = {
                            prioritizeAnimation()
                            navigator.push(AppRoute.CloudSync)
                        },
                        onReadingSettings = {
                            prioritizeAnimation()
                            navigator.push(AppRoute.ReadingSettings)
                        },
                        onHabits = {
                            prioritizeAnimation()
                            navigator.push(AppRoute.ReadingHabits)
                        },
                        onBackup = {
                            prioritizeAnimation()
                            navigator.push(AppRoute.BackupSettings)
                        },
                        onAbout = {
                            prioritizeAnimation()
                            navigator.push(AppRoute.About)
                        },
                        detailContent = { pane ->
                            EmbeddedSettingsPane(dependencies = dependencies, pane = pane)
                        },
                    )
                }
            }
        }
    }
}
