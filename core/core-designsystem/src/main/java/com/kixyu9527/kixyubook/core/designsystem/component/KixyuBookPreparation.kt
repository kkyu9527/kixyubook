package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.common.model.BookPreparationProgress
import com.kixyu9527.kixyubook.core.common.model.BookPreparationStage
import com.kixyu9527.kixyubook.core.designsystem.R

@StringRes
fun bookPreparationStageLabel(stage: BookPreparationStage): Int = when (stage) {
    BookPreparationStage.WAITING_SYNC -> R.string.kixyu_book_preparation_waiting
    BookPreparationStage.AUTHORIZATION -> R.string.kixyu_book_preparation_authorization
    BookPreparationStage.LOCATING_SOURCE -> R.string.kixyu_book_preparation_locating
    BookPreparationStage.DOWNLOADING -> R.string.kixyu_book_preparation_downloading
    BookPreparationStage.VERIFYING -> R.string.kixyu_book_preparation_verifying
    BookPreparationStage.READING_METADATA -> R.string.kixyu_book_preparation_metadata
    BookPreparationStage.PREPARING_DIRECTORY -> R.string.kixyu_book_preparation_directory
    BookPreparationStage.RESTORING_PROGRESS -> R.string.kixyu_book_preparation_progress
    BookPreparationStage.READY -> R.string.kixyu_book_preparation_ready
}

@Composable
fun bookPreparationLabel(progress: BookPreparationProgress): String {
    if (progress.stage != BookPreparationStage.DOWNLOADING || progress.downloadedBytes <= 0) {
        return stringResource(bookPreparationStageLabel(progress.stage))
    }
    val context = LocalContext.current
    val received = android.text.format.Formatter.formatShortFileSize(context, progress.downloadedBytes)
    return if (progress.totalBytes > 0) {
        stringResource(R.string.kixyu_book_preparation_transfer, received,
            android.text.format.Formatter.formatShortFileSize(context, progress.totalBytes),
            (progress.downloadedBytes.toDouble() / progress.totalBytes * 100).toInt().coerceIn(0, 100))
    } else stringResource(R.string.kixyu_book_preparation_received, received)
}
