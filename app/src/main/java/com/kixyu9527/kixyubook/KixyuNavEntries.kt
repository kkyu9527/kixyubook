package com.kixyu9527.kixyubook

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationBackTransitionActive
import com.kixyu9527.kixyubook.core.navigation.AppRoute
import com.kixyu9527.kixyubook.core.navigation.Routes
import com.kixyu9527.kixyubook.feature.home.HomeRoute
import com.kixyu9527.kixyubook.feature.library.LibraryRoute
import com.kixyu9527.kixyubook.feature.reader.CorrectionManagementRoute
import com.kixyu9527.kixyubook.feature.reader.ReaderRoute
import com.kixyu9527.kixyubook.feature.settings.AboutRoute
import com.kixyu9527.kixyubook.feature.settings.AppearanceRoute
import com.kixyu9527.kixyubook.feature.settings.CloudSyncRoute
import com.kixyu9527.kixyubook.feature.settings.DiagnosticLogCategoryRoute
import com.kixyu9527.kixyubook.feature.settings.DiagnosticLogRoute
import com.kixyu9527.kixyubook.feature.settings.FontManagementRoute
import com.kixyu9527.kixyubook.feature.settings.GoogleAccountRoute
import com.kixyu9527.kixyubook.feature.settings.ReadingInformationRoute
import com.kixyu9527.kixyubook.feature.settings.ReadingSettingsRoute
import com.kixyu9527.kixyubook.feature.settings.SettingsPane
import com.kixyu9527.kixyubook.feature.settings.SettingsRoute

internal const val PROJECT_SOURCE_URL = "https://github.com/kkyu9527/kixyubook"
private const val TELEGRAM_CONTACT_URL = "https://t.me/kkyu9527s_bot"

internal class KixyuNavEntryDependencies(
    val topDestinations: List<TopDestination>,
    val pagerState: PagerState,
    val navigator: KixyuNavigator,
    val initialReaderSettings: ReaderSettings,
    val updateState: AppUpdateState,
    val diagnosticOnlyFailures: Boolean,
    val externalImportRequestId: Long?,
    val externalImportUris: List<String>,
    val uriHandler: UriHandler,
    val openBook: (String) -> Unit,
    val prioritizeAnimation: () -> Unit,
    val popDestination: () -> Unit,
    val exitReader: (String) -> Unit,
    val onExternalImportConsumed: (Long) -> Unit,
    val onCheckForUpdates: () -> Unit,
    val onUpdateResultConsumed: () -> Unit,
    val onShowReleaseNotes: () -> Unit,
    val onDiagnosticOnlyFailuresChanged: (Boolean) -> Unit,
)

internal fun kixyuEntryProvider(dependencies: KixyuNavEntryDependencies) =
    entryProvider<AppRoute> {
        with(dependencies) {
            entry<AppRoute.Home> {
                val navigationBackTransitionActive =
                    LocalKixyuNavigationBackTransitionActive.current
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // The previous top-level page is built on the first predictive-back frame.
                    // Do not also build its adjacent sibling until the gesture has settled.
                    beyondViewportPageCount = if (navigationBackTransitionActive) 0 else 1,
                    key = { page -> topDestinations[page].route },
                ) { page ->
                    when (topDestinations[page].route) {
                        Routes.HOME -> HomeRoute(onOpenBook = openBook)
                        Routes.LIBRARY -> LibraryRoute(
                            onOpenBook = openBook,
                            onOpenHiddenLibrary = {
                                prioritizeAnimation()
                                navigator.push(AppRoute.HiddenLibrary)
                            },
                            externalImportRequestId = externalImportRequestId,
                            externalImportUris = externalImportUris,
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
                            onAbout = {
                                prioritizeAnimation()
                                navigator.push(AppRoute.About)
                            },
                            detailContent = { pane ->
                                EmbeddedSettingsPane(
                                    pane = pane,
                                    dependencies = dependencies,
                                )
                            },
                        )
                    }
                }
            }
            entry<AppRoute.HiddenLibrary> {
                DestinationWithBack(popDestination) { onBack ->
                    LibraryRoute(
                        onOpenBook = openBook,
                        hiddenOnly = true,
                        onBack = onBack,
                    )
                }
            }
            entry<AppRoute.Appearance> {
                DestinationWithBack(popDestination) { onBack ->
                    AppearanceRoute(onBack = onBack)
                }
            }
            entry<AppRoute.ReadingSettings> {
                DestinationWithBack(popDestination) { onBack ->
                    ReadingSettingsRoute(
                        onBack = onBack,
                        onManageFonts = {
                            prioritizeAnimation()
                            navigator.push(AppRoute.FontManagement)
                        },
                        onReadingInformation = {
                            prioritizeAnimation()
                            navigator.push(AppRoute.ReadingInformation)
                        },
                    )
                }
            }
            entry<AppRoute.ReadingInformation> {
                DestinationWithBack(popDestination) { onBack ->
                    ReadingInformationRoute(onBack = onBack)
                }
            }
            entry<AppRoute.FontManagement> {
                DestinationWithBack(popDestination) { onBack ->
                    FontManagementRoute(onBack = onBack)
                }
            }
            entry<AppRoute.CloudSync> {
                DestinationWithBack(popDestination) { onBack ->
                    CloudSyncRoute(
                        onBack = onBack,
                        onGoogleAccount = {
                            prioritizeAnimation()
                            navigator.push(AppRoute.GoogleAccount)
                        },
                    )
                }
            }
            entry<AppRoute.GoogleAccount> {
                DestinationWithBack(popDestination) { onBack ->
                    GoogleAccountRoute(onBack = onBack)
                }
            }
            entry<AppRoute.About> {
                DestinationWithBack(popDestination) { onBack ->
                    AboutDestination(dependencies = dependencies, onBack = onBack)
                }
            }
            entry<AppRoute.DiagnosticLog> {
                DestinationWithBack(popDestination) { onBack ->
                    DiagnosticLogRoute(
                        onlyFailures = diagnosticOnlyFailures,
                        onOnlyFailuresChanged = onDiagnosticOnlyFailuresChanged,
                        onBack = onBack,
                        onOpenCategory = { category ->
                            prioritizeAnimation()
                            navigator.push(AppRoute.DiagnosticLogCategory(category))
                        },
                    )
                }
            }
            entry<AppRoute.DiagnosticLogCategory> { route ->
                DestinationWithBack(popDestination) { onBack ->
                    DiagnosticLogCategoryRoute(
                        categoryKey = route.category,
                        onlyFailures = diagnosticOnlyFailures,
                        onOnlyFailuresChanged = onDiagnosticOnlyFailuresChanged,
                        onBack = onBack,
                    )
                }
            }
            entry<AppRoute.Reader> { route ->
                val onExitReader: () -> Unit = { exitReader(route.bookUuid) }
                KixyuNavigationBackHandler(onExitReader)
                ReaderRoute(
                    bookUuid = route.bookUuid,
                    initialSettings = initialReaderSettings,
                    onManageCorrections = {
                        prioritizeAnimation()
                        navigator.push(AppRoute.TextCorrections(route.bookUuid))
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

@Composable
private fun DestinationWithBack(
    onBack: () -> Unit,
    content: @Composable ((() -> Unit) -> Unit),
) {
    KixyuNavigationBackHandler(onBack)
    content(onBack)
}

@Composable
private fun EmbeddedSettingsPane(
    pane: SettingsPane,
    dependencies: KixyuNavEntryDependencies,
) = with(dependencies) {
    when (pane) {
        SettingsPane.CLOUD_SYNC -> CloudSyncRoute(
            onBack = {},
            onGoogleAccount = {
                prioritizeAnimation()
                navigator.push(AppRoute.GoogleAccount)
            },
            embedded = true,
        )
        SettingsPane.READING -> ReadingSettingsRoute(
            onBack = {},
            onManageFonts = {
                prioritizeAnimation()
                navigator.push(AppRoute.FontManagement)
            },
            onReadingInformation = {
                prioritizeAnimation()
                navigator.push(AppRoute.ReadingInformation)
            },
            embedded = true,
        )
        SettingsPane.APPEARANCE -> AppearanceRoute(onBack = {}, embedded = true)
        SettingsPane.ABOUT -> AboutDestination(
            dependencies = dependencies,
            onBack = {},
            embedded = true,
        )
    }
}

@Composable
private fun AboutDestination(
    dependencies: KixyuNavEntryDependencies,
    onBack: () -> Unit,
    embedded: Boolean = false,
) = with(dependencies) {
    AboutRoute(
        updateState = updateState,
        currentVersion = BuildConfig.VERSION_NAME,
        onCheckForUpdates = onCheckForUpdates,
        onUpdateResultConsumed = onUpdateResultConsumed,
        onShowReleaseNotes = onShowReleaseNotes,
        onOpenDiagnosticLog = {
            prioritizeAnimation()
            navigator.push(AppRoute.DiagnosticLog)
        },
        onOpenProjectSource = {
            runCatching { uriHandler.openUri(PROJECT_SOURCE_URL) }.isSuccess
        },
        onContactTelegram = {
            runCatching { uriHandler.openUri(TELEGRAM_CONTACT_URL) }.isSuccess
        },
        appLogo = {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Kixyu Book Logo",
                modifier = Modifier.size(56.dp),
            )
        },
        onBack = onBack,
        embedded = embedded,
    )
}
