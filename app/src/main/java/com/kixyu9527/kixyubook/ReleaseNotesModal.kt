package com.kixyu9527.kixyubook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import com.kixyu9527.kixyubook.core.common.model.ReleaseNotesState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAdaptiveModal
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import com.kixyu9527.kixyubook.update.ReleaseNotesMarkdown

@Composable
internal fun ReleaseNotesModal(
    show: Boolean,
    state: ReleaseNotesState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenReleasePage: () -> Boolean,
) {
    KixyuAdaptiveModal(show = show, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(
                    start = KixyuSpacing.large,
                    end = KixyuSpacing.large,
                    top = KixyuSpacing.medium,
                    bottom = KixyuSpacing.small,
                ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
        ) {
            Text(
                text = stringResource(R.string.release_notes_title, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
            // The release name sits above the notes viewport exactly like the update prompt's
            // version line, so both dialogs show the same title + subtitle + notes + footer shape.
            if (state is ReleaseNotesState.Available) {
                Text(
                    text = state.release.releaseName,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            // A fixed notes viewport keeps the dialog sized to its content instead of reserving the
            // adaptive maximum, which used to leave a large empty strip below the buttons.
            when (state) {
                ReleaseNotesState.Idle,
                ReleaseNotesState.Loading,
                -> Box(
                    Modifier.fillMaxWidth().height(KixyuSize.updateNotesMaxHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is ReleaseNotesState.Available -> ReleaseNotesMarkdown(
                    markdown = state.release.releaseNotes.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.release_notes_empty),
                    modifier = Modifier.fillMaxWidth().height(KixyuSize.updateNotesMaxHeight),
                )
                is ReleaseNotesState.Unavailable -> Column(
                    modifier = Modifier.fillMaxWidth().height(KixyuSize.updateNotesMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(
                        KixyuSpacing.small,
                        Alignment.CenterVertically,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    KixyuButton(text = stringResource(R.string.retry), onClick = onRetry)
                    KixyuTextButton(text = stringResource(R.string.open_github), onClick = { onOpenReleasePage() })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state is ReleaseNotesState.Available) {
                    Text(
                        text = stringResource(R.string.view_release_on_github),
                        modifier = Modifier.clickable { onOpenReleasePage() },
                        style = MaterialTheme.typography.labelLarge.copy(
                            textDecoration = TextDecoration.Underline,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                KixyuTextButton(text = stringResource(R.string.close), onClick = onDismiss)
            }
        }
    }
}
