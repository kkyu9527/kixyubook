package com.kixyu9527.kixyubook.feature.settings

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderBrightnessControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuFontControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderBehaviorControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderLayoutControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuReaderThemeControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuStepperRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSwitch
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ReadingSettingsRoute(
    onBack: () -> Unit,
    onManageFonts: () -> Unit,
    onReadingInformation: () -> Unit,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    var resetAllVisible by remember { mutableStateOf(false) }
    var reminderTimeVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    KixyuPageScaffold(
        title = "阅读",
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
            item {
                KixyuSection(
                    title = "阅读外观",
                    action = { ReadingSectionResetAction(viewModel::resetReaderTheme) },
                ) {
                    KixyuReaderThemeControls(
                        settings = state.settings,
                        onSettingsChange = { updated -> viewModel.update { updated } },
                    )
                    KixyuDivider()
                    KixyuReaderBrightnessControls(
                        settings = state.settings,
                        onSettingsChange = { updated -> viewModel.update { updated } },
                    )
                }
            }
            item {
                KixyuSection(
                    title = "排版与翻页",
                    action = { ReadingSectionResetAction(viewModel::resetReaderLayout) },
                ) {
                    KixyuFontControls(
                        fonts = state.fonts,
                        selectedFontUuid = state.settings.fontUuid,
                        onSelectFont = { uuid -> viewModel.update { it.copy(fontUuid = uuid) } },
                        onAddFont = {},
                        onDeleteFont = viewModel::deleteFont,
                        onManageFonts = onManageFonts,
                    )
                    KixyuDivider()
                    KixyuReaderLayoutControls(state.settings) { updated ->
                        viewModel.update { updated }
                    }
                }
            }
            item {
                KixyuSection(
                    title = "阅读控制",
                    action = { ReadingSectionResetAction(viewModel::resetReaderBehavior) },
                ) {
                    KixyuReaderBehaviorControls(state.settings) { updated ->
                        viewModel.update { updated }
                    }
                }
            }
            item {
                KixyuSection(title = "阅读信息") {
                    KixyuSettingsRow(
                        title = "阅读信息栏",
                        supportingText = listOfNotNull(
                            "章节名".takeIf { state.settings.showChapterTitle },
                            "页码".takeIf { state.settings.showPageNumber },
                            "时间".takeIf { state.settings.showReadingTime },
                            "电量".takeIf { state.settings.showBatteryLevel },
                        ).joinToString("、").ifBlank { "均不显示" },
                        onClick = onReadingInformation,
                    ) {
                        Icon(KixyuSymbols.KeyboardArrowRight, null)
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
                            onClick = { reminderTimeVisible = true },
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
            item {
                KixyuSection(title = "重置") {
                    KixyuSettingsRow(
                        title = "恢复全部阅读设置",
                        supportingText = "不会修改应用外观、书籍内容和阅读记录",
                        icon = KixyuSymbols.Refresh,
                        onClick = { resetAllVisible = true },
                    ) {
                        Icon(KixyuSymbols.KeyboardArrowRight, null)
                    }
                }
            }
            item { KixyuBottomContentSpacer() }
        }
    }
    KixyuActionDialog(
        show = resetAllVisible,
        onDismissRequest = { resetAllVisible = false },
        title = "恢复全部阅读设置",
        confirmLabel = "恢复默认",
        onConfirm = {
            resetAllVisible = false
            viewModel.resetAllReaderSettings()
        },
        dismissLabel = "取消",
    ) {
        Text("将重置全局排版、阅读外观、阅读控制、信息栏和阅读目标。")
    }
    ReadingReminderTimeDialog(
        show = reminderTimeVisible,
        initialHour = state.readingReminder.hour,
        initialMinute = state.readingReminder.minute,
        onDismissRequest = { reminderTimeVisible = false },
        onConfirm = { hour, minute ->
            reminderTimeVisible = false
            viewModel.setReadingReminderTime(hour, minute)
        },
    )
}

@Composable
private fun ReadingReminderTimeDialog(
    show: Boolean,
    initialHour: Int,
    initialMinute: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    if (!show) return
    var hour by rememberSaveable(initialHour) { mutableStateOf(initialHour) }
    var minute by rememberSaveable(initialMinute) { mutableStateOf(initialMinute) }
    KixyuActionDialog(
        show = true,
        title = "提醒时间",
        onDismissRequest = onDismissRequest,
        confirmLabel = "确定",
        onConfirm = { onConfirm(hour, minute) },
        dismissLabel = "取消",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium)) {
            Text(
                "%02d:%02d".format(Locale.US, hour, minute),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            KixyuStepperRow(
                title = "小时",
                valueLabel = hour.toString().padStart(2, '0'),
                onDecrease = { hour = (hour + 23) % 24 },
                onIncrease = { hour = (hour + 1) % 24 },
            )
            KixyuDivider()
            KixyuStepperRow(
                title = "分钟",
                valueLabel = minute.toString().padStart(2, '0'),
                onDecrease = { minute = (minute + 59) % 60 },
                onIncrease = { minute = (minute + 1) % 60 },
            )
        }
    }
}

@Composable
private fun ReadingSectionResetAction(onClick: () -> Unit) {
    KixyuTextButton(text = "重置", onClick = onClick)
}
