package com.kixyu9527.kixyubook.feature.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppColorControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppUiStyleControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDropdownRow
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
import com.kixyu9527.kixyubook.core.sync.InitialSyncChoice
import kotlinx.coroutines.launch

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
    val navigationContentPadding = LocalKixyuNavigationContentPadding.current
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
                onClick = { openPane(SettingsPane.READING, onReadingSettings) },
            ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
            KixyuDivider()
            KixyuSettingsRow(
                title = "外观",
                supportingText = "${state.settings.theme.displayName()} · ${state.settings.appUiStyle.displayName()} · ${state.settings.appColorTheme.displayName()}",
                icon = Icons.Outlined.Palette,
                onClick = { openPane(SettingsPane.APPEARANCE, onAppearance) },
            ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }
    val dataSection: @Composable () -> Unit = {
        KixyuSection(title = "数据") {
            KixyuSettingsRow(
                title = "数据与备份",
                supportingText = "导出或恢复书库、进度与个人设置",
                icon = Icons.Outlined.Backup,
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
                    item { Spacer(Modifier.height(navigationContentPadding)) }
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
                item { Spacer(Modifier.height(navigationContentPadding)) }
                item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
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
    val syncAccount = state.cloudSync.account
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var conflictDeferred by rememberSaveable { mutableStateOf(false) }
    var autoAuthorizationAttempted by rememberSaveable { mutableStateOf(false) }
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
    LaunchedEffect(activity, syncAccount, state.cloudSync.phase) {
        if (
            activity != null &&
            !autoAuthorizationAttempted &&
            (syncAccount == null || state.cloudSync.phase == CloudSyncPhase.AUTH_REQUIRED)
        ) {
            autoAuthorizationAttempted = true
            viewModel.connectGoogle(activity)
        }
    }
    LaunchedEffect(state.cloudSync.initialSyncDecision) {
        if (state.cloudSync.initialSyncDecision != null) conflictDeferred = false
    }

    val accountSection: @Composable () -> Unit = {
        KixyuSection(title = "账号") {
            if (syncAccount == null) {
                KixyuSettingsRow(
                    title = "使用 Google 账号登录",
                    supportingText = "登录后在设备间同步书籍、进度与设置",
                    icon = Icons.Outlined.Cloud,
                )
                KixyuDivider()
                Row(Modifier.fillMaxWidth().padding(KixyuSpacing.rowHorizontal)) {
                    KixyuButton(
                        text = "选择 Google 账号",
                        onClick = { activity?.let(viewModel::connectGoogle) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = activity != null,
                    )
                }
            } else {
                KixyuSettingsRow(
                    title = syncAccount.displayName,
                    supportingText = syncAccount.email,
                    icon = Icons.Outlined.CloudDone,
                ) {
                    Text(
                        when {
                            state.cloudSync.initialSyncDecision != null -> "待处理"
                            state.cloudSync.inspectingInitialSync -> "检查中"
                            state.cloudSync.enabled -> "已连接"
                            else -> "已暂停"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                KixyuDivider()
                KixyuSettingsRow(
                    title = "自动同步",
                    supportingText = "书库发生变化后在后台增量同步",
                    onClick = {
                        if (state.cloudSync.initialSyncDecision != null) {
                            conflictDeferred = false
                        } else {
                            viewModel.setCloudSyncEnabled(!state.cloudSync.enabled)
                        }
                    },
                ) {
                    KixyuSwitch(
                        checked = state.cloudSync.enabled,
                        onCheckedChange = viewModel::setCloudSyncEnabled,
                        enabled = state.cloudSync.initialSyncDecision == null &&
                            !state.cloudSync.inspectingInitialSync,
                    )
                }
            }
        }
    }
    val statusSection: @Composable () -> Unit = {
        val status = cloudSyncStatus(state.cloudSync)
        KixyuSection(title = "同步状态") {
            KixyuSettingsRow(
                title = status.title,
                supportingText = status.detail,
                icon = status.icon,
            )
            KixyuDivider()
            Row(Modifier.fillMaxWidth().padding(KixyuSpacing.rowHorizontal)) {
                KixyuButton(
                    text = when {
                        state.cloudSync.initialSyncDecision != null -> "处理同步冲突"
                        state.cloudSync.inspectingInitialSync -> "正在检查云端…"
                        state.cloudSync.phase == CloudSyncPhase.AUTHORIZING -> "正在授权…"
                        state.cloudSync.phase == CloudSyncPhase.AUTH_REQUIRED -> "重新授权"
                        state.cloudSync.phase == CloudSyncPhase.SYNCING -> "正在同步…"
                        !state.cloudSync.enabled -> "同步已暂停"
                        else -> "立即同步"
                    },
                    onClick = {
                        if (state.cloudSync.initialSyncDecision != null) {
                            conflictDeferred = false
                        } else if (state.cloudSync.phase == CloudSyncPhase.AUTH_REQUIRED) {
                            activity?.let(viewModel::connectGoogle)
                        } else {
                            viewModel.syncNow()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.cloudSync.inspectingInitialSync &&
                        state.cloudSync.phase !in setOf(
                            CloudSyncPhase.AUTHORIZING,
                            CloudSyncPhase.SYNCING,
                        ) && (
                            state.cloudSync.enabled ||
                                state.cloudSync.phase == CloudSyncPhase.AUTH_REQUIRED ||
                                state.cloudSync.initialSyncDecision != null
                            ),
                )
            }
        }
    }
    val contentSection: @Composable () -> Unit = {
        KixyuSection(title = "同步内容") {
            KixyuSettingsRow(
                title = "书籍与阅读数据",
                supportingText = "书籍信息、进度、统计、书签和设置",
                icon = Icons.Outlined.CheckCircle,
            ) {
                Text(
                    "始终同步",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            KixyuDivider()
            KixyuSettingsRow(
                title = "原始书籍文件",
                supportingText = "同步 TXT / EPUB，供其他设备完整恢复",
                onClick = { viewModel.setSyncOriginalFiles(!state.cloudSync.syncOriginalFiles) },
            ) { KixyuSwitch(state.cloudSync.syncOriginalFiles, viewModel::setSyncOriginalFiles) }
            KixyuDivider()
            KixyuSettingsRow(
                title = "用户字体",
                supportingText = "同步已导入的 TTF / OTF",
                onClick = { viewModel.setSyncFonts(!state.cloudSync.syncFonts) },
            ) { KixyuSwitch(state.cloudSync.syncFonts, viewModel::setSyncFonts) }
        }
    }
    val networkSection: @Composable () -> Unit = {
        KixyuSection(title = "网络") {
            KixyuDropdownRow(
                title = "大文件同步网络",
                selected = if (state.cloudSync.wifiOnlyForLargeFiles) {
                    LargeFileNetwork.WIFI_ONLY
                } else {
                    LargeFileNetwork.ANY_NETWORK
                },
                options = LargeFileNetwork.entries,
                optionLabel = LargeFileNetwork::label,
                icon = Icons.Outlined.Wifi,
                onSelected = { option ->
                    viewModel.setWifiOnlyForLargeFiles(option == LargeFileNetwork.WIFI_ONLY)
                },
            )
            KixyuDivider()
            KixyuSettingsRow(
                title = "Google Drive 应用专属空间",
                supportingText = "增量同步，仅 Kixyu Book 可以访问",
                icon = Icons.Outlined.Storage,
            )
        }
    }
    val accountActionsSection: @Composable () -> Unit = {
        KixyuSection(title = "账号操作") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.rowHorizontal),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            ) {
                KixyuTextButton(
                    text = "退出账号",
                    onClick = viewModel::disconnectGoogle,
                    modifier = Modifier.weight(1f),
                )
                KixyuTextButton(
                    text = "删除云端数据",
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    KixyuPageScaffold(
        title = "Google Drive 同步",
        largeTitle = false,
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
                    .padding(bottom = KixyuSpacing.medium)
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
            item { accountSection() }
            if (syncAccount != null) {
                item { statusSection() }
                item { contentSection() }
                item { networkSection() }
                item { accountActionsSection() }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
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
        dismissLabel = "稍后处理",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
            Text("有 ${syncConflict?.conflicts?.size ?: 0} 项内容在本机和云端都发生了修改。")
            Text(
                "阅读进度、阅读记录和删除操作会自动合并；这里只列出无法安全判断的书籍信息、书签或阅读设置。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KixyuSecondaryButton(
                text = "使用云端更改",
                onClick = {
                    viewModel.resolveInitialSync(InitialSyncChoice.USE_CLOUD_CHANGES)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.cloudSync.inspectingInitialSync,
            )
        }
    }

}

private data class CloudSyncStatusUi(
    val title: String,
    val detail: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private enum class LargeFileNetwork {
    WIFI_ONLY,
    ANY_NETWORK,
}

private fun LargeFileNetwork.label(): String = when (this) {
    LargeFileNetwork.WIFI_ONLY -> "仅 Wi-Fi"
    LargeFileNetwork.ANY_NETWORK -> "Wi-Fi 和移动数据"
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
        )
        state.inspectingInitialSync -> CloudSyncStatusUi(
            title = "正在检查云端书库",
            detail = "正在识别本机与云端数据",
            icon = Icons.Outlined.CloudSync,
        )
        !state.enabled -> CloudSyncStatusUi(
            title = "同步已暂停",
            detail = lastSync?.let { "上次同步于 $it" } ?: "开启自动同步后开始上传数据",
            icon = Icons.Outlined.Cloud,
        )
        state.phase == CloudSyncPhase.AUTHORIZING -> CloudSyncStatusUi(
            title = "正在连接 Google Drive",
            detail = "正在确认账号与访问权限",
            icon = Icons.Outlined.CloudSync,
        )
        state.phase == CloudSyncPhase.AUTH_REQUIRED -> CloudSyncStatusUi(
            title = "需要重新授权",
            detail = state.errorMessage ?: "请重新允许 Kixyu Book 访问应用专属空间",
            icon = Icons.Outlined.Cloud,
        )
        state.phase == CloudSyncPhase.SYNCING -> CloudSyncStatusUi(
            title = "正在同步",
            detail = if (state.pendingCount > 0) "${state.pendingCount} 项本地变更等待完成" else "正在检查云端变更",
            icon = Icons.Outlined.CloudSync,
        )
        state.phase == CloudSyncPhase.ERROR -> CloudSyncStatusUi(
            title = "同步遇到问题",
            detail = state.errorMessage ?: "请检查网络后重试",
            icon = Icons.Outlined.Cloud,
        )
        state.pendingCount > 0 -> CloudSyncStatusUi(
            title = "等待同步",
            detail = "${state.pendingCount} 项本地变更等待上传",
            icon = Icons.Outlined.CloudSync,
        )
        lastSync != null -> CloudSyncStatusUi(
            title = "所有数据均已同步",
            detail = "上次同步于 $lastSync",
            icon = Icons.Outlined.CloudDone,
        )
        else -> CloudSyncStatusUi(
            title = "等待首次同步",
            detail = "连接网络后将自动开始",
            icon = Icons.Outlined.CloudSync,
        )
    }
}

@Composable
fun ReadingSettingsRoute(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFont(it.toString()) }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    KixyuPageScaffold(
        title = "阅读",
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            if (!embedded) {
                KixyuIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                }
            }
        },
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
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
            item {
                KixyuSection(title = "阅读配色") {
                    KixyuReaderThemeControls(
                        settings = state.settings,
                        onSettingsChange = { updated -> viewModel.update { updated } },
                    )
                }
            }
            item {
                KixyuSection(title = "排版与翻页") {
                    KixyuFontControls(
                        fonts = state.fonts,
                        selectedFontUuid = state.settings.fontUuid,
                        onSelectFont = { uuid -> viewModel.update { it.copy(fontUuid = uuid) } },
                        onAddFont = {
                            fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
                        },
                        onDeleteFont = viewModel::deleteFont,
                    )
                    KixyuDivider()
                    KixyuReaderLayoutControls(state.settings) { updated ->
                        viewModel.update { updated }
                    }
                }
            }
            item {
                KixyuSection(title = "阅读行为") {
                    KixyuReaderBehaviorControls(state.settings) { updated ->
                        viewModel.update { updated }
                    }
                }
            }
            item {
                KixyuSection(title = "阅读习惯") {
                    KixyuStepperRow(
                        title = "每日目标",
                        valueLabel = "${state.goalMinutes} 分钟",
                        onDecrease = { viewModel.setGoal((state.goalMinutes - 5).coerceAtLeast(5)) },
                        onIncrease = { viewModel.setGoal((state.goalMinutes + 5).coerceAtMost(120)) },
                        decreaseEnabled = state.goalMinutes > 5,
                        increaseEnabled = state.goalMinutes < 120,
                    )
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceRoute(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    KixyuPageScaffold(
        title = "外观",
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            if (!embedded) {
                KixyuIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                }
            }
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
            item {
                KixyuSection(title = "应用界面") {
                    KixyuThemeModeControl(
                        settings = state.settings,
                        onSettingsChange = { updated -> viewModel.update { updated } },
                    )
                    KixyuDivider()
                    KixyuAppUiStyleControl(
                        settings = state.settings,
                        onSettingsChange = { updated -> viewModel.update { updated } },
                    )
                    KixyuDivider()
                    KixyuAppColorControl(
                        settings = state.settings,
                        onSettingsChange = { updated -> viewModel.update { updated } },
                    )
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
fun DataAndBackupRoute(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    var restored by rememberSaveable { mutableStateOf(false) }
    val backupCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.exportBackup(it.toString()) } }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingRestore = it.toString() }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.restoreCompleted.collect { restored = true } }

    KixyuPageScaffold(
        title = "数据与备份",
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            if (!embedded) {
                KixyuIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                }
            }
        },
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
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
            item {
                KixyuSection(title = "手动备份") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.rowHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    ) {
                        KixyuButton(
                            text = if (state.backupOperation == BackupOperation.EXPORT) "正在导出…" else "导出",
                            onClick = {
                                backupCreator.launch(
                                    "KixyuBook-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.kixyubackup",
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = state.backupOperation == null,
                        )
                        KixyuButton(
                            text = if (state.backupOperation == BackupOperation.RESTORE) "正在恢复…" else "恢复",
                            onClick = {
                                backupPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                            modifier = Modifier.weight(1f),
                            enabled = state.backupOperation == null,
                        )
                    }
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }

    pendingRestore?.let { uri ->
        KixyuActionDialog(
            show = true,
            title = "恢复完整备份？",
            onDismissRequest = { pendingRestore = null },
            confirmLabel = "开始恢复",
            dismissLabel = "取消",
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
fun AboutRoute(
    onBack: () -> Unit,
    updateState: AppUpdateState,
    currentVersion: String,
    onCheckForUpdates: () -> Unit,
    onUpdateResultConsumed: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    onOpenProjectSource: () -> Boolean,
    onContactTelegram: () -> Boolean,
    appLogo: @Composable () -> Unit,
    embedded: Boolean = false,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openExternal: ((() -> Boolean), String) -> Unit = { open, errorMessage ->
        if (!open()) scope.launch { snackbar.showSnackbar(errorMessage) }
    }
    LaunchedEffect(updateState) {
        when (val result = updateState) {
            is AppUpdateState.UpToDate -> {
                snackbar.showSnackbar("当前已是最新版本 ${result.currentVersion}")
                onUpdateResultConsumed()
            }
            is AppUpdateState.Failed -> {
                snackbar.showSnackbar(result.message)
                onUpdateResultConsumed()
            }
            else -> Unit
        }
    }
    KixyuPageScaffold(
        title = "关于",
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            if (!embedded) {
                KixyuIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                }
            }
        },
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
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
            item {
                KixyuSection(title = "应用") {
                    KixyuSettingsRow(
                        title = "Kixyu Book",
                        supportingText = "本地离线小说阅读器",
                        leading = appLogo,
                    ) {
                        Text("v$currentVersion", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    KixyuDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.rowHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    ) {
                        KixyuButton(
                            text = if (updateState == AppUpdateState.Checking) "正在检查…" else "检查更新",
                            onClick = onCheckForUpdates,
                            modifier = Modifier.weight(1f),
                            enabled = updateState != AppUpdateState.Checking,
                        )
                        KixyuSecondaryButton(
                            text = "更新日志",
                            onClick = onShowReleaseNotes,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                KixyuSection(title = "项目与联系") {
                    KixyuSettingsRow(
                        title = "项目源码",
                        supportingText = "github.com/kkyu9527/kixyubook",
                        onClick = {
                            openExternal(onOpenProjectSource, "无法打开 GitHub，请检查可用的浏览器")
                        },
                        leading = {
                            Icon(
                                painterResource(R.drawable.ic_brand_github),
                                null,
                                Modifier.size(KixyuSize.icon),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(KixyuSize.icon))
                    }
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = "Telegram 联系",
                        supportingText = "@kkyu9527s_bot",
                        onClick = {
                            openExternal(onContactTelegram, "无法打开 Telegram 联系链接")
                        },
                        leading = {
                            Icon(
                                painterResource(R.drawable.ic_brand_telegram),
                                null,
                                Modifier.size(KixyuSize.icon),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(KixyuSize.icon))
                    }
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}
