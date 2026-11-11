package com.kixyu9527.kixyubook.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuOperationHost
import com.kixyu9527.kixyubook.core.ui.rememberNotificationPermissionAction

/**
 * Long-term reading habits get their own page instead of living inline on the settings overview.
 * The overview only carries the goal/reminder summary and a stable place to enter this page.
 */
@Composable
fun ReadingHabitsRoute(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: ReadingHabitsViewModel = hiltViewModel(),
) = KixyuOperationHost(viewModel.operations) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionAction()
    KixyuPageScaffold(
        title = stringResource(R.string.settings_reading_habits_section),
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
    ) { innerPadding ->
        LazyColumn(
            Modifier.kixyuPageContentWidth()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
        ) {
            item {
                ReadingHabitsSection(
                    state = state,
                    onSetGoal = viewModel::setGoal,
                    onRequestReminderEnabled = { enabled ->
                        requestNotificationPermission(enabled) {
                            viewModel.setEnabled(enabled)
                        }
                    },
                    onSetReminderTime = viewModel::setTime,
                )
            }
            item { KixyuBottomContentSpacer() }
        }
    }
}
