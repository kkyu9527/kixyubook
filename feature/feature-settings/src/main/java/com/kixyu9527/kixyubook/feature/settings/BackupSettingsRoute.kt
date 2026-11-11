package com.kixyu9527.kixyubook.feature.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.ui.rememberNotificationPermissionAction
import kotlin.system.exitProcess

/**
 * Backup/restore is a multi-step, destructive flow, so it gets a dedicated page with its own
 * preview and completion dialogs instead of putting launch buttons on the settings overview.
 */
@Composable
fun BackupSettingsRoute(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: BackupSettingsViewModel = hiltViewModel(),
) {
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val backupPreview by viewModel.preview.collectAsStateWithLifecycle()
    val backupInspectionActive by viewModel.inspecting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val requestNotificationPermission = rememberNotificationPermissionAction()
    var restored by rememberSaveable { mutableStateOf(false) }
    var restoreFailure by rememberSaveable { mutableStateOf<String?>(null) }
    val backupCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.export(it.toString())
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.inspect(it.toString())
        }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.restoreCompleted.collect { restoreFailure = it; restored = true } }
    KixyuPageScaffold(
        title = stringResource(R.string.settings_backup_page_title),
        largeTitle = false,
        showTopBar = !embedded,
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
            )
        },
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
                SettingsBackupSection(
                    operation = operation,
                    inspectionActive = backupInspectionActive,
                    onRestore = {
                        requestNotificationPermission(false) {
                            backupPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                        }
                    },
                    onExport = {
                        requestNotificationPermission(false) {
                            backupCreator.launch(
                                "KixyuBook-${java.text.SimpleDateFormat(
                                    "yyyyMMdd-HHmm",
                                    java.util.Locale.US,
                                ).format(java.util.Date())}.kixyubackup",
                            )
                        }
                    },
                )
            }
            item { KixyuBottomContentSpacer() }
        }
    }
    SettingsBackupDialogs(
        preview = backupPreview,
        restored = restored,
        restoreFailure = restoreFailure,
        onDismissPreview = viewModel::clearPreview,
        onRestore = { preview ->
            viewModel.clearPreview()
            viewModel.restore(preview.uriString)
        },
        onCloseApp = {
            (context as? Activity)?.finishAffinity()
            exitProcess(0)
        },
    )
}
