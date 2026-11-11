package com.kixyu9527.kixyubook

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.State
import androidx.compose.ui.platform.UriHandler
import androidx.navigation3.runtime.entryProvider
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.navigation.AppRoute

internal const val PROJECT_SOURCE_URL = "https://github.com/kkyu9527/kixyubook"
internal const val TELEGRAM_CONTACT_URL = "https://t.me/kkyu9527s_bot"

internal class KixyuNavEntryDependencies(
    val topDestinations: State<List<TopDestination>>,
    val pagerState: PagerState,
    val navigator: KixyuNavigator,
    val initialReaderSettings: State<ReaderSettings>,
    val updateState: State<AppUpdateState>,
    val diagnosticOnlyFailures: State<Boolean>,
    val externalImportRequestId: State<Long?>,
    val externalImportUris: State<List<String>>,
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
        kixyuHomeEntries(dependencies)
        kixyuLibraryEntries(dependencies)
        kixyuSettingsEntries(dependencies)
        kixyuCloudEntries(dependencies)
        kixyuReaderEntries(dependencies)
    }
