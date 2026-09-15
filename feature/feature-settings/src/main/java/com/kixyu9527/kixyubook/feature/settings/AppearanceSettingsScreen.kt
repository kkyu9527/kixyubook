package com.kixyu9527.kixyubook.feature.settings

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppColorControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAppUiStyleControl
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuGlassEffectControls
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSwitch
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuThemeModeControl
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceRoute(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) = com.kixyu9527.kixyubook.core.designsystem.component.KixyuOperationHost(viewModel.operations) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    KixyuPageScaffold(
        title = stringResource(R.string.settings_appearance),
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
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                KixyuSection(title = stringResource(R.string.settings_app_interface_section)) {
                    AppLanguageControl()
                    KixyuDivider()
                    KixyuThemeModeControl(
                        settings = state.settings,
                        onSettingsChange = viewModel::update,
                    )
                    KixyuDivider()
                    KixyuAppUiStyleControl(
                        settings = state.settings,
                        onSettingsChange = viewModel::update,
                    )
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_predictive_back),
                        supportingText = if (state.settings.predictiveBackEnabled) {
                            stringResource(R.string.settings_predictive_enabled)
                        } else {
                            stringResource(R.string.settings_predictive_disabled)
                        },
                        onClick = {
                            viewModel.update {
                                it.copy(predictiveBackEnabled = !it.predictiveBackEnabled)
                            }
                        },
                    ) {
                        KixyuSwitch(
                            checked = state.settings.predictiveBackEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.update { it.copy(predictiveBackEnabled = enabled) }
                            },
                        )
                    }
                    KixyuDivider()
                    KixyuGlassEffectControls(
                        settings = state.settings,
                        onSettingsChange = viewModel::update,
                    )
                    KixyuDivider()
                    KixyuAppColorControl(
                        settings = state.settings,
                        onSettingsChange = viewModel::update,
                    )
                }
            }
            item { KixyuBottomContentSpacer() }
        }
    }
}
