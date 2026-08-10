package com.kixyu9527.kixyubook.feature.settings

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppColorControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppUiStyleControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuFontControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderBehaviorControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderLayoutControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderThemeControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSecondaryButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuStepperRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSwitch
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuThemeModeControl
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationContentPadding
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowSizeClass
import com.kixyu9527.kixyubook.core.designsystem.component.displayName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess
import com.kixyu9527.kixyubook.core.sync.CloudSyncPhase
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.SyncAccount
import com.kixyu9527.kixyubook.core.sync.InitialSyncChoice
import com.kixyu9527.kixyubook.core.sync.BackupOperationType
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

enum class SettingsPane { CLOUD_SYNC, READING, APPEARANCE, DATA_AND_BACKUP, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onCloudSync: () -> Unit,
    onReadingSettings: () -> Unit,
    onAppearance: () -> Unit,
    onDataAndBackup: () -> Unit,
    onAbout: () -> Unit,
    currentVersion: String,
    detailContent: (@Composable (SettingsPane) -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val syncAccount = state.cloudSync.account
    val windowSizeClass = kixyuWindowSizeClass()
    val twoPane = windowSizeClass.supportsTwoPane && detailContent != null
    var selectedPaneName by rememberSaveable { mutableStateOf(SettingsPane.READING.name) }
    val selectedPane = SettingsPane.entries.firstOrNull { it.name == selectedPaneName } ?: SettingsPane.READING
    val openPane: (SettingsPane, () -> Unit) -> Unit = { pane, compactNavigation ->
        if (twoPane) selectedPaneName = pane.name else compactNavigation()
    }

    val accountSection: @Composable () -> Unit = {
        KixyuSection(title = "账号") {
            KixyuSettingsRow(
                title = "Google 同步",
                supportingText = when {
                    syncAccount == null -> "登录后在设备间增量同步"
                    state.cloudSync.initialSyncDecision != null -> "需要处理同步冲突 · ${syncAccount.email}"
                    state.cloudSync.phase == CloudSyncPhase.SYNCING -> "正在同步 · ${syncAccount.email}"
                    state.cloudSync.pendingCount > 0 -> "${state.cloudSync.pendingCount} 项等待同步 · ${syncAccount.email}"
                    else -> syncAccount.email
                },
                icon = when {
                    syncAccount == null -> Icons.Outlined.Cloud
                    state.cloudSync.initialSyncDecision != null -> Icons.Outlined.CloudSync
                    else -> Icons.Outlined.CloudDone
                },
                selected = if (twoPane) selectedPane == SettingsPane.CLOUD_SYNC else null,
                onClick = { openPane(SettingsPane.CLOUD_SYNC, onCloudSync) },
            ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }
    val preferenceSection: @Composable () -> Unit = {
        KixyuSection(title = "偏好设置") {
            KixyuSettingsRow(
                title = "阅读",
                supportingText = buildString {
                    append(state.settings.pageMode.displayName())
                    append(" · ")
                    append(state.fonts.firstOrNull { it.uuid == state.settings.fontUuid }?.name ?: "系统字体")
                },
                icon = Icons.Outlined.Tune,
                selected = if (twoPane) selectedPane == SettingsPane.READING else null,
                onClick = { openPane(SettingsPane.READING, onReadingSettings) },
            ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
            KixyuDivider()
            KixyuSettingsRow(
                title = "外观",
                supportingText = "${state.settings.theme.displayName()} · ${state.settings.appUiStyle.displayName()} · ${state.settings.appColorTheme.displayName()}",
                icon = Icons.Outlined.Palette,
                selected = if (twoPane) selectedPane == SettingsPane.APPEARANCE else null,
                onClick = { openPane(SettingsPane.APPEARANCE, onAppearance) },
            ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }
    val dataSection: @Composable () -> Unit = {
        KixyuSection(title = "数据") {
            KixyuSettingsRow(
                title = "数据与备份",
                supportingText = "本地导出恢复数据",
                icon = Icons.Outlined.Backup,
                selected = if (twoPane) selectedPane == SettingsPane.DATA_AND_BACKUP else null,
                onClick = { openPane(SettingsPane.DATA_AND_BACKUP, onDataAndBackup) },
            ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }
    val aboutSection: @Composable () -> Unit = {
        KixyuSection(title = "关于") {
            KixyuSettingsRow(
                title = "关于 Kixyu Book",
                supportingText = "版本 $currentVersion · 更新与项目信息",
                icon = Icons.Outlined.Info,
                selected = if (twoPane) selectedPane == SettingsPane.ABOUT else null,
                onClick = { openPane(SettingsPane.ABOUT, onAbout) },
            ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }

    KixyuPageScaffold(
        title = "设置",
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        if (twoPane) {
            Row(
                modifier = Modifier.kixyuPageContentWidth(KixyuSize.expandedPageContentMaxWidth)
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .padding(horizontal = KixyuSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(.36f).fillMaxSize(),
                    contentPadding = PaddingValues(vertical = KixyuSpacing.screenVertical),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
                ) {
                    item { accountSection() }
                    item { preferenceSection() }
                    item { dataSection() }
                    item { aboutSection() }
                    item { KixyuBottomContentSpacer() }
                }
                Surface(
                    modifier = Modifier.weight(.64f).fillMaxSize()
                        .padding(top = KixyuSpacing.screenVertical),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Box(Modifier.fillMaxSize()) { detailContent.invoke(selectedPane) }
                }
            }
        } else {
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
                item { accountSection() }
                item { preferenceSection() }
                item { dataSection() }
                item { aboutSection() }
                item { KixyuBottomContentSpacer() }
            }
        }
    }
}

@Composable
fun CloudSyncRoute(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbar = remember { SnackbarHostState() }
    val navigationContentPadding = LocalKixyuNavigationContentPadding.current
    val syncAccount = state.cloudSync.account
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
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

    val overviewSection: @Composable () -> Unit = {
        CloudSyncOverviewCard(
            state = state.cloudSync,
            account = syncAccount,
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
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
            CloudSyncSectionHeader(
                title = "同步方式",
                detail = "控制同步发生的时机与网络",
            )
            KixyuSection {
                KixyuSettingsRow(
                    title = "自动同步",
                    supportingText = "有变更时在后台安静地增量同步",
                    icon = Icons.Outlined.CloudSync,
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
                KixyuSettingsRow(
                    title = "仅通过 Wi-Fi 同步大文件",
                    supportingText = "避免书籍和字体使用移动数据",
                    icon = Icons.Outlined.Wifi,
                    onClick = {
                        viewModel.setWifiOnlyForLargeFiles(!state.cloudSync.wifiOnlyForLargeFiles)
                    },
                ) {
                    KixyuSwitch(
                        checked = state.cloudSync.wifiOnlyForLargeFiles,
                        onCheckedChange = viewModel::setWifiOnlyForLargeFiles,
                    )
                }
            }
        }
    }
    val syncContentSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
            CloudSyncSectionHeader(
                title = "附加内容",
                detail = "阅读数据始终同步",
            )
            KixyuSection {
                KixyuSettingsRow(
                    title = "原始书籍文件",
                    supportingText = "同步 TXT / EPUB，供其他设备完整恢复",
                    icon = Icons.Outlined.Backup,
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
                    title = "用户字体",
                    supportingText = "同步已导入的 TTF / OTF",
                    icon = Icons.Outlined.Tune,
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
        }
    }
    val syncPreferencesSection: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 700.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.weight(1f)) { syncBehaviorSection() }
                    Box(Modifier.weight(1f)) { syncContentSection() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap)) {
                    syncBehaviorSection()
                    syncContentSection()
                }
            }
        }
    }
    val accountActionsSection: @Composable () -> Unit = {
        KixyuSection(title = "账号管理") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.rowHorizontal),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            ) {
                KixyuSecondaryButton(
                    text = "断开 Google 账号",
                    onClick = viewModel::disconnectGoogle,
                    modifier = Modifier.fillMaxWidth(),
                )
                KixyuTextButton(
                    text = "删除 Google Drive 中的同步数据",
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    KixyuPageScaffold(
        title = "Google Drive 同步",
        largeTitle = false,
        showTopBar = !embedded,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            if (!embedded) {
                KixyuIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            }
        },
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = navigationContentPadding + KixyuSpacing.medium)
                    .padding(horizontal = KixyuSpacing.screenHorizontal),
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
                item { syncPreferencesSection() }
                item { accountActionsSection() }
            }
            item { KixyuBottomContentSpacer() }
        }
    }

    KixyuActionDialog(
        show = confirmDelete,
        title = "删除云端同步数据？",
        onDismissRequest = { confirmDelete = false },
        confirmLabel = "永久删除",
        onConfirm = {
            confirmDelete = false
            activity?.let(viewModel::deleteCloudData)
        },
    ) {
        Text("此操作不会删除本机数据，但无法从 Google Drive 恢复。")
    }

    val syncConflict = state.cloudSync.initialSyncDecision
    KixyuActionDialog(
        show = syncConflict != null && !conflictDeferred && !confirmDelete,
        title = "发现同步冲突",
        onDismissRequest = { conflictDeferred = true },
        confirmLabel = "使用本机更改",
        onConfirm = {
            viewModel.resolveInitialSync(InitialSyncChoice.KEEP_LOCAL_CHANGES)
        },
        confirmEnabled = !state.cloudSync.inspectingInitialSync,
        alternativeLabel = "使用云端更改",
        onAlternative = {
            viewModel.resolveInitialSync(InitialSyncChoice.USE_CLOUD_CHANGES)
        },
        alternativeEnabled = !state.cloudSync.inspectingInitialSync,
        dismissLabel = "稍后处理",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
            Text("有 ${syncConflict?.conflicts?.size ?: 0} 项内容在本机和云端都发生了修改。")
            Text(
                "阅读进度、阅读记录和删除操作会自动合并；这里只列出无法安全判断的书籍信息、书签或阅读设置。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

}

@Composable
private fun CloudSyncOverviewCard(
    state: CloudSyncState,
    account: SyncAccount?,
    onConnect: () -> Unit,
    connectEnabled: Boolean,
    onSyncAction: () -> Unit,
) {
    KixyuSection {
        if (account == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.CloudDone,
                            null,
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                ) {
                    Text(
                        "在每台设备继续阅读",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "同步书库、阅读进度、书签和个性化设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                KixyuButton(
                    text = "连接 Google Drive",
                    onClick = onConnect,
                    enabled = connectEnabled,
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        null,
                        modifier = Modifier.size(KixyuSize.iconSmall),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "仅使用 Kixyu Book 的 Google Drive 应用专属空间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@KixyuSection
        }

        val status = cloudSyncStatus(state)
        val statusContainerColor = when (status.tone) {
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
        Column(
            modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.large),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.large),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                GoogleAccountAvatar(account)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                ) {
                    Text(account.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        account.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Cloud,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Google Drive",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = MaterialTheme.shapes.large,
                color = statusContainerColor,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            status.icon,
                            null,
                            modifier = Modifier.size(26.dp),
                            tint = statusContentColor,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                        ) {
                            Text(
                                status.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = statusContentColor,
                            )
                            Text(
                                status.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusContentColor.copy(alpha = .78f),
                            )
                        }
                    }
                    if (status.busy) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(KixyuSize.progressHeight),
                            color = statusContentColor,
                            trackColor = statusContentColor.copy(alpha = .16f),
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.CloudDone,
                    null,
                    modifier = Modifier.size(KixyuSize.iconSmall),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "阅读数据已纳入同步",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "书库、进度、书签、统计与阅读设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            KixyuButton(
                text = cloudSyncActionLabel(state),
                onClick = onSyncAction,
                modifier = Modifier.fillMaxWidth(),
                enabled = cloudSyncActionEnabled(state),
            )
        }
    }
}

@Composable
private fun CloudSyncSectionHeader(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = KixyuSpacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun cloudSyncActionLabel(state: CloudSyncState): String = when {
    state.initialSyncDecision != null -> "处理同步冲突"
    state.inspectingInitialSync -> "正在检查云端…"
    state.phase == CloudSyncPhase.AUTHORIZING -> "正在授权…"
    state.phase == CloudSyncPhase.AUTH_REQUIRED -> "重新授权"
    state.phase == CloudSyncPhase.SYNCING -> "正在同步…"
    !state.enabled -> "同步已暂停"
    state.phase == CloudSyncPhase.ERROR -> "重试同步"
    else -> "立即同步"
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

/** Requests notification permission only after a related user action, never at app launch. */
@Composable
internal fun rememberNotificationPermissionAction(): (Boolean, () -> Unit) -> Unit {
    val context = LocalContext.current
    val permissionPreferences = remember(context) {
        context.getSharedPreferences("notification_state", android.content.Context.MODE_PRIVATE)
    }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var permissionRequired by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted || !permissionRequired) action?.invoke()
        permissionRequired = false
    }
    return remember(context, launcher) {
        actionGate@{ requirePermission, action ->
            val alreadyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (alreadyGranted) {
                action()
            } else {
                val now = System.currentTimeMillis()
                val lastRequest = permissionPreferences.getLong("last_permission_request", 0L)
                if (now - lastRequest < 48 * 60 * 60_000L) {
                    if (!requirePermission) action()
                    return@actionGate
                }
                pendingAction = action
                permissionRequired = requirePermission
                permissionPreferences.edit { putLong("last_permission_request", now) }
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun GoogleAccountAvatar(account: SyncAccount) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.size(KixyuSize.accountAvatar).clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                account.displayName.trim().firstOrNull()?.uppercase() ?: "G",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            account.avatarUrl?.let { avatarUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "${account.displayName}的账号头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
        }
    }
}

private fun cloudSyncStatus(state: CloudSyncState): CloudSyncStatusUi {
    val conflict = state.initialSyncDecision
    val lastSync = state.lastSyncTime.takeIf { it > 0 }?.let {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it))
    }
    return when {
        conflict != null -> CloudSyncStatusUi(
            title = "等待处理同步冲突",
            detail = "${conflict.conflicts.size} 项内容在本机和云端都已修改",
            icon = Icons.Outlined.CloudSync,
            tone = CloudSyncStatusTone.ATTENTION,
        )
        state.inspectingInitialSync -> CloudSyncStatusUi(
            title = "正在检查云端书库",
            detail = "正在识别本机与云端数据",
            icon = Icons.Outlined.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
            busy = true,
        )
        !state.enabled -> CloudSyncStatusUi(
            title = "同步已暂停",
            detail = lastSync?.let { "上次同步于 $it" } ?: "开启自动同步后开始上传数据",
            icon = Icons.Outlined.Cloud,
            tone = CloudSyncStatusTone.MUTED,
        )
        state.phase == CloudSyncPhase.AUTHORIZING -> CloudSyncStatusUi(
            title = "正在连接 Google Drive",
            detail = "正在确认账号与访问权限",
            icon = Icons.Outlined.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
            busy = true,
        )
        state.phase == CloudSyncPhase.AUTH_REQUIRED -> CloudSyncStatusUi(
            title = "需要重新授权",
            detail = "进入应用后会自动恢复 Google Drive 连接",
            icon = Icons.Outlined.Cloud,
            tone = CloudSyncStatusTone.ATTENTION,
        )
        state.phase == CloudSyncPhase.SYNCING -> CloudSyncStatusUi(
            title = "正在同步",
            detail = if (state.pendingCount > 0) "${state.pendingCount} 项本地变更等待完成" else "正在检查云端变更",
            icon = Icons.Outlined.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
            busy = true,
        )
        state.phase == CloudSyncPhase.ERROR -> CloudSyncStatusUi(
            title = "同步遇到问题",
            detail = state.errorMessage ?: "请检查网络后重试",
            icon = Icons.Outlined.Cloud,
            tone = CloudSyncStatusTone.ERROR,
        )
        state.pendingCount > 0 -> CloudSyncStatusUi(
            title = "等待同步",
            detail = "${state.pendingCount} 项本地变更等待上传",
            icon = Icons.Outlined.CloudSync,
            tone = CloudSyncStatusTone.ATTENTION,
        )
        lastSync != null -> CloudSyncStatusUi(
            title = "所有数据均已同步",
            detail = "上次同步于 $lastSync",
            icon = Icons.Outlined.CloudDone,
            tone = CloudSyncStatusTone.SUCCESS,
        )
        else -> CloudSyncStatusUi(
            title = "等待首次同步",
            detail = "连接网络后将自动开始",
            icon = Icons.Outlined.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
        )
    }
}
