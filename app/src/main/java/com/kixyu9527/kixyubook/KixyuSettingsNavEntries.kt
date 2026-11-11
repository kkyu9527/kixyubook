package com.kixyu9527.kixyubook

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import com.kixyu9527.kixyubook.core.navigation.AppRoute
import com.kixyu9527.kixyubook.feature.cloud.BookDownloadsRoute
import com.kixyu9527.kixyubook.feature.cloud.CloudSyncRoute
import com.kixyu9527.kixyubook.feature.cloud.GoogleAccountRoute
import com.kixyu9527.kixyubook.feature.settings.AboutRoute
import com.kixyu9527.kixyubook.feature.settings.AppearanceRoute
import com.kixyu9527.kixyubook.feature.settings.BackupSettingsRoute
import com.kixyu9527.kixyubook.feature.settings.DiagnosticLogCategoryRoute
import com.kixyu9527.kixyubook.feature.settings.DiagnosticLogRoute
import com.kixyu9527.kixyubook.feature.settings.FontManagementRoute
import com.kixyu9527.kixyubook.feature.settings.ReadingColorsRoute
import com.kixyu9527.kixyubook.feature.settings.ReadingGroupSettingsRoute
import com.kixyu9527.kixyubook.feature.settings.ReadingHabitsRoute
import com.kixyu9527.kixyubook.feature.settings.ReadingSettingsRoute
import com.kixyu9527.kixyubook.feature.settings.SettingsPane

internal fun EntryProviderScope<AppRoute>.kixyuSettingsEntries(dependencies: KixyuNavEntryDependencies) {
    with(dependencies) {
        entry<AppRoute.Appearance> {
            DestinationWithBack(popDestination) { onBack -> AppearanceRoute(onBack = onBack) }
        }
        entry<AppRoute.ReadingSettings> {
            DestinationWithBack(popDestination) { onBack ->
                ReadingSettingsRoute(
                    onBack = onBack,
                    onOpenGroup = { group ->
                        prioritizeAnimation()
                        navigator.push(AppRoute.ReadingSettingsGroup(group.name))
                    },
                )
            }
        }
        entry<AppRoute.ReadingHabits> {
            DestinationWithBack(popDestination) { onBack -> ReadingHabitsRoute(onBack = onBack) }
        }
        entry<AppRoute.BackupSettings> {
            DestinationWithBack(popDestination) { onBack -> BackupSettingsRoute(onBack = onBack) }
        }
        entry<AppRoute.ReadingSettingsGroup> { route ->
            DestinationWithBack(popDestination) { onBack ->
                ReadingGroupSettingsRoute(
                    groupName = route.group,
                    onBack = onBack,
                    onManageFonts = {
                        prioritizeAnimation()
                        navigator.push(AppRoute.FontManagement)
                    },
                    onEditColors = {
                        prioritizeAnimation()
                        navigator.push(AppRoute.ReadingColors)
                    },
                )
            }
        }
        entry<AppRoute.ReadingColors> {
            DestinationWithBack(popDestination) { onBack -> ReadingColorsRoute(onBack = onBack) }
        }
        entry<AppRoute.FontManagement> {
            DestinationWithBack(popDestination) { onBack -> FontManagementRoute(onBack = onBack) }
        }
        entry<AppRoute.About> {
            DestinationWithBack(popDestination) { onBack ->
                AboutDestination(dependencies = dependencies, onBack = onBack)
            }
        }
        entry<AppRoute.DiagnosticLog> {
            DestinationWithBack(popDestination) { onBack ->
                DiagnosticLogRoute(
                    onlyFailures = diagnosticOnlyFailures.value,
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
                    onlyFailures = diagnosticOnlyFailures.value,
                    onOnlyFailuresChanged = onDiagnosticOnlyFailuresChanged,
                    onBack = onBack,
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
internal fun EmbeddedSettingsPane(
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
            onBookDownloads = {
                prioritizeAnimation()
                navigator.push(AppRoute.BookDownloads)
            },
            embedded = true,
        )
        SettingsPane.READING -> ReadingSettingsRoute(
            onBack = {},
            onOpenGroup = { group ->
                prioritizeAnimation()
                navigator.push(AppRoute.ReadingSettingsGroup(group.name))
            },
            embedded = true,
        )
        SettingsPane.APPEARANCE -> AppearanceRoute(onBack = {}, embedded = true)
        SettingsPane.HABITS -> ReadingHabitsRoute(onBack = {}, embedded = true)
        SettingsPane.BACKUP -> BackupSettingsRoute(onBack = {}, embedded = true)
        SettingsPane.ABOUT -> AboutDestination(dependencies = dependencies, onBack = {}, embedded = true)
    }
}

@androidx.compose.runtime.Composable
private fun AboutDestination(
    dependencies: KixyuNavEntryDependencies,
    onBack: () -> Unit,
    embedded: Boolean = false,
) = with(dependencies) {
    AboutRoute(
        updateState = updateState.value,
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
                contentDescription = stringResource(R.string.app_logo_description),
                modifier = Modifier.size(56.dp),
            )
        },
        onBack = onBack,
        embedded = embedded,
    )
}
