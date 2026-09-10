package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuEditorOperation
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.common.model.TextCorrection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton

@Composable
internal fun CorrectionEditDialog(
    original: String,
    initialReplacement: String,
    existing: TextCorrection?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onManageAll: (() -> Unit)? = null,
) {
    var replacement by rememberSaveable(original, initialReplacement) { mutableStateOf(initialReplacement) }
    val operation = rememberKixyuEditorOperation(onDismiss)
    KixyuActionDialog(
        show = true,
        title = if (existing == null) stringResource(R.string.reader_correct_paragraph) else stringResource(R.string.reader_edit_correction),
        onDismissRequest = { operation.edited(); onDismiss() },
        confirmLabel = stringResource(R.string.reader_annotation_save),
        confirmEnabled = !operation.running && replacement.isNotBlank() && replacement != original,
        onConfirm = { operation.submit { onSave(replacement) } },
        alternativeLabel = onDelete?.let { stringResource(R.string.reader_undo_correction) },
        alternativeEnabled = !operation.running,
        onAlternative = onDelete?.let { delete -> { operation.submit(delete) } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium)) {
            if (operation.failed) Text(stringResource(com.kixyu9527.kixyubook.core.designsystem.R.string.kixyu_operation_failed), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.reader_correction_original), style = MaterialTheme.typography.labelLarge)
            Text(original, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = replacement,
                enabled = !operation.running,
                onValueChange = { operation.edited(); replacement = it },
                label = { Text(stringResource(R.string.reader_correction_replacement)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 10,
            )
            onManageAll?.let {
                KixyuTextButton(
                    text = stringResource(R.string.reader_manage_corrections),
                    onClick = it,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
