package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.common.model.EpubLinkResult
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing

@Composable
internal fun EpubFootnoteDialog(
    footnote: EpubLinkResult.Footnote,
    onDismiss: () -> Unit,
) {
    KixyuActionDialog(
        show = true,
        title = footnote.title.ifBlank { stringResource(R.string.reader_epub_footnote) },
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.reader_epub_footnote_close),
        onConfirm = onDismiss,
        dismissLabel = null,
        content = {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = KixyuSpacing.extraSmall)) {
                Text(
                    text = footnote.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}
