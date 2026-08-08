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
import androidx.core.content.ContextCompat
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppColorControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppUiStyleControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
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
import com.kixyu9527.kixyubook.core.sync.BackupOperationType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataAndBackupRoute(
    onBack: () -> Unit,
    onOpenDiagnosticLog: () -> Unit,
    embedded: Boolean = false,
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
    ) { uri -> uri?.let {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModel.exportBackup(it.toString())
    } }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingRestore = it.toString()
        }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.restoreCompleted.collect { restored = true } }

    KixyuPageScaffold(
        title = "数据与备份",
        largeTitle = false,
        showTopBar = !embedded,
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
                            text = if (state.backupOperation == BackupOperationType.EXPORT) "正在导出…" else "导出",
                            onClick = {
                                requestNotificationPermission(false) {
                                    backupCreator.launch(
                                        "KixyuBook-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.kixyubackup",
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = state.backupOperation == null,
                        )
                        KixyuButton(
                            text = if (state.backupOperation == BackupOperationType.RESTORE) "正在恢复…" else "恢复",
                            onClick = {
                                requestNotificationPermission(false) {
                                    backupPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = state.backupOperation == null,
                        )
                    }
                }
            }
            item {
                KixyuSection(title = "诊断") {
                    KixyuSettingsRow(
                        title = "日志详情",
                        supportingText = "查看同步、导入、解析与阅读性能记录",
                        onClick = onOpenDiagnosticLog,
                        leading = { Icon(Icons.Outlined.Storage, null, Modifier.size(KixyuSize.icon)) },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon))
                    }
                }
            }
            item { KixyuBottomContentSpacer() }
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
