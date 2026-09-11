package com.kixyu9527.kixyubook.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.component.*

@Composable
internal fun LibraryBookRepairDialog(
    title: String,
    running: Boolean,
    progress: BookRepairProgress?,
    outcome: BookRepairOutcome?,
    onDismiss: () -> Unit,
    onRepair: (BookRepairMode) -> Unit,
) {
    KixyuActionDialog(show = true, title = title,
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(com.kixyu9527.kixyubook.core.designsystem.R.string.kixyu_close),
        onConfirm = onDismiss, dismissLabel = null,
    ) {
        Column {
            Text(stringResource(R.string.library_repair_explanation))
            BookRepairMode.entries.forEach { mode ->
                KixyuTextButton(text = stringResource(when (mode) {
                    BookRepairMode.CACHE -> R.string.library_repair_cache
                    BookRepairMode.SEARCH_INDEX -> R.string.library_repair_index
                    BookRepairMode.REPARSE -> R.string.library_repair_parse
                }), enabled = !running, onClick = { onRepair(mode) })
            }
            if (progress != null) {
                if (progress.total > 0) {
                    LinearProgressIndicator(progress = { progress.completed.toFloat() / progress.total })
                    Text("${progress.completed}/${progress.total}", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                } else Text(stringResource(R.string.library_repair_preparing))
            }
            outcome?.let {
                Text(stringResource(if (it.originalPreserved) R.string.library_repair_preserved else R.string.library_repair_complete),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}
