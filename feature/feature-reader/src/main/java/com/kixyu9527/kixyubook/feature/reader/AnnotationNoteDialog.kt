package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuEditorOperation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing

@Composable
internal fun AnnotationNoteDialog(
    excerpt: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var note by rememberSaveable(excerpt, initialNote) { mutableStateOf(initialNote) }
    val operation = rememberKixyuEditorOperation(onDismiss)
    val dismissDraft = com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuDraftDismiss(note != initialNote) {
        operation.edited(); onDismiss()
    }
    KixyuActionDialog(
        show = true,
        title = stringResource(R.string.reader_annotation_note_title),
        onDismissRequest = dismissDraft,
        dismissRequiresConfirmation = note != initialNote,
        confirmLabel = stringResource(R.string.reader_annotation_save),
        confirmEnabled = !operation.running,
        onConfirm = { operation.submit { onSave(note) } },
        alternativeLabel = onDelete?.let { stringResource(R.string.reader_annotation_delete) },
        alternativeEnabled = !operation.running,
        onAlternative = onDelete?.let { delete -> { operation.submit(delete) } },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium)) {
                if (operation.failed) {
                    Text(stringResource(com.kixyu9527.kixyubook.core.designsystem.R.string.kixyu_operation_failed), color = MaterialTheme.colorScheme.error)
                }
                Text(
                    text = excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = note,
                    enabled = !operation.running,
                    onValueChange = { operation.edited(); note = it },
                    label = { Text(stringResource(R.string.reader_annotation_note_hint)) },
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
    )
}
