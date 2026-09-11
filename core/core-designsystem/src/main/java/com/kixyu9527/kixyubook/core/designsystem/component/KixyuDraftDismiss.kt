package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.designsystem.R

/** Dismissal from buttons, outside taps and predictive back follows the same draft policy. */
@Composable
fun rememberKixyuDraftDismiss(dirty: Boolean, onDiscard: () -> Unit): () -> Unit {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val discard by rememberUpdatedState(onDiscard)
    KixyuActionDialog(
        show = confirming,
        title = stringResource(R.string.kixyu_draft_discard_title),
        confirmLabel = stringResource(R.string.kixyu_draft_discard),
        onDismissRequest = { confirming = false },
        onConfirm = { confirming = false; discard() },
    ) { Text(stringResource(R.string.kixyu_draft_discard_message)) }
    return { if (dirty) confirming = true else discard() }
}
