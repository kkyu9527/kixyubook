package com.kixyu9527.kixyubook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import com.kixyu9527.kixyubook.core.common.model.AppUpdateInfo
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAdaptiveModal
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuWindowWidthClass
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowWidthClass
import com.kixyu9527.kixyubook.update.ReleaseNotesMarkdown

@Composable
internal fun AvailableUpdateModal(
    update: AppUpdateInfo?,
    onDismiss: () -> Unit,
    onDownload: (AppUpdateInfo) -> Boolean,
) {
    val uriHandler = LocalUriHandler.current
    val usesBottomSheet = kixyuWindowWidthClass() == KixyuWindowWidthClass.COMPACT
    KixyuAdaptiveModal(
        show = update != null,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .then(if (usesBottomSheet) Modifier.navigationBarsPadding() else Modifier)
                .padding(
                    start = KixyuSpacing.large,
                    end = KixyuSpacing.large,
                    top = KixyuSpacing.medium,
                    bottom = KixyuSpacing.large,
                ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
        ) {
            Text(
                text = "发现新版本 ${update?.versionName.orEmpty()}",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
            Text(
                text = "当前版本 ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReleaseNotesMarkdown(
                markdown = update?.releaseNotes?.takeIf { it.isNotBlank() }
                    ?: "新版本已经发布，下载完成后将自动打开系统安装页面。",
                modifier = Modifier.weight(1f, fill = false),
            )
            update?.releaseUrl?.let { releaseUrl ->
                Text(
                    text = "前往 GitHub 发布页",
                    modifier = Modifier.clickable {
                        runCatching { uriHandler.openUri(releaseUrl) }
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        textDecoration = TextDecoration.Underline,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    KixyuSpacing.small,
                    Alignment.End,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KixyuTextButton(text = "取消", onClick = onDismiss)
                KixyuButton(
                    text = "下载",
                    onClick = {
                        if (update != null && onDownload(update)) onDismiss()
                    },
                    enabled = update?.downloadUrl != null,
                )
            }
        }
    }
}
