package com.kixyu9527.kixyubook.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.repository.BackupPreview
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSecondaryButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.sync.BackupOperationType
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun SettingsBackupSection(
    operation: BackupOperationType?,
    inspectionActive: Boolean,
    onRestore: () -> Unit,
    onExport: () -> Unit,
) {
    KixyuSection(title = stringResource(R.string.settings_data_section)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = KixyuSpacing.rowHorizontal,
                vertical = KixyuSpacing.medium,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.large),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            KixyuSymbols.Backup,
                            null,
                            Modifier.size(KixyuSize.icon),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                ) {
                    Text(
                        stringResource(R.string.settings_local_full_backup),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            when (operation) {
                                BackupOperationType.EXPORT -> R.string.settings_backup_exporting_detail
                                BackupOperationType.RESTORE -> R.string.settings_backup_restoring_detail
                                null -> R.string.settings_backup_content_detail
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            ) {
                KixyuSecondaryButton(
                    text = stringResource(
                        when {
                            inspectionActive -> R.string.settings_backup_inspecting
                            operation == BackupOperationType.RESTORE -> R.string.settings_backup_restoring
                            else -> R.string.settings_restore_backup
                        },
                    ),
                    onClick = onRestore,
                    enabled = operation == null && !inspectionActive,
                    modifier = Modifier.weight(1f),
                )
                KixyuButton(
                    text = stringResource(
                        if (operation == BackupOperationType.EXPORT) {
                            R.string.settings_backup_exporting
                        } else {
                            R.string.settings_export_backup
                        },
                    ),
                    onClick = onExport,
                    enabled = operation == null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun SettingsBackupDialogs(
    preview: BackupPreview?,
    restored: Boolean,
    restoreFailure: String?,
    onDismissPreview: () -> Unit,
    onRestore: (BackupPreview) -> Unit,
    onCloseApp: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    preview?.let { value ->
        KixyuActionDialog(
            show = true,
            title = stringResource(R.string.settings_restore_full_backup_title),
            onDismissRequest = onDismissPreview,
            confirmLabel = stringResource(R.string.settings_start_restore),
            onConfirm = { onRestore(value) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                Text(
                    stringResource(
                        R.string.settings_restore_preview_summary,
                        value.bookCount,
                        Formatter.formatFileSize(context, value.totalBytes),
                    ),
                )
                if (value.createdTime > 0L) {
                    Text(
                        stringResource(
                            R.string.settings_restore_preview_created,
                            SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(Date(value.createdTime)),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(
                        if (value.integrityProtected) {
                            R.string.settings_restore_integrity_verified
                        } else {
                            R.string.settings_restore_legacy_warning
                        },
                    ),
                    color = if (value.integrityProtected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                Text(stringResource(R.string.settings_restore_replace_warning))
            }
        }
    }
    KixyuActionDialog(
        show = restored,
        title = stringResource(if (restoreFailure == null) R.string.settings_restore_complete else R.string.settings_restore_recovery_title),
        onDismissRequest = {},
        confirmLabel = stringResource(R.string.settings_close_app),
        dismissLabel = null,
        onConfirm = onCloseApp,
    ) {
        Text(restoreFailure ?: stringResource(R.string.settings_restore_restart_hint))
    }
}
