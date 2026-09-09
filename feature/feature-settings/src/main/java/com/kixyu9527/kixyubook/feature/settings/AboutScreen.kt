package com.kixyu9527.kixyubook.feature.settings

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSecondaryButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
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
    onOpenDiagnosticLog: () -> Unit,
    onOpenProjectSource: () -> Boolean,
    onContactTelegram: () -> Boolean,
    appLogo: @Composable () -> Unit,
    embedded: Boolean = false,
) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openExternal: ((() -> Boolean), String) -> Unit = { open, errorMessage ->
        if (!open()) scope.launch { snackbar.showSnackbar(errorMessage) }
    }
    LaunchedEffect(updateState) {
        when (val result = updateState) {
            is AppUpdateState.UpToDate -> {
                snackbar.showSnackbar(resources.getString(R.string.settings_latest_version, result.currentVersion))
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
        title = stringResource(R.string.settings_about_title),
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
                KixyuSection(title = stringResource(R.string.settings_application_section)) {
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_app_name),
                        supportingText = stringResource(R.string.settings_app_summary),
                        leading = appLogo,
                    ) {
                        Text(stringResource(R.string.settings_version, currentVersion), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    KixyuDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.rowHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    ) {
                        KixyuButton(
                            text = if (updateState == AppUpdateState.Checking) stringResource(R.string.settings_checking_update) else stringResource(R.string.settings_check_update),
                            onClick = onCheckForUpdates,
                            modifier = Modifier.weight(1f),
                            enabled = updateState != AppUpdateState.Checking,
                        )
                        KixyuSecondaryButton(
                            text = stringResource(R.string.settings_release_notes),
                            onClick = onShowReleaseNotes,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                KixyuSection(title = stringResource(R.string.settings_diagnostics_section)) {
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_log_details),
                        supportingText = stringResource(R.string.settings_log_details_summary),
                        icon = KixyuSymbols.Storage,
                        onClick = onOpenDiagnosticLog,
                    ) {
                        Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon))
                    }
                }
            }
            item {
                KixyuSection(title = stringResource(R.string.settings_project_contact_section)) {
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_project_source),
                        supportingText = stringResource(R.string.settings_project_url),
                        onClick = {
                            openExternal(onOpenProjectSource, resources.getString(R.string.settings_open_github_error))
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
                        Icon(KixyuSymbols.OpenInNew, null, Modifier.size(KixyuSize.icon))
                    }
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_telegram_contact),
                        supportingText = stringResource(R.string.settings_telegram_handle),
                        onClick = {
                            openExternal(onContactTelegram, resources.getString(R.string.settings_open_telegram_error))
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
                        Icon(KixyuSymbols.OpenInNew, null, Modifier.size(KixyuSize.icon))
                    }
                }
            }
            item { KixyuBottomContentSpacer() }
        }
    }
}
