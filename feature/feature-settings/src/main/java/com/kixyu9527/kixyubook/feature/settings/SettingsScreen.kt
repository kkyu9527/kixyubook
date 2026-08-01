package com.kixyu9527.kixyubook.feature.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppColorControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppUiStyleControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuEdgeToEdgeDialogProperties
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuFontControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderBehaviorControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderLayoutControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderThemeControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSwitch
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationContentPadding
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.displayName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess
import com.kixyu9527.kixyubook.core.sync.CloudSyncPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onCloudSync: () -> Unit,
    onAppearance: () -> Unit,
    onReadingSettings: () -> Unit,
    updateState: AppUpdateState,
    currentVersion: String,
    onCheckForUpdates: () -> Unit,
    onUpdateResultConsumed: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    val backupCreator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportBackup(it.toString()) }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingRestore = it.toString() }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.restoreCompleted.collect { restored = true } }
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
    val navigationContentPadding = LocalKixyuNavigationContentPadding.current
    val syncAccount = state.cloudSync.account

    KixyuPageScaffold(
        title = "设置",
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = navigationContentPadding + KixyuSpacing.small)
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
                KixyuSection {
                    KixyuSettingsRow(
                        title = "Google 同步",
                        supportingText = when {
                            syncAccount == null -> "登录后在设备间增量同步"
                            state.cloudSync.phase == CloudSyncPhase.SYNCING -> "正在同步 · ${syncAccount.email}"
                            state.cloudSync.pendingCount > 0 -> "${state.cloudSync.pendingCount} 项等待同步 · ${syncAccount.email}"
                            else -> syncAccount.email
                        },
                        icon = if (syncAccount == null) Icons.Outlined.Cloud else Icons.Outlined.CloudDone,
                        onClick = onCloudSync,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon))
                    }
                }
            }
            item {
                KixyuSection {
                    KixyuSettingsRow(
                        title = "外观",
                        supportingText = buildString {
                            append(state.settings.theme.displayName())
                            append(" · ")
                            append(state.settings.appUiStyle.displayName())
                            if (state.settings.customThemeEnabled) append(" · 自定义配色")
                            append(" · ")
                            append(state.fonts.firstOrNull { it.uuid == state.settings.fontUuid }?.name ?: "系统字体")
                        },
                        icon = Icons.Outlined.Palette,
                        onClick = onAppearance,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon))
                    }
                }
            }
            item {
                KixyuSection(title = "阅读") {
                    KixyuSettingsRow(
                        title = "每日目标",
                        supportingText = "用于首页阅读进度统计",
                        icon = Icons.Outlined.Flag,
                    ) {
                        Text("${state.goalMinutes} 分钟", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    Slider(
                        value = state.goalMinutes.toFloat(),
                        onValueChange = { viewModel.setGoal(it.toInt()) },
                        valueRange = 5f..120f,
                        steps = 22,
                        modifier = Modifier.padding(horizontal = KixyuSpacing.rowHorizontal),
                    )
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = "阅读设置",
                        supportingText = "页面外观、翻页与阅读行为",
                        icon = Icons.Outlined.Tune,
                        onClick = onReadingSettings,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon))
                    }
                }
            }
            item {
                KixyuSection(title = "备份") {
                    Row(
                        Modifier.fillMaxWidth().padding(KixyuSpacing.rowHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    ) {
                        Button(
                            onClick = { backupCreator.launch("KixyuBook-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.kixyubackup") },
                            enabled = state.backupOperation == null,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.backupOperation == BackupOperation.EXPORT) {
                                CircularProgressIndicator(
                                    Modifier.size(KixyuSize.iconSmall),
                                    color = LocalContentColor.current,
                                    strokeWidth = KixyuSpacing.hairline,
                                )
                            } else {
                                Icon(Icons.Outlined.SaveAlt, null, Modifier.size(KixyuSize.iconSmall))
                            }
                            Spacer(Modifier.width(KixyuSize.compactButtonIconGap))
                            Text("导出", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { backupPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                            enabled = state.backupOperation == null,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.backupOperation == BackupOperation.RESTORE) {
                                CircularProgressIndicator(
                                    Modifier.size(KixyuSize.iconSmall),
                                    color = LocalContentColor.current,
                                    strokeWidth = KixyuSpacing.hairline,
                                )
                            } else {
                                Icon(Icons.Outlined.Restore, null, Modifier.size(KixyuSize.iconSmall))
                            }
                            Spacer(Modifier.width(KixyuSize.compactButtonIconGap))
                            Text("恢复", maxLines = 1)
                        }
                    }
                }
            }
            item {
                KixyuSection(title = "关于") {
                    KixyuSettingsRow(
                        title = "检查更新",
                        supportingText = when (val result = updateState) {
                            AppUpdateState.Checking -> "正在连接 GitHub…"
                            is AppUpdateState.Available -> "发现新版本 ${result.update.versionName}"
                            else -> "当前版本 $currentVersion"
                        },
                        icon = Icons.Outlined.SystemUpdate,
                        onClick = onCheckForUpdates.takeUnless { updateState == AppUpdateState.Checking },
                    ) {
                        if (updateState == AppUpdateState.Checking) {
                            CircularProgressIndicator(
                                Modifier.size(KixyuSize.iconSmall),
                                strokeWidth = KixyuSpacing.hairline,
                            )
                        } else {
                            Text("v$currentVersion", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(navigationContentPadding)) }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            properties = KixyuEdgeToEdgeDialogProperties,
            title = { Text("恢复完整备份？", maxLines = 1) },
            text = { Text("当前书库和设置将被备份内容替换，完成后需要重新启动应用。") },
            confirmButton = { TextButton({ pendingRestore = null; viewModel.restoreBackup(uri) }) { Text("开始恢复") } },
            dismissButton = { TextButton({ pendingRestore = null }) { Text("取消") } },
        )
    }
    if (restored) {
        AlertDialog(
            onDismissRequest = {},
            properties = KixyuEdgeToEdgeDialogProperties,
            title = { Text("恢复完成", maxLines = 1) },
            text = { Text("请关闭后重新打开应用，以加载恢复后的书库。") },
            confirmButton = { Button({ (context as? Activity)?.finishAffinity(); exitProcess(0) }) { Text("关闭应用") } },
        )
    }
}

@Composable
fun CloudSyncRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbar = remember { SnackbarHostState() }
    val syncAccount = state.cloudSync.account
    var confirmDelete by remember { mutableStateOf(false) }
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
    LaunchedEffect(activity, syncAccount) {
        if (activity != null && syncAccount == null) viewModel.connectGoogle(activity)
    }

    KixyuPageScaffold(
        title = "Google 同步",
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
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
            Modifier.kixyuPageContentWidth().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(horizontal = KixyuSpacing.screenHorizontal, vertical = KixyuSpacing.screenVertical),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                KixyuSection(title = "账号") {
                    if (syncAccount == null) {
                        KixyuSettingsRow(
                            title = "使用 Google 账号登录",
                            supportingText = "自动使用设备账号，也可点击选择或重新授权",
                            icon = Icons.Outlined.Cloud,
                            onClick = { activity?.let(viewModel::connectGoogle) },
                        ) {
                            Text("登录", color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        }
                    } else {
                        KixyuSettingsRow(
                            title = syncAccount.displayName,
                            supportingText = syncAccount.email,
                            icon = Icons.Outlined.CloudDone,
                        ) {
                            Text(
                                when (state.cloudSync.phase) {
                                    CloudSyncPhase.SYNCING -> "同步中"
                                    CloudSyncPhase.AUTH_REQUIRED -> "需授权"
                                    CloudSyncPhase.ERROR -> "有错误"
                                    else -> "已连接"
                                },
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                        KixyuDivider()
                        KixyuSettingsRow(
                            title = "自动同步",
                            supportingText = "本地修改会进入队列，联网后自动同步",
                            onClick = { viewModel.setCloudSyncEnabled(!state.cloudSync.enabled) },
                        ) {
                            KixyuSwitch(state.cloudSync.enabled, viewModel::setCloudSyncEnabled)
                        }
                        KixyuDivider()
                        KixyuSettingsRow(
                            title = "立即同步",
                            supportingText = syncStatusText(state.cloudSync.lastSyncTime, state.cloudSync.pendingCount, state.cloudSync.errorMessage),
                            icon = Icons.Outlined.CloudSync,
                            onClick = viewModel::syncNow,
                        )
                    }
                }
            }
            if (syncAccount != null) {
                item {
                    KixyuSection(title = "同步内容") {
                        KixyuSettingsRow(
                            title = "小说原始文件",
                            supportingText = "TXT / EPUB，用于在另一台设备完整恢复",
                            onClick = { viewModel.setSyncOriginalFiles(!state.cloudSync.syncOriginalFiles) },
                        ) { KixyuSwitch(state.cloudSync.syncOriginalFiles, viewModel::setSyncOriginalFiles) }
                        KixyuDivider()
                        KixyuSettingsRow(
                            title = "用户字体",
                            supportingText = "同步已导入的 TTF / OTF",
                            onClick = { viewModel.setSyncFonts(!state.cloudSync.syncFonts) },
                        ) { KixyuSwitch(state.cloudSync.syncFonts, viewModel::setSyncFonts) }
                        KixyuDivider()
                        KixyuSettingsRow(
                            title = "大文件仅使用 Wi-Fi",
                            supportingText = "进度、书签和设置仍可使用移动网络同步",
                            onClick = { viewModel.setWifiOnlyForLargeFiles(!state.cloudSync.wifiOnlyForLargeFiles) },
                        ) { KixyuSwitch(state.cloudSync.wifiOnlyForLargeFiles, viewModel::setWifiOnlyForLargeFiles) }
                    }
                }
                item {
                    KixyuSection(title = "账号操作") {
                        KixyuSettingsRow(
                            title = "退出 Google 账号",
                            supportingText = "停止同步，但保留本机书籍和数据",
                            onClick = viewModel::disconnectGoogle,
                        )
                        KixyuDivider()
                        KixyuSettingsRow(
                            title = "删除云端同步数据",
                            supportingText = "永久删除应用专属 Drive 空间中的数据",
                            icon = Icons.Outlined.DeleteForever,
                            onClick = { confirmDelete = true },
                        )
                    }
                }
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
}

private fun syncStatusText(lastSyncTime: Long, pendingCount: Int, error: String?): String = when {
    error != null -> error
    pendingCount > 0 -> "$pendingCount 项等待同步"
    lastSyncTime > 0 -> "上次同步：${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(lastSyncTime))}"
    else -> "尚未完成首次同步"
}

@Composable
fun ReadingSettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    KixyuPageScaffold(
        title = "阅读设置",
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
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
                KixyuSection(title = "页面外观") {
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
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFont(it.toString()) }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    KixyuPageScaffold(
        title = "外观",
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
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
            item {
                KixyuSection(title = "界面") {
                    KixyuAppUiStyleControl(
                        settings = state.settings,
                        onSettingsChange = { updated -> viewModel.update { updated } },
                    )
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = "组件预览",
                        supportingText = when (state.settings.appUiStyle) {
                            AppUiStyle.MATERIAL -> "Material 3 标准组件与动态色"
                            AppUiStyle.MIUIX -> "MIUIX 圆润列表、下拉框与开关"
                        },
                    ) {
                        KixyuSwitch(checked = true, onCheckedChange = null)
                    }
                    KixyuDivider()
                    KixyuAppColorControl(
                        settings = state.settings,
                        onSettingsChange = { updated -> viewModel.update { updated } },
                    )
                }
            }
            item {
                KixyuSection(title = "阅读主题") {
                    KixyuReaderThemeControls(
                        settings = state.settings,
                        onSettingsChange = { updated -> viewModel.update { updated } },
                    )
                }
            }
            item {
                KixyuSection(title = "字体") {
                    KixyuFontControls(
                        fonts = state.fonts,
                        selectedFontUuid = state.settings.fontUuid,
                        onSelectFont = { uuid -> viewModel.update { it.copy(fontUuid = uuid) } },
                        onAddFont = {
                            fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
                        },
                        onDeleteFont = viewModel::deleteFont,
                    )
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}
