package com.kixyu9527.kixyubook.feature.settings

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowSizeClass
import com.kixyu9527.kixyubook.core.designsystem.component.displayName
import java.util.Locale
import com.kixyu9527.kixyubook.core.sync.CloudSyncPhase
import com.kixyu9527.kixyubook.core.sync.BackupOperationType
import kotlinx.coroutines.launch

enum class SettingsPane { CLOUD_SYNC, READING, APPEARANCE, HABITS, BACKUP, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onCloudSync: () -> Unit,
    onReadingSettings: () -> Unit,
    onAppearance: () -> Unit,
    onHabits: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    currentVersion: String,
    detailContent: (@Composable (SettingsPane) -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) = com.kixyu9527.kixyubook.core.designsystem.component.KixyuOperationHost(viewModel.operations) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var rulesManagerVisible by rememberSaveable { mutableStateOf(false) }
    var ruleEditorVisible by rememberSaveable { mutableStateOf(false) }
    var advancedEditorVisible by rememberSaveable { mutableStateOf(false) }
    var pickedSample by rememberSaveable { mutableStateOf<String?>(null) }
    var editingSpec by remember { mutableStateOf<com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec?>(null) }
    val samplePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pickedSample = context.documentDisplayName(it) }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val syncAccount = state.cloudSync.account
    val windowSizeClass = kixyuWindowSizeClass()
    val twoPane = windowSizeClass.supportsTwoPane && detailContent != null
    var selectedPaneName by rememberSaveable { mutableStateOf(SettingsPane.READING.name) }
    val selectedPane = SettingsPane.entries.firstOrNull { it.name == selectedPaneName } ?: SettingsPane.READING
    val openPane: (SettingsPane, () -> Unit) -> Unit = { pane, compactNavigation ->
        if (twoPane) selectedPaneName = pane.name else compactNavigation()
    }

    val accountSection: @Composable () -> Unit = {
        KixyuSection(title = stringResource(R.string.settings_account_section)) {
            KixyuSettingsRow(
                title = stringResource(R.string.settings_google_sync),
                supportingText = when {
                    syncAccount == null -> stringResource(R.string.settings_sync_login_hint)
                    state.cloudSync.initialSyncDecision != null -> stringResource(R.string.settings_sync_conflict_account, syncAccount.email)
                    state.cloudSync.phase == CloudSyncPhase.SYNCING -> stringResource(R.string.settings_syncing_account, syncAccount.email)
                    state.cloudSync.pendingCount > 0 -> stringResource(R.string.settings_sync_pending_account, state.cloudSync.pendingCount, syncAccount.email)
                    else -> syncAccount.email
                },
                icon = when {
                    syncAccount == null -> KixyuSymbols.Cloud
                    state.cloudSync.initialSyncDecision != null -> KixyuSymbols.CloudSync
                    else -> KixyuSymbols.CloudDone
                },
                selected = if (twoPane) selectedPane == SettingsPane.CLOUD_SYNC else null,
                onClick = { openPane(SettingsPane.CLOUD_SYNC, onCloudSync) },
            ) { Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }
    val preferenceSection: @Composable () -> Unit = {
        KixyuSection(
            title = stringResource(R.string.settings_preferences_section),
        ) {
            KixyuSettingsRow(
                title = stringResource(R.string.settings_reading),
                supportingText = buildString {
                    append(state.settings.pageMode.displayName())
                    append(" · ")
                    append(state.fonts.firstOrNull { it.uuid == state.settings.fontUuid }?.name ?: stringResource(R.string.settings_system_font))
                },
                icon = KixyuSymbols.Tune,
                selected = if (twoPane) selectedPane == SettingsPane.READING else null,
                onClick = { openPane(SettingsPane.READING, onReadingSettings) },
            ) { Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
            KixyuDivider()
            KixyuSettingsRow(
                title = stringResource(R.string.settings_appearance),
                supportingText = stringResource(
                    R.string.settings_appearance_summary,
                    state.settings.theme.displayName(),
                    state.settings.appUiStyle.displayName(),
                    state.settings.appColorTheme.displayName(),
                ),
                icon = KixyuSymbols.Palette,
                selected = if (twoPane) selectedPane == SettingsPane.APPEARANCE else null,
                onClick = { openPane(SettingsPane.APPEARANCE, onAppearance) },
            ) { Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
            KixyuDivider()
            KixyuSettingsRow(
                title = stringResource(R.string.settings_reading_habits_section),
                supportingText = stringResource(
                    R.string.settings_habits_summary,
                    state.goalMinutes,
                    if (state.readingReminder.enabled) {
                        stringResource(
                            R.string.settings_reminder_enabled,
                            "%02d:%02d".format(
                                Locale.US,
                                state.readingReminder.hour,
                                state.readingReminder.minute,
                            ),
                        )
                    } else {
                        stringResource(R.string.settings_habits_reminder_off)
                    },
                ),
                icon = KixyuSymbols.Schedule,
                selected = if (twoPane) selectedPane == SettingsPane.HABITS else null,
                onClick = { openPane(SettingsPane.HABITS, onHabits) },
            ) { Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }
    val metadataSection: @Composable () -> Unit = {
        KixyuSection(
            title = stringResource(R.string.settings_metadata_section),
        ) {
            KixyuSettingsRow(
                title = stringResource(R.string.settings_filename_rules),
                supportingText = if (state.filenameRules.isEmpty() && state.filenameRuleSpecs.isEmpty()) {
                    stringResource(R.string.settings_filename_rules_empty)
                } else {
                    stringResource(
                        R.string.settings_filename_rules_summary,
                        state.filenameRules.size + state.filenameRuleSpecs.size,
                    )
                },
                icon = KixyuSymbols.Edit,
                onClick = { rulesManagerVisible = true },
            ) { Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }
    val dataSection: @Composable () -> Unit = {
        KixyuSection(
            title = stringResource(R.string.settings_data_section),
        ) {
            KixyuSettingsRow(
                title = stringResource(R.string.settings_backup_page_title),
                supportingText = when (state.backupOperation) {
                    BackupOperationType.EXPORT -> stringResource(R.string.settings_backup_exporting_detail)
                    BackupOperationType.RESTORE -> stringResource(R.string.settings_backup_restoring_detail)
                    null -> null
                },
                icon = KixyuSymbols.Backup,
                selected = if (twoPane) selectedPane == SettingsPane.BACKUP else null,
                onClick = { openPane(SettingsPane.BACKUP, onBackup) },
            ) { Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }
    val aboutSection: @Composable () -> Unit = {
        KixyuSection(title = stringResource(R.string.settings_about_section)) {
            KixyuSettingsRow(
                title = stringResource(R.string.settings_about_app),
                supportingText = stringResource(R.string.settings_about_summary, currentVersion),
                icon = KixyuSymbols.Info,
                selected = if (twoPane) selectedPane == SettingsPane.ABOUT else null,
                onClick = { openPane(SettingsPane.ABOUT, onAbout) },
            ) { Icon(KixyuSymbols.KeyboardArrowRight, null, Modifier.size(KixyuSize.icon)) }
        }
    }

    KixyuPageScaffold(
        title = stringResource(R.string.settings_title),
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
            )
        },
    ) { innerPadding ->
        if (twoPane) {
            Row(
                modifier = Modifier.kixyuPageContentWidth(KixyuSize.expandedPageContentMaxWidth)
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .padding(horizontal = KixyuSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(.36f).fillMaxSize(),
                    contentPadding = PaddingValues(vertical = KixyuSpacing.screenVertical),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
                ) {
                    item { accountSection() }
                    item { preferenceSection() }
                    item { metadataSection() }
                    item { dataSection() }
                    item { aboutSection() }
                    item { KixyuBottomContentSpacer() }
                }
                Surface(
                    modifier = Modifier.weight(.64f).fillMaxSize()
                        .padding(top = KixyuSpacing.screenVertical),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Box(Modifier.fillMaxSize()) { detailContent.invoke(selectedPane) }
                }
            }
        } else {
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
                item { accountSection() }
                item { preferenceSection() }
                item { metadataSection() }
                item { dataSection() }
                item { aboutSection() }
                item { KixyuBottomContentSpacer() }
            }
        }
    }

    if (rulesManagerVisible) {
        FilenameRulesManagerDialog(
            specs = state.filenameRuleSpecs,
            advancedRules = state.filenameRules,
            onAdd = {
                pickedSample = null
                editingSpec = null
                ruleEditorVisible = true
            },
            onEditSpec = { spec ->
                editingSpec = spec
                pickedSample = null
                ruleEditorVisible = true
            },
            onSetSpecEnabled = viewModel::setFilenameRuleSpecEnabled,
            onDeleteSpec = viewModel::removeFilenameRuleSpec,
            onAddAdvanced = { advancedEditorVisible = true },
            onDeleteAdvanced = viewModel::removeFilenameRule,
            onDismiss = { rulesManagerVisible = false },
        )
    }
    if (ruleEditorVisible) {
        FilenameRuleEditorDialog(
            pickedSample = pickedSample,
            initialSpec = editingSpec,
            onPickFile = {
                samplePicker.launch(arrayOf("text/plain", "application/epub+zip", "application/octet-stream"))
            },
            onSave = { spec ->
                viewModel.addFilenameRuleSpec(spec)
                ruleEditorVisible = false
                pickedSample = null
                editingSpec = null
            },
            onDismiss = {
                ruleEditorVisible = false
                pickedSample = null
                editingSpec = null
            },
        )
    }
    if (advancedEditorVisible) {
        AdvancedRegexRuleDialog(
            onSave = { pattern ->
                viewModel.addFilenameRule(pattern)
                advancedEditorVisible = false
            },
            onDismiss = { advancedEditorVisible = false },
        )
    }

}
