package com.kixyu9527.kixyubook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSafeTopPopup
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPredictiveBackHandler
import com.kixyu9527.kixyubook.core.designsystem.component.rememberKixyuPredictiveBackState
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPredictivePopupTransform
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.bookPreparationLabel

/** Availability feedback never blocks browsing, replaces the shelf, or blurs its whole window. */
@Composable
internal fun BookOpeningFeedback(state: BookOpeningState?, onCancel: () -> Unit, onRetry: () -> Unit) {
    var retained by remember { mutableStateOf<BookOpeningState?>(null) }
    val visibleState = state?.takeIf { it.feedbackVisible }
    SideEffect { if (visibleState != null) retained = visibleState }
    // Keep the outgoing surface's content/height until the common exit animation finishes.
    // Clearing the request must not replace it with a shorter blank card mid-gesture.
    val displayed = visibleState ?: retained
    val failed = displayed?.error != null
    val back = rememberKixyuPredictiveBackState<String>()
    KixyuPredictiveBackHandler(target = state?.bookUuid, state = back, animateOnCommit = false,
        onBack = { onCancel() })
    KixyuSafeTopPopup(
        visible = state?.feedbackVisible == true,
        surfaceModifier = Modifier.kixyuPredictivePopupTransform { back.progress },
    ) {
        Column(Modifier.padding(KixyuSpacing.medium), verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(if (failed) R.string.book_open_failed else R.string.book_open_preparing),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                KixyuTextButton(stringResource(R.string.book_open_stay), onCancel)
            }
            if (displayed != null) Text(bookPreparationLabel(displayed.progress))
            if (failed) {
                Text(displayed.error.message ?: stringResource(R.string.book_open_failed), maxLines = 2)
                KixyuTextButton(stringResource(R.string.book_open_retry), onRetry)
            } else {
                val progress = displayed?.progress
                if (progress != null && progress.stage == com.kixyu9527.kixyubook.core.common.model.BookPreparationStage.DOWNLOADING && progress.totalBytes > 0) {
                    LinearProgressIndicator(progress = { (progress.downloadedBytes.toDouble() / progress.totalBytes).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(stringResource(R.string.book_open_background), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}
