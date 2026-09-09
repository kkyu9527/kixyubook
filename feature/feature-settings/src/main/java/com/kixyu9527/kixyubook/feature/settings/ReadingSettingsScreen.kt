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
import androidx.compose.ui.res.stringResource
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
        title = stringResource(R.string.settings_reading),
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
            item {
                KixyuSection(
                    title = stringResource(R.string.settings_reading_appearance),
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
                    title = stringResource(R.string.settings_layout_and_page_turn),
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
                    title = stringResource(R.string.settings_reading_controls),
                    action = { ReadingSectionResetAction(viewModel::resetReaderBehavior) },
                ) {
                    KixyuReaderBehaviorControls(state.settings) { updated ->
                        viewModel.update { updated }
                    }
                }
            }
            item {
                KixyuSection(title = stringResource(R.string.settings_reading_information_section)) {
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_reading_information_bar),
                        supportingText = listOfNotNull(
                            stringResource(R.string.settings_info_chapter).takeIf { state.settings.showChapterTitle },
                            stringResource(R.string.settings_info_page).takeIf { state.settings.showPageNumber },
                            stringResource(R.string.settings_info_time).takeIf { state.settings.showReadingTime },
                            stringResource(R.string.settings_info_battery).takeIf { state.settings.showBatteryLevel },
                        ).joinToString(stringResource(R.string.settings_info_separator)).ifBlank { stringResource(R.string.settings_info_hidden) },
                        onClick = onReadingInformation,
                    ) {
                        Icon(KixyuSymbols.KeyboardArrowRight, null)
                    }
                }
            }
            item {
                KixyuSection(title = stringResource(R.string.settings_reading_habits_section)) {
                    KixyuStepperRow(
                        title = stringResource(R.string.settings_daily_goal),
                        valueLabel = stringResource(R.string.settings_goal_minutes, state.goalMinutes),
                        onDecrease = { viewModel.setGoal((state.goalMinutes - 5).coerceAtLeast(5)) },
                        onIncrease = { viewModel.setGoal((state.goalMinutes + 5).coerceAtMost(120)) },
                        decreaseEnabled = state.goalMinutes > 5,
                        increaseEnabled = state.goalMinutes < 120,
                    )
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_daily_goal_reminder),
                        supportingText = if (state.readingReminder.enabled) {
                            stringResource(
                                R.string.settings_reminder_enabled,
                                "%02d:%02d".format(
                                    androidx.compose.ui.platform.LocalConfiguration.current.locales[0],
                                    state.readingReminder.hour, state.readingReminder.minute,
                                ),
                            )
                        } else {
                            stringResource(R.string.settings_reminder_disabled)
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
                            title = stringResource(R.string.settings_reminder_time),
                            supportingText = stringResource(R.string.settings_reminder_delay_summary),
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
                KixyuSection(title = stringResource(R.string.settings_reset_section)) {
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_reset_all_reader_settings),
                        supportingText = stringResource(R.string.settings_reset_reader_summary),
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
        title = stringResource(R.string.settings_reset_all_reader_settings),
        confirmLabel = stringResource(R.string.settings_reset_defaults),
        onConfirm = {
            resetAllVisible = false
            viewModel.resetAllReaderSettings()
        },
        dismissLabel = stringResource(R.string.settings_cancel),
    ) {
        Text(stringResource(R.string.settings_reset_reader_warning))
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
        title = stringResource(R.string.settings_reminder_time),
        onDismissRequest = onDismissRequest,
        confirmLabel = stringResource(R.string.settings_ok),
        onConfirm = { onConfirm(hour, minute) },
        dismissLabel = stringResource(R.string.settings_cancel),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium)) {
            Text(
                "%02d:%02d".format(Locale.US, hour, minute),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            KixyuStepperRow(
                title = stringResource(R.string.settings_hour),
                valueLabel = hour.toString().padStart(2, '0'),
                onDecrease = { hour = (hour + 23) % 24 },
                onIncrease = { hour = (hour + 1) % 24 },
            )
            KixyuDivider()
            KixyuStepperRow(
                title = stringResource(R.string.settings_minute),
                valueLabel = minute.toString().padStart(2, '0'),
                onDecrease = { minute = (minute + 59) % 60 },
                onIncrease = { minute = (minute + 1) % 60 },
            )
        }
    }
}

@Composable
private fun ReadingSectionResetAction(onClick: () -> Unit) {
    KixyuTextButton(text = stringResource(R.string.settings_reset), onClick = onClick)
}
