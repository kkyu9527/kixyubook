package com.kixyu9527.kixyubook.feature.settings

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
import androidx.compose.material3.LinearProgressIndicator
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
import com.kixyu9527.kixyubook.core.sync.CloudSyncPhase
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.DriveStorageQuotaState
import com.kixyu9527.kixyubook.core.sync.SyncAccount
import com.kixyu9527.kixyubook.core.sync.InitialSyncChoice
import kotlinx.coroutines.launch

@Composable
fun CloudSyncRoute(
    onBack: () -> Unit,
    onGoogleAccount: () -> Unit,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbar = remember { SnackbarHostState() }
    val navigationContentPadding = LocalKixyuNavigationContentPadding.current
    val syncAccount = state.cloudSync.account
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
    LaunchedEffect(state.cloudSync.initialSyncDecision) {
        if (state.cloudSync.initialSyncDecision != null) conflictDeferred = false
    }
    LaunchedEffect(syncAccount?.subject) {
        if (syncAccount != null) viewModel.refreshGoogleDriveStorage()
    }
    val overviewSection: @Composable () -> Unit = {
        CloudSyncOverviewCard(
            state = state.cloudSync,
            account = syncAccount,
            onAccountClick = onGoogleAccount,
            onConnect = {
                requestNotificationPermission(false) {
                    activity?.let(viewModel::connectGoogle)
                }
            },
            connectEnabled = activity != null,
            onSyncAction = {
                if (state.cloudSync.initialSyncDecision != null) {
                    conflictDeferred = false
                } else if (state.cloudSync.phase == CloudSyncPhase.AUTH_REQUIRED) {
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
                    if (state.cloudSync.initialSyncDecision != null) {
                        conflictDeferred = false
                    } else {
                        val enabled = !state.cloudSync.enabled
                        if (enabled) requestNotificationPermission(false) {
                            viewModel.setCloudSyncEnabled(true)
                        } else viewModel.setCloudSyncEnabled(false)
                    }
                },
            ) {
                KixyuSwitch(
                    checked = state.cloudSync.enabled,
                    onCheckedChange = { enabled ->
                        if (enabled) requestNotificationPermission(false) {
                            viewModel.setCloudSyncEnabled(true)
                        } else viewModel.setCloudSyncEnabled(false)
                    },
                    enabled = state.cloudSync.initialSyncDecision == null &&
                        !state.cloudSync.inspectingInitialSync,
                )
            }
            KixyuDivider()
            KixyuDropdownRow(
                title = stringResource(R.string.settings_large_file_network),
                selected = state.cloudSync.wifiOnlyForLargeFiles,
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
                        val enabled = !state.cloudSync.syncOriginalFiles
                        if (enabled) requestNotificationPermission(false) {
                            viewModel.setSyncOriginalFiles(true)
                        } else viewModel.setSyncOriginalFiles(false)
                    },
                ) {
                    KixyuSwitch(
                        checked = state.cloudSync.syncOriginalFiles,
                        onCheckedChange = { enabled ->
                            if (enabled) requestNotificationPermission(false) {
                                viewModel.setSyncOriginalFiles(true)
                            } else viewModel.setSyncOriginalFiles(false)
                        },
                    )
                }
                KixyuDivider()
                KixyuSettingsRow(
                    title = stringResource(R.string.settings_user_fonts),
                    supportingText = stringResource(R.string.settings_sync_fonts_summary),
                    icon = KixyuSymbols.Tune,
                    onClick = {
                        val enabled = !state.cloudSync.syncFonts
                        if (enabled) requestNotificationPermission(false) {
                            viewModel.setSyncFonts(true)
                        } else viewModel.setSyncFonts(false)
                    },
                ) {
                    KixyuSwitch(
                        checked = state.cloudSync.syncFonts,
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
                    Icon(KixyuSymbols.ArrowBack, stringResource(R.string.settings_back))
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
                        state = state.cloudSync.storageQuota,
                        onRefresh = { viewModel.refreshGoogleDriveStorage(force = true) },
                    )
                }
                item { syncBehaviorSection() }
                item { syncContentSection() }
            }
            item { KixyuBottomContentSpacer() }
        }
    }

    val syncConflict = state.cloudSync.initialSyncDecision
    KixyuActionDialog(
        show = syncConflict != null && !conflictDeferred,
        title = stringResource(R.string.settings_sync_conflict_title),
        onDismissRequest = { conflictDeferred = true },
        confirmLabel = stringResource(R.string.settings_use_local),
        onConfirm = {
            viewModel.resolveInitialSync(InitialSyncChoice.KEEP_LOCAL_CHANGES)
        },
        confirmEnabled = !state.cloudSync.inspectingInitialSync,
        alternativeLabel = stringResource(R.string.settings_use_cloud),
        onAlternative = {
            viewModel.resolveInitialSync(InitialSyncChoice.USE_CLOUD_CHANGES)
        },
        alternativeEnabled = !state.cloudSync.inspectingInitialSync,
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

@Composable
private fun GoogleStorageSection(
    state: DriveStorageQuotaState,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val quota = state.quota
    val warning = quota?.isNearlyFull == true
    val accentColor = if (warning) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val usedText = quota?.let { Formatter.formatShortFileSize(context, it.usageBytes) }
    val limitText = quota?.limitBytes?.let { Formatter.formatShortFileSize(context, it) }
    val remainingText = quota?.remainingBytes?.let { Formatter.formatShortFileSize(context, it) }

    KixyuSection(title = stringResource(R.string.settings_cloud_storage_section)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = KixyuSpacing.rowHorizontal,
                    vertical = KixyuSpacing.medium,
                ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                Icon(
                    KixyuSymbols.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(KixyuSize.icon),
                    tint = accentColor,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                ) {
                    Text(
                        text = when {
                            quota == null && state.refreshing -> stringResource(R.string.settings_storage_loading)
                            quota == null -> state.errorMessage ?: stringResource(R.string.settings_storage_google)
                            limitText == null -> stringResource(R.string.settings_storage_used, usedText.orEmpty())
                            else -> stringResource(R.string.settings_storage_used_limit, usedText.orEmpty(), limitText)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (quota == null && state.errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = when {
                            warning -> stringResource(R.string.settings_storage_low, remainingText.orEmpty())
                            remainingText != null -> stringResource(R.string.settings_storage_remaining, remainingText)
                            quota != null -> stringResource(R.string.settings_storage_unlimited)
                            state.errorMessage != null -> stringResource(R.string.settings_storage_retry)
                            else -> stringResource(R.string.settings_storage_shared)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (warning) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                KixyuIconButton(
                    onClick = onRefresh,
                    enabled = !state.refreshing,
                ) {
                    Icon(KixyuSymbols.Refresh, stringResource(R.string.settings_refresh_cloud_storage))
                }
            }
            if (state.refreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(KixyuSize.progressHeight),
                )
            } else {
                quota?.usedFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(KixyuSize.progressHeight),
                        color = accentColor,
                    )
                }
            }
            quota?.takeIf { it.usageInDriveBytes > 0L }?.let {
                val driveUsage = Formatter.formatShortFileSize(context, it.usageInDriveBytes)
                val trashUsage = Formatter.formatShortFileSize(context, it.usageInDriveTrashBytes)
                Text(
                    text = stringResource(R.string.settings_storage_drive, driveUsage, trashUsage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CloudSyncOverviewCard(
    state: CloudSyncState,
    account: SyncAccount?,
    onAccountClick: () -> Unit,
    onConnect: () -> Unit,
    connectEnabled: Boolean,
    onSyncAction: () -> Unit,
) {
    KixyuSection {
        if (account == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.large),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.large),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(KixyuSize.accountAvatar),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                KixyuSymbols.CloudDone,
                                null,
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                    ) {
                        Text(stringResource(R.string.settings_sync_continue_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_connect_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                KixyuButton(
                    text = stringResource(R.string.settings_connect_drive),
                    onClick = onConnect,
                    enabled = connectEnabled,
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                )
            }
            return@KixyuSection
        }

        val status = cloudSyncStatus(state)
        val statusIndicatorColor = when (status.tone) {
            CloudSyncStatusTone.ACTIVE,
            CloudSyncStatusTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
            CloudSyncStatusTone.ATTENTION -> MaterialTheme.colorScheme.tertiaryContainer
            CloudSyncStatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer
            CloudSyncStatusTone.MUTED -> MaterialTheme.colorScheme.surfaceContainerHighest
        }
        val statusContentColor = when (status.tone) {
            CloudSyncStatusTone.ACTIVE,
            CloudSyncStatusTone.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
            CloudSyncStatusTone.ATTENTION -> MaterialTheme.colorScheme.onTertiaryContainer
            CloudSyncStatusTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
            CloudSyncStatusTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val prominentAction = state.initialSyncDecision != null ||
            state.phase in setOf(CloudSyncPhase.AUTH_REQUIRED, CloudSyncPhase.ERROR)
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            KixyuSettingsRow(
                title = account.displayName,
                supportingText = account.email,
                leading = { GoogleAccountAvatar(account) },
                onClick = onAccountClick,
            ) {
                Text(
                    stringResource(R.string.settings_connected),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    KixyuSymbols.KeyboardArrowRight,
                    null,
                    Modifier.size(KixyuSize.icon),
                )
            }
            KixyuDivider()
            KixyuSettingsRow(
                title = status.title,
                supportingText = status.detail,
                leading = {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = statusIndicatorColor,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                status.icon,
                                null,
                                modifier = Modifier.size(KixyuSize.icon),
                                tint = statusContentColor,
                            )
                        }
                    }
                },
            ) {
                if (!status.busy && !prominentAction && cloudSyncActionEnabled(state)) {
                    KixyuIconButton(onClick = onSyncAction) {
                        Icon(
                            KixyuSymbols.Refresh,
                            stringResource(R.string.settings_sync_now),
                        )
                    }
                }
            }
            if (status.busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KixyuSpacing.rowHorizontal)
                        .height(KixyuSize.progressHeight),
                )
            }
            if (prominentAction) {
                KixyuButton(
                    text = cloudSyncActionLabel(state),
                    onClick = onSyncAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = KixyuSpacing.rowHorizontal,
                            end = KixyuSpacing.rowHorizontal,
                            bottom = KixyuSpacing.medium,
                        ),
                    enabled = cloudSyncActionEnabled(state),
                )
            }
        }
    }
}

@Composable
private fun cloudSyncActionLabel(state: CloudSyncState): String = when {
    state.initialSyncDecision != null -> stringResource(R.string.settings_resolve_conflicts)
    state.inspectingInitialSync -> stringResource(R.string.settings_checking_cloud)
    state.phase == CloudSyncPhase.AUTHORIZING -> stringResource(R.string.settings_authorizing)
    state.phase == CloudSyncPhase.AUTH_REQUIRED -> stringResource(R.string.settings_reauthorize)
    state.phase == CloudSyncPhase.SYNCING -> stringResource(R.string.settings_syncing)
    !state.enabled -> stringResource(R.string.settings_sync_status_paused)
    state.phase == CloudSyncPhase.ERROR -> stringResource(R.string.settings_retry_sync)
    else -> stringResource(R.string.settings_sync_now)
}

private fun cloudSyncActionEnabled(state: CloudSyncState): Boolean =
    !state.inspectingInitialSync &&
        state.phase !in setOf(CloudSyncPhase.AUTHORIZING, CloudSyncPhase.SYNCING) &&
        (state.enabled || state.phase == CloudSyncPhase.AUTH_REQUIRED || state.initialSyncDecision != null)

private enum class CloudSyncStatusTone { ACTIVE, SUCCESS, ATTENTION, ERROR, MUTED }

private data class CloudSyncStatusUi(
    val title: String,
    val detail: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tone: CloudSyncStatusTone,
    val busy: Boolean = false,
)

@Composable
private fun cloudSyncStatus(state: CloudSyncState): CloudSyncStatusUi {
    val locale = LocalLocale.current.platformLocale
    val conflict = state.initialSyncDecision
    val lastSync = state.lastSyncTime.takeIf { it > 0 }?.let {
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, locale,
        ).format(Date(it))
    }
    return when {
        conflict != null -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_sync_status_waiting_conflict),
            detail = stringResource(R.string.settings_sync_conflict_count, conflict.conflicts.size),
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ATTENTION,
        )
        state.inspectingInitialSync -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_sync_status_checking_library),
            detail = stringResource(R.string.settings_identifying_data),
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
            busy = true,
        )
        !state.enabled -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_sync_status_paused),
            detail = lastSync?.let { stringResource(R.string.settings_last_sync, it) } ?: stringResource(R.string.settings_enable_sync_hint),
            icon = KixyuSymbols.Cloud,
            tone = CloudSyncStatusTone.MUTED,
        )
        state.phase == CloudSyncPhase.AUTHORIZING -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_sync_status_connecting),
            detail = stringResource(R.string.settings_confirming_access),
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
            busy = true,
        )
        state.phase == CloudSyncPhase.AUTH_REQUIRED -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_sync_status_reauthorize),
            detail = stringResource(R.string.settings_reconnect_hint),
            icon = KixyuSymbols.Cloud,
            tone = CloudSyncStatusTone.ATTENTION,
        )
        state.phase == CloudSyncPhase.SYNCING -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_sync_status_syncing),
            detail = if (state.pendingCount > 0) stringResource(R.string.settings_pending_changes, state.pendingCount) else stringResource(R.string.settings_checking_changes),
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
            busy = true,
        )
        state.phase == CloudSyncPhase.ERROR -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_sync_status_error),
            detail = state.errorMessage ?: stringResource(R.string.settings_check_network),
            icon = KixyuSymbols.Cloud,
            tone = CloudSyncStatusTone.ERROR,
        )
        state.pendingCount > 0 -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_sync_status_waiting),
            detail = stringResource(R.string.settings_pending_upload, state.pendingCount),
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ATTENTION,
        )
        lastSync != null -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_sync_status_complete),
            detail = stringResource(R.string.settings_last_sync, lastSync),
            icon = KixyuSymbols.CloudDone,
            tone = CloudSyncStatusTone.SUCCESS,
        )
        else -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_sync_status_initial),
            detail = stringResource(R.string.settings_wait_network),
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
        )
    }
}
