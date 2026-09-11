package com.kixyu9527.kixyubook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import com.kixyu9527.kixyubook.core.common.model.AppUpdateInfo
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAdaptiveModal
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import com.kixyu9527.kixyubook.update.ReleaseNotesMarkdown

@Composable
internal fun AvailableUpdateModal(
    update: AppUpdateInfo?,
    onDismiss: () -> Unit,
    onDownload: (AppUpdateInfo) -> Boolean,
) {
    val uriHandler = LocalUriHandler.current
    KixyuAdaptiveModal(
        show = update != null,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(
                    start = KixyuSpacing.large,
                    end = KixyuSpacing.large,
                    top = KixyuSpacing.medium,
                    bottom = KixyuSpacing.large,
                ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
        ) {
            Text(
                text = stringResource(R.string.update_available_title, update?.versionName.orEmpty()),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReleaseNotesMarkdown(
                markdown = update?.releaseNotes?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.update_notes_fallback),
                // A fixed notes viewport keeps this prompt the same size as the release-notes
                // prompt while letting the dialog wrap its content instead of reserving the
                // adaptive maximum and leaving a large empty strip at the bottom.
                modifier = Modifier.fillMaxWidth().height(KixyuSize.updateNotesMaxHeight),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                update?.releaseUrl?.let { releaseUrl ->
                    Text(
                        text = stringResource(R.string.open_github_release),
                        modifier = Modifier.clickable {
                            runCatching { uriHandler.openUri(releaseUrl) }
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            textDecoration = TextDecoration.Underline,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                KixyuTextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
                KixyuButton(
                    text = stringResource(R.string.download),
                    onClick = {
                        if (update != null && onDownload(update)) onDismiss()
                    },
                    enabled = update?.downloadUrl != null,
                )
            }
        }
    }
}
