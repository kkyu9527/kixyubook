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
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.ui.rememberNotificationPermissionAction
import com.kixyu9527.kixyubook.core.sync.DriveStorageQuotaState
import com.kixyu9527.kixyubook.core.sync.SyncAccount
import com.kixyu9527.kixyubook.core.sync.InitialSyncChoice
import kotlinx.coroutines.launch

@Composable
internal fun GoogleStorageSection(
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
                KixyuProgressBar(progress = null, wavy = true)
            } else {
                quota?.usedFraction?.let { fraction ->
                    KixyuProgressBar(progress = fraction, color = accentColor, wavy = true)
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
internal fun CloudSyncOverviewCard(
    state: CloudSyncState,
    account: SyncAccount?,
    onAccountClick: () -> Unit,
    onBookDownloads: () -> Unit,
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
                onClick = if (state.pendingBookDownloads > 0) onBookDownloads else null,
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
                KixyuProgressBar(
                    progress = null,
                    modifier = Modifier.padding(horizontal = KixyuSpacing.rowHorizontal),
                    wavy = true,
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
    val detail: String?,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tone: CloudSyncStatusTone,
    val busy: Boolean = false,
)

@Composable
internal fun bookDownloadSummary(downloads: List<CloudBookDownload>): String {
    val failed = downloads.count { it.status == CloudBookDownloadStatus.FAILED }
    val running = downloads.count { it.status == CloudBookDownloadStatus.RUNNING }
    return when {
        downloads.isEmpty() -> stringResource(R.string.settings_book_download_summary_empty)
        failed > 0 -> stringResource(R.string.settings_book_download_summary_failed, failed, downloads.size)
        running > 0 -> stringResource(R.string.settings_book_download_summary_running, running, downloads.size)
        else -> stringResource(R.string.settings_book_download_summary_pending, downloads.size)
    }
}

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
            detail = when {
                state.pendingBookDownloads > 0 -> stringResource(R.string.settings_sync_pending_bodies, state.pendingBookDownloads)
                state.pendingCount > 0 -> stringResource(R.string.settings_pending_changes, state.pendingCount)
                else -> stringResource(R.string.settings_checking_changes)
            },
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
        state.pendingBookDownloads > 0 -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_sync_pending_bodies, state.pendingBookDownloads),
            detail = when {
                state.bookDownloads.any { it.status == CloudBookDownloadStatus.FAILED } -> stringResource(R.string.settings_book_download_failed)
                state.bookDownloads.any { it.status == CloudBookDownloadStatus.RUNNING } -> stringResource(R.string.settings_book_download_running)
                !state.syncOriginalFiles -> stringResource(R.string.settings_sync_bodies_disabled)
                state.wifiOnlyForLargeFiles -> stringResource(R.string.settings_sync_bodies_wifi)
                else -> stringResource(R.string.settings_sync_bodies_resume)
            },
            icon = KixyuSymbols.CloudSync,
            tone = if (state.bookDownloads.any { it.status == CloudBookDownloadStatus.RUNNING }) CloudSyncStatusTone.ACTIVE else CloudSyncStatusTone.ATTENTION,
            busy = state.bookDownloads.any { it.status == CloudBookDownloadStatus.RUNNING },
        )
        lastSync != null -> CloudSyncStatusUi(
            title = stringResource(R.string.settings_last_sync, lastSync),
            detail = null,
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
