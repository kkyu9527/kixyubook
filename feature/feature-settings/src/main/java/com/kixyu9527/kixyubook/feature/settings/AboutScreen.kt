package com.kixyu9527.kixyubook.feature.settings

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
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
            item { KixyuBottomContentSpacer() }
        }
    }
}
