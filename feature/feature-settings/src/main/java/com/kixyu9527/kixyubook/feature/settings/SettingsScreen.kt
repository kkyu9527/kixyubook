package com.kixyu9527.kixyubook.feature.settings

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDropdownRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSecondaryButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSwitch
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationContentPadding
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowSizeClass
import com.kixyu9527.kixyubook.core.designsystem.component.displayName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess
import com.kixyu9527.kixyubook.core.sync.BackupOperationType
import com.kixyu9527.kixyubook.core.sync.CloudSyncPhase
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.DriveStorageQuotaState
import com.kixyu9527.kixyubook.core.sync.SyncAccount
import com.kixyu9527.kixyubook.core.sync.InitialSyncChoice
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch

enum class SettingsPane { CLOUD_SYNC, READING, APPEARANCE, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onCloudSync: () -> Unit,
    onReadingSettings: () -> Unit,
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
    currentVersion: String,
    detailContent: (@Composable (SettingsPane) -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val requestNotificationPermission = rememberNotificationPermissionAction()
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    var restored by rememberSaveable { mutableStateOf(false) }
    val backupCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.exportBackup(it.toString())
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            pendingRestore = it.toString()
        }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.restoreCompleted.collect { restored = true } }
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
                    syncAccount == null -> KixyuSymbols.Cloud
                    state.cloudSync.initialSyncDecision != null -> KixyuSymbols.CloudSync
                    else -> KixyuSymbols.CloudDone
                },
                selected = if (twoPane) selectedPane == SettingsPane.CLOUD_SYNC else null,
                onClick = { openPane(SettingsPane.CLOUD_SYNC, onCloudSync) },
            ) { Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
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
                icon = KixyuSymbols.Tune,
                selected = if (twoPane) selectedPane == SettingsPane.READING else null,
                onClick = { openPane(SettingsPane.READING, onReadingSettings) },
            ) { Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
            KixyuDivider()
            KixyuSettingsRow(
                title = "外观",
                supportingText = "${state.settings.theme.displayName()} · ${state.settings.appUiStyle.displayName()} · ${state.settings.appColorTheme.displayName()}",
                icon = KixyuSymbols.Palette,
                selected = if (twoPane) selectedPane == SettingsPane.APPEARANCE else null,
                onClick = { openPane(SettingsPane.APPEARANCE, onAppearance) },
            ) { Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }
    val dataSection: @Composable () -> Unit = {
        KixyuSection(title = "数据") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = KixyuSpacing.rowHorizontal,
                    vertical = KixyuSpacing.medium,
                ),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.large),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                KixyuSymbols.Backup,
                                null,
                                Modifier.size(KixyuSize.icon),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                    ) {
                        Text(
                            "本地完整备份",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            when (state.backupOperation) {
                                BackupOperationType.EXPORT -> "正在整理并导出完整数据…"
                                BackupOperationType.RESTORE -> "正在恢复书库与设置…"
                                null -> "包含书籍、阅读进度、设置与用户字体"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                ) {
                    KixyuSecondaryButton(
                        text = if (state.backupOperation == BackupOperationType.RESTORE) {
                            "正在恢复…"
                        } else {
                            "恢复备份"
                        },
                        onClick = {
                            requestNotificationPermission(false) {
                                backupPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                            }
                        },
                        enabled = state.backupOperation == null,
                        modifier = Modifier.weight(1f),
                    )
                    KixyuButton(
                        text = if (state.backupOperation == BackupOperationType.EXPORT) {
                            "正在导出…"
                        } else {
                            "导出备份"
                        },
                        onClick = {
                            requestNotificationPermission(false) {
                                backupCreator.launch(
                                    "KixyuBook-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.kixyubackup",
                                )
                            }
                        },
                        enabled = state.backupOperation == null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    val aboutSection: @Composable () -> Unit = {
        KixyuSection(title = "关于") {
            KixyuSettingsRow(
                title = "关于 Kixyu Book",
                supportingText = "版本 $currentVersion · 更新与项目信息",
                icon = KixyuSymbols.Info,
                selected = if (twoPane) selectedPane == SettingsPane.ABOUT else null,
                onClick = { openPane(SettingsPane.ABOUT, onAbout) },
            ) { Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }

    KixyuPageScaffold(
        title = "设置",
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
            )
        },
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

    pendingRestore?.let { uri ->
        KixyuActionDialog(
            show = true,
            title = "恢复完整备份？",
            onDismissRequest = { pendingRestore = null },
            confirmLabel = "开始恢复",
            onConfirm = {
                pendingRestore = null
                viewModel.restoreBackup(uri)
            },
        ) {
            Text("当前书库和设置将被备份内容替换，完成后需要重新启动应用。")
        }
    }
    KixyuActionDialog(
        show = restored,
        title = "恢复完成",
        onDismissRequest = {},
        confirmLabel = "关闭应用",
        dismissLabel = null,
        onConfirm = {
            (context as? Activity)?.finishAffinity()
            exitProcess(0)
        },
    ) {
        Text("请关闭后重新打开应用，以加载恢复后的书库。")
    }
}

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
        KixyuSection(title = "同步") {
            KixyuSettingsRow(
                title = "自动同步",
                supportingText = "有变更时在后台安静地增量同步",
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
                title = "大文件同步网络",
                selected = state.cloudSync.wifiOnlyForLargeFiles,
                options = listOf(true, false),
                optionLabel = { wifiOnly ->
                    if (wifiOnly) "仅 Wi-Fi" else "Wi-Fi 和移动数据"
                },
                onSelected = viewModel::setWifiOnlyForLargeFiles,
                icon = KixyuSymbols.Wifi,
            )
        }
    }
    val syncContentSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
            KixyuSection(title = "同步内容") {
                KixyuSettingsRow(
                    title = "原始书籍文件",
                    supportingText = "同步 TXT / EPUB，供其他设备完整恢复",
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
                    title = "用户字体",
                    supportingText = "同步已导入的 TTF / OTF",
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
                "书库、进度、书签、统计和阅读设置始终同步",
                modifier = Modifier.padding(horizontal = KixyuSpacing.extraSmall),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    KixyuPageScaffold(
        title = "Google Drive 同步",
        largeTitle = false,
        showTopBar = !embedded,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            if (!embedded) {
                KixyuIconButton(onClick = onBack) {
                    Icon(KixyuSymbols.ArrowBack, "返回")
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

    KixyuSection(title = "云空间") {
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
                            quota == null && state.refreshing -> "正在获取 Google 云空间"
                            quota == null -> state.errorMessage ?: "Google 云空间"
                            limitText == null -> "已使用 $usedText"
                            else -> "已使用 $usedText，共 $limitText"
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
                            warning -> "仅剩 $remainingText，空间不足可能导致同步失败"
                            remainingText != null -> "剩余 $remainingText · Drive、Gmail 和 Google Photos 共用"
                            quota != null -> "账号未提供空间上限 · Drive、Gmail 和 Google Photos 共用"
                            state.errorMessage != null -> "点击右侧按钮重试"
                            else -> "Drive、Gmail 和 Google Photos 共用此空间"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (warning) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                KixyuIconButton(
                    onClick = onRefresh,
                    enabled = !state.refreshing,
                ) {
                    Icon(KixyuSymbols.Refresh, "刷新云空间")
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
                    text = "其中 Google Drive 占用 $driveUsage，回收站占用 $trashUsage",
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
                        Text("在每台设备继续阅读", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "同步书库、进度、书签和个性化设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                KixyuButton(
                    text = "连接 Google Drive",
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
                    "已连接",
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
                            "立即同步",
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
fun GoogleAccountRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val account = state.cloudSync.account
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val navigationContentPadding = LocalKixyuNavigationContentPadding.current
    // A Custom Tab is hosted by the browser, so this app cannot make that Activity
    // edge-to-edge. Keep every browser-owned system surface on the same color to avoid
    // a visually detached navigation-bar strip.
    val customTabSystemSurfaceColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val customTabColors = remember(
        customTabSystemSurfaceColor,
    ) {
        CustomTabColorSchemeParams.Builder()
            .setToolbarColor(customTabSystemSurfaceColor)
            .setNavigationBarColor(customTabSystemSurfaceColor)
            .setNavigationBarDividerColor(customTabSystemSurfaceColor)
            .build()
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
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

    if (account == null) {
        KixyuPageScaffold(
            title = "管理 Google 账号",
            largeTitle = false,
            modifier = Modifier.fillMaxSize(),
            navigationIcon = {
                KixyuIconButton(onClick = onBack) {
                    Icon(KixyuSymbols.ArrowBack, "返回 Google Drive 同步")
                }
            },
            snackbarHost = {
                KixyuSnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Google 账号未连接",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        GoogleAccountPage(
            account = account,
            snackbar = snackbar,
            navigationContentPadding = navigationContentPadding,
            onBack = onBack,
            onManageGoogleAccount = {
                val uri = Uri.Builder()
                    .scheme("https")
                    .authority("myaccount.google.com")
                    .appendQueryParameter("authuser", account.email)
                    .build()
                runCatching {
                    CustomTabsIntent.Builder()
                        .setDefaultColorSchemeParams(customTabColors)
                        .setShowTitle(false)
                        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                        .setUrlBarHidingEnabled(true)
                        .build()
                        .launchUrl(context, uri)
                }.recoverCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }.onFailure {
                    scope.launch { snackbar.showSnackbar("无法打开 Google 账号页面") }
                }
            },
            onSwitchAccount = { activity?.let(viewModel::switchGoogleAccount) },
            onReconnect = { activity?.let(viewModel::connectGoogle) },
            onDisconnect = {
                viewModel.disconnectGoogle()
                onBack()
            },
            onDeleteCloudData = { confirmDelete = true },
        )
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
}

@Composable
private fun GoogleAccountPage(
    account: SyncAccount,
    snackbar: SnackbarHostState,
    navigationContentPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onManageGoogleAccount: () -> Unit,
    onSwitchAccount: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDeleteCloudData: () -> Unit,
) {
    KixyuPageScaffold(
        title = "管理 Google 账号",
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) {
                Icon(KixyuSymbols.ArrowBack, "返回 Google Drive 同步")
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
            modifier = Modifier
                .kixyuPageContentWidth()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                KixyuSection {
                    KixyuSettingsRow(
                        title = account.displayName,
                        supportingText = account.email,
                        leading = { GoogleAccountAvatar(account) },
                    ) {
                        Text(
                            "已连接",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item {
                KixyuSection(title = "账号授权") {
                    KixyuSettingsRow(
                        title = "管理你的 Google 账号",
                        supportingText = "个人信息、安全、隐私和设备",
                        icon = KixyuSymbols.AccountCircle,
                        onClick = onManageGoogleAccount,
                    ) {
                        Icon(
                            KixyuSymbols.OpenInNew,
                            null,
                            Modifier.size(KixyuSize.icon),
                        )
                    }
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = "切换 Google 账号",
                        supportingText = "选择这台设备上的其他 Google 账号",
                        icon = KixyuSymbols.SwitchAccount,
                        onClick = onSwitchAccount,
                    ) {
                        Icon(
                            KixyuSymbols.KeyboardArrowRight,
                            null,
                            Modifier.size(KixyuSize.icon),
                        )
                    }
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = "重新授权 Google Drive",
                        supportingText = "重新确认账号和应用访问权限",
                        icon = KixyuSymbols.Refresh,
                        onClick = onReconnect,
                    )
                }
            }
            item {
                KixyuSection(title = "账号操作") {
                    KixyuSettingsRow(
                        title = "断开 Google 账号",
                        supportingText = "停止同步并撤销 Kixyu Book 的访问权限",
                        icon = KixyuSymbols.Cloud,
                        contentColor = MaterialTheme.colorScheme.error,
                        onClick = onDisconnect,
                    )
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = "删除云端同步数据",
                        supportingText = "不会删除本机书籍和阅读数据",
                        icon = KixyuSymbols.DeleteOutline,
                        contentColor = MaterialTheme.colorScheme.error,
                        onClick = onDeleteCloudData,
                    )
                }
            }
            item { KixyuBottomContentSpacer() }
        }
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

@Composable
private fun cloudSyncStatus(state: CloudSyncState): CloudSyncStatusUi {
    val locale = LocalLocale.current.platformLocale
    val conflict = state.initialSyncDecision
    val lastSync = state.lastSyncTime.takeIf { it > 0 }?.let {
        SimpleDateFormat("MM-dd HH:mm", locale).format(Date(it))
    }
    return when {
        conflict != null -> CloudSyncStatusUi(
            title = "等待处理同步冲突",
            detail = "${conflict.conflicts.size} 项内容在本机和云端都已修改",
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ATTENTION,
        )
        state.inspectingInitialSync -> CloudSyncStatusUi(
            title = "正在检查云端书库",
            detail = "正在识别本机与云端数据",
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
            busy = true,
        )
        !state.enabled -> CloudSyncStatusUi(
            title = "同步已暂停",
            detail = lastSync?.let { "上次同步于 $it" } ?: "开启自动同步后开始上传数据",
            icon = KixyuSymbols.Cloud,
            tone = CloudSyncStatusTone.MUTED,
        )
        state.phase == CloudSyncPhase.AUTHORIZING -> CloudSyncStatusUi(
            title = "正在连接 Google Drive",
            detail = "正在确认账号与访问权限",
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
            busy = true,
        )
        state.phase == CloudSyncPhase.AUTH_REQUIRED -> CloudSyncStatusUi(
            title = "需要重新授权",
            detail = "进入应用后会自动恢复 Google Drive 连接",
            icon = KixyuSymbols.Cloud,
            tone = CloudSyncStatusTone.ATTENTION,
        )
        state.phase == CloudSyncPhase.SYNCING -> CloudSyncStatusUi(
            title = "正在同步",
            detail = if (state.pendingCount > 0) "${state.pendingCount} 项本地变更等待完成" else "正在检查云端变更",
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
            busy = true,
        )
        state.phase == CloudSyncPhase.ERROR -> CloudSyncStatusUi(
            title = "同步遇到问题",
            detail = state.errorMessage ?: "请检查网络后重试",
            icon = KixyuSymbols.Cloud,
            tone = CloudSyncStatusTone.ERROR,
        )
        state.pendingCount > 0 -> CloudSyncStatusUi(
            title = "等待同步",
            detail = "${state.pendingCount} 项本地变更等待上传",
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ATTENTION,
        )
        lastSync != null -> CloudSyncStatusUi(
            title = "所有数据均已同步",
            detail = "上次同步于 $lastSync",
            icon = KixyuSymbols.CloudDone,
            tone = CloudSyncStatusTone.SUCCESS,
        )
        else -> CloudSyncStatusUi(
            title = "等待首次同步",
            detail = "连接网络后将自动开始",
            icon = KixyuSymbols.CloudSync,
            tone = CloudSyncStatusTone.ACTIVE,
        )
    }
}
