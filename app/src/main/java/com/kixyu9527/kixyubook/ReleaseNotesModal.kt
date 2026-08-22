package com.kixyu9527.kixyubook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import com.kixyu9527.kixyubook.core.common.model.ReleaseNotesState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAdaptiveModal
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
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
                text = "更新日志 · v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
            when (state) {
                ReleaseNotesState.Idle,
                ReleaseNotesState.Loading,
                -> Box(
                    Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is ReleaseNotesState.Available -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                ) {
                    Text(
                        text = state.release.releaseName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ReleaseNotesMarkdown(
                        markdown = state.release.releaseNotes.takeIf { it.isNotBlank() }
                            ?: "此版本未填写 Release Note。",
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                is ReleaseNotesState.Unavailable -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    KixyuButton(text = "重试", onClick = onRetry)
                    KixyuTextButton(text = "前往 GitHub", onClick = { onOpenReleasePage() })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state is ReleaseNotesState.Available) {
                    Text(
                        text = "在 GitHub 查看此版本",
                        modifier = Modifier.clickable { onOpenReleasePage() },
                        style = MaterialTheme.typography.labelLarge.copy(
                            textDecoration = TextDecoration.Underline,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                KixyuTextButton(text = "关闭", onClick = onDismiss)
            }
        }
    }
}
