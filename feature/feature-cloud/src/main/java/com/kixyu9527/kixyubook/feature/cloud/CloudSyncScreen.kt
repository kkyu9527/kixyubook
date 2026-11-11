package com.kixyu9527.kixyubook.feature.cloud

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import android.app.Activity
import android.text.format.Formatter
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDropdownRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuProgressBar
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSwitch
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationContentPadding
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.displayName
import java.util.Date
import com.kixyu9527.kixyubook.core.sync.CloudBookDownload
import com.kixyu9527.kixyubook.core.sync.CloudBookDownloadStatus
import com.kixyu9527.kixyubook.core.sync.CloudSyncPhase
import com.kixyu9527.kixyubook.feature.cloud.R
import com.kixyu9527.kixyubook.core.ui.rememberNotificationPermissionAction
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.DriveStorageQuotaState
import com.kixyu9527.kixyubook.core.sync.SyncAccount
import com.kixyu9527.kixyubook.core.sync.InitialSyncChoice
import kotlinx.coroutines.launch

@Composable
fun CloudSyncRoute(
    onBack: () -> Unit,
    onGoogleAccount: () -> Unit,
    onBookDownloads: () -> Unit,
    embedded: Boolean = false,
    viewModel: CloudSettingsViewModel = hiltViewModel(),
) = com.kixyu9527.kixyubook.core.designsystem.component.KixyuOperationHost(viewModel.operations) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbar = remember { SnackbarHostState() }
    val navigationContentPadding = LocalKixyuNavigationContentPadding.current
    val syncAccount = state.account
    var conflictDeferred by rememberSaveable { mutableStateOf(false) }
    val requestNotificationPermission = rememberNotificationPermissionAction()
    val authorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (activity != null) viewModel.finishGoogleAuthorization(activity, result.data)
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.authorizationRequests.collect { pendingIntent ->
            authorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }
    LaunchedEffect(state.initialSyncDecision) {
        if (state.initialSyncDecision != null) conflictDeferred = false
    }
    LaunchedEffect(syncAccount?.subject) {
        if (syncAccount != null) viewModel.refreshGoogleDriveStorage()
    }
    val overviewSection: @Composable () -> Unit = {
        CloudSyncOverviewCard(
            state = state,
            account = syncAccount,
            onAccountClick = onGoogleAccount,
            onBookDownloads = onBookDownloads,
            onConnect = {
                requestNotificationPermission(false) {
                    activity?.let(viewModel::connectGoogle)
                }
            },
            connectEnabled = activity != null,
            onSyncAction = {
                if (state.initialSyncDecision != null) {
                    conflictDeferred = false
                } else if (state.phase == CloudSyncPhase.AUTH_REQUIRED) {
                    activity?.let(viewModel::connectGoogle)
                } else {
                    viewModel.syncNow()
                }
            },
        )
    }
    val syncBehaviorSection: @Composable () -> Unit = {
        KixyuSection(title = stringResource(R.string.settings_sync_section)) {
            KixyuSettingsRow(
                title = stringResource(R.string.settings_auto_sync),
                supportingText = stringResource(R.string.settings_auto_sync_summary),
                icon = KixyuSymbols.CloudSync,
                onClick = {
                    if (state.initialSyncDecision != null) {
                        conflictDeferred = false
                    } else {
                        val enabled = !state.enabled
                        if (enabled) requestNotificationPermission(false) {
                            viewModel.setCloudSyncEnabled(true)
                        } else viewModel.setCloudSyncEnabled(false)
                    }
                },
            ) {
                KixyuSwitch(
                    checked = state.enabled,
                    onCheckedChange = { enabled ->
                        if (enabled) requestNotificationPermission(false) {
                            viewModel.setCloudSyncEnabled(true)
                        } else viewModel.setCloudSyncEnabled(false)
                    },
                    enabled = state.initialSyncDecision == null &&
                        !state.inspectingInitialSync,
                )
            }
            KixyuDivider()
            KixyuDropdownRow(
                title = stringResource(R.string.settings_large_file_network),
                selected = state.wifiOnlyForLargeFiles,
                options = listOf(true, false),
                optionLabel = { wifiOnly ->
                    if (wifiOnly) stringResource(R.string.settings_wifi_only) else stringResource(R.string.settings_any_network)
                },
                onSelected = viewModel::setWifiOnlyForLargeFiles,
                icon = KixyuSymbols.Wifi,
            )
        }
    }
    val syncContentSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
            KixyuSection(title = stringResource(R.string.settings_sync_content_section)) {
                KixyuSettingsRow(
                    title = stringResource(R.string.settings_original_book_files),
                    supportingText = stringResource(R.string.settings_sync_books_summary),
                    icon = KixyuSymbols.Backup,
                    onClick = {
                        val enabled = !state.syncOriginalFiles
                        if (enabled) requestNotificationPermission(false) {
                            viewModel.setSyncOriginalFiles(true)
                        } else viewModel.setSyncOriginalFiles(false)
                    },
                ) {
                    KixyuSwitch(
                        checked = state.syncOriginalFiles,
                        onCheckedChange = { enabled ->
                            if (enabled) requestNotificationPermission(false) {
                                viewModel.setSyncOriginalFiles(true)
                            } else viewModel.setSyncOriginalFiles(false)
                        },
                    )
                }
                if (state.syncOriginalFiles || state.bookDownloads.isNotEmpty()) {
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_book_download_tasks),
                        supportingText = bookDownloadSummary(state.bookDownloads),
                        icon = KixyuSymbols.Cloud,
                        onClick = onBookDownloads,
                    ) {
                        Icon(
                            KixyuSymbols.KeyboardArrowRight,
                            null,
                            Modifier.size(KixyuSize.icon),
                        )
                    }
                }
                KixyuDivider()
                KixyuSettingsRow(
                    title = stringResource(R.string.settings_user_fonts),
                    supportingText = stringResource(R.string.settings_sync_fonts_summary),
                    icon = KixyuSymbols.Tune,
                    onClick = {
                        val enabled = !state.syncFonts
                        if (enabled) requestNotificationPermission(false) {
                            viewModel.setSyncFonts(true)
                        } else viewModel.setSyncFonts(false)
                    },
                ) {
                    KixyuSwitch(
                        checked = state.syncFonts,
                        onCheckedChange = { enabled ->
                            if (enabled) requestNotificationPermission(false) {
                                viewModel.setSyncFonts(true)
                            } else viewModel.setSyncFonts(false)
                        },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_always_synced),
                modifier = Modifier.padding(horizontal = KixyuSpacing.extraSmall),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    KixyuPageScaffold(
        title = stringResource(R.string.settings_google_drive_sync),
        largeTitle = false,
        showTopBar = !embedded,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            if (!embedded) {
                KixyuIconButton(onClick = onBack) {
                    Icon(KixyuSymbols.ArrowBack, stringResource(R.string.cloud_back))
                }
            }
        },
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            Modifier.kixyuPageContentWidth()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item { overviewSection() }
            if (syncAccount != null) {
                item {
                    GoogleStorageSection(
                        state = state.storageQuota,
                        onRefresh = { viewModel.refreshGoogleDriveStorage(force = true) },
                    )
                }
                item { syncBehaviorSection() }
                item { syncContentSection() }
            }
            item { KixyuBottomContentSpacer() }
        }
    }

    val syncConflict = state.initialSyncDecision
    KixyuActionDialog(
        show = syncConflict != null && !conflictDeferred,
        title = stringResource(R.string.settings_sync_conflict_title),
        onDismissRequest = { conflictDeferred = true },
        confirmLabel = stringResource(R.string.settings_use_local),
        onConfirm = {
            viewModel.resolveInitialSync(InitialSyncChoice.KEEP_LOCAL_CHANGES)
        },
        confirmEnabled = !state.inspectingInitialSync,
        alternativeLabel = stringResource(R.string.settings_use_cloud),
        onAlternative = {
            viewModel.resolveInitialSync(InitialSyncChoice.USE_CLOUD_CHANGES)
        },
        alternativeEnabled = !state.inspectingInitialSync,
        dismissLabel = stringResource(R.string.settings_resolve_later),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
            Text(stringResource(R.string.settings_sync_conflict_count, syncConflict?.conflicts?.size ?: 0))
            Text(
                stringResource(R.string.settings_conflict_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

}
