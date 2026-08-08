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

@Composable
fun ReadingSettingsRoute(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val requestNotificationPermission = rememberNotificationPermissionAction()
    val updateReminderEnabled: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setReadingReminderEnabled(false)
        } else {
            requestNotificationPermission(true) {
                viewModel.setReadingReminderEnabled(true)
            }
        }
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFont(it.toString()) }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    KixyuPageScaffold(
        title = "阅读",
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
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = "每日目标提醒",
                        supportingText = if (state.readingReminder.enabled) {
                            "目标未完成时，在 ${state.readingReminder.hour.toString().padStart(2, '0')}:" +
                                state.readingReminder.minute.toString().padStart(2, '0') + " 提醒"
                        } else {
                            "关闭 · 仅在主动开启后发送"
                        },
                        onClick = { updateReminderEnabled(!state.readingReminder.enabled) },
                    ) {
                        KixyuSwitch(
                            checked = state.readingReminder.enabled,
                            onCheckedChange = updateReminderEnabled,
                        )
                    }
                    if (state.readingReminder.enabled) {
                        KixyuDivider()
                        KixyuSettingsRow(
                            title = "提醒时间",
                            supportingText = "系统可能根据省电策略延后少量时间",
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute -> viewModel.setReadingReminderTime(hour, minute) },
                                    state.readingReminder.hour,
                                    state.readingReminder.minute,
                                    true,
                                ).show()
                            },
                        ) {
                            Text(
                                "%02d:%02d".format(
                                    Locale.US,
                                    state.readingReminder.hour,
                                    state.readingReminder.minute,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            item { KixyuBottomContentSpacer() }
        }
    }
}
