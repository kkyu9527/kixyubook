package com.kixyu9527.kixyubook.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuStepperRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSwitch
import java.util.Locale

/**
 * Reading goal and reminder live on the settings home under "reading habits": they are long-term
 * preferences, not display options, so they stay out of the reading configuration surfaces.
 */
@Composable
internal fun ReadingHabitsSection(
    state: SettingsUiState,
    onSetGoal: (Int) -> Unit,
    onRequestReminderEnabled: (Boolean) -> Unit,
    onSetReminderTime: (Int, Int) -> Unit,
) {
    var reminderTimeVisible by rememberSaveable { mutableStateOf(false) }
    KixyuSection(title = stringResource(R.string.settings_reading_habits_section)) {
        KixyuStepperRow(
            title = stringResource(R.string.settings_daily_goal),
            valueLabel = stringResource(R.string.settings_goal_minutes, state.goalMinutes),
            onDecrease = { onSetGoal((state.goalMinutes - 5).coerceAtLeast(5)) },
            onIncrease = { onSetGoal((state.goalMinutes + 5).coerceAtMost(120)) },
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
                        LocalConfiguration.current.locales[0],
                        state.readingReminder.hour,
                        state.readingReminder.minute,
                    ),
                )
            } else {
                stringResource(R.string.settings_reminder_disabled)
            },
            onClick = { onRequestReminderEnabled(!state.readingReminder.enabled) },
        ) {
            KixyuSwitch(
                checked = state.readingReminder.enabled,
                onCheckedChange = onRequestReminderEnabled,
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
                    "%02d:%02d".format(Locale.US, state.readingReminder.hour, state.readingReminder.minute),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (reminderTimeVisible) {
        var hour by rememberSaveable { mutableIntStateOf(state.readingReminder.hour) }
        var minute by rememberSaveable { mutableIntStateOf(state.readingReminder.minute) }
        KixyuActionDialog(
            show = true,
            title = stringResource(R.string.settings_reminder_time),
            onDismissRequest = { reminderTimeVisible = false },
            confirmLabel = stringResource(R.string.settings_ok),
            onConfirm = {
                reminderTimeVisible = false
                onSetReminderTime(hour, minute)
            },
            dismissLabel = stringResource(R.string.settings_cancel),
        ) {
            Column(Modifier.fillMaxWidth()) {
                KixyuStepperRow(
                    title = stringResource(R.string.settings_hour),
                    valueLabel = "%02d".format(Locale.US, hour),
                    onDecrease = { hour = (hour + 23) % 24 },
                    onIncrease = { hour = (hour + 1) % 24 },
                )
                KixyuDivider()
                KixyuStepperRow(
                    title = stringResource(R.string.settings_minute),
                    valueLabel = "%02d".format(Locale.US, minute),
                    onDecrease = { minute = (minute + 55) % 60 },
                    onIncrease = { minute = (minute + 5) % 60 },
                )
            }
        }
    }
}
