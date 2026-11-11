package com.kixyu9527.kixyubook.feature.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuEmptyState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuOperationHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuProgressBar
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.bookPreparationLabel
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.feature.cloud.R
import com.kixyu9527.kixyubook.core.sync.CloudBookDownload
import com.kixyu9527.kixyubook.core.sync.CloudBookDownloadStatus

@Composable
fun BookDownloadsRoute(
    onBack: () -> Unit,
    viewModel: CloudSettingsViewModel = hiltViewModel(),
) = KixyuOperationHost(viewModel.operations) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    BookDownloadsScreen(
        downloads = state.bookDownloads,
        onBack = onBack,
        onRetry = viewModel::retryBookDownload,
        snackbar = snackbar,
    )
}

/**
 * The queue is intentionally a separate destination: the Drive overview stays a status page,
 * while every pending body and its retry stays discoverable without flooding that page.
 */
@Composable
internal fun BookDownloadsScreen(
    downloads: List<CloudBookDownload>,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val failed = downloads.filter { it.status == CloudBookDownloadStatus.FAILED }
    val running = downloads.filter { it.status == CloudBookDownloadStatus.RUNNING }
    val waiting = downloads.filter {
        it.status == CloudBookDownloadStatus.PENDING || it.status == CloudBookDownloadStatus.WAITING_NETWORK
    }

    KixyuPageScaffold(
        title = stringResource(R.string.settings_book_download_tasks),
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) {
                Icon(KixyuSymbols.ArrowBack, stringResource(R.string.cloud_back))
            }
        },
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
            )
        },
    ) { innerPadding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                KixyuEmptyState(
                    icon = KixyuSymbols.CloudDone,
                    title = stringResource(R.string.settings_book_download_empty),
                    description = stringResource(R.string.settings_book_download_empty_summary),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.kixyuPageContentWidth()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    horizontal = KixyuSpacing.screenHorizontal,
                    vertical = KixyuSpacing.screenVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
            ) {
                if (failed.isNotEmpty()) {
                    item {
                        BookDownloadSection(
                            title = stringResource(R.string.settings_book_download_section_failed, failed.size),
                            downloads = failed,
                            onRetry = onRetry,
                        )
                    }
                }
                if (running.isNotEmpty()) {
                    item {
                        BookDownloadSection(
                            title = stringResource(R.string.settings_book_download_section_running, running.size),
                            downloads = running,
                            onRetry = onRetry,
                        )
                    }
                }
                if (waiting.isNotEmpty()) {
                    item {
                        BookDownloadSection(
                            title = stringResource(R.string.settings_book_download_section_waiting, waiting.size),
                            downloads = waiting,
                            onRetry = onRetry,
                        )
                    }
                }
                item { KixyuBottomContentSpacer() }
            }
        }
    }
}

@Composable
private fun BookDownloadSection(
    title: String,
    downloads: List<CloudBookDownload>,
    onRetry: (String) -> Unit,
) {
    KixyuSection(title = title) {
        downloads.forEachIndexed { index, task ->
            if (index > 0) KixyuDivider()
            BookDownloadRow(task = task, onRetry = onRetry)
        }
    }
}

@Composable
private fun BookDownloadRow(
    task: CloudBookDownload,
    onRetry: (String) -> Unit,
) {
    val failed = task.status == CloudBookDownloadStatus.FAILED
    val running = task.status == CloudBookDownloadStatus.RUNNING
    val detail = when {
        failed -> stringResource(R.string.settings_book_download_failed_row)
        running -> task.progress?.let { bookPreparationLabel(it) }
            ?: stringResource(R.string.settings_book_download_running)
        task.status == CloudBookDownloadStatus.WAITING_NETWORK ->
            stringResource(R.string.settings_book_download_waiting_network)
        else -> stringResource(R.string.settings_book_download_pending)
    }
    KixyuSettingsRow(
        title = task.title,
        supportingText = detail,
        icon = when (task.status) {
            CloudBookDownloadStatus.FAILED -> KixyuSymbols.ErrorOutline
            CloudBookDownloadStatus.RUNNING -> KixyuSymbols.CloudSync
            CloudBookDownloadStatus.WAITING_NETWORK -> KixyuSymbols.Wifi
            CloudBookDownloadStatus.PENDING -> KixyuSymbols.Cloud
        },
        contentColor = if (failed) MaterialTheme.colorScheme.error else null,
        // Matches the old inline queue: any queued book can be pulled forward immediately.
        onClick = if (running) null else ({ onRetry(task.bookUuid) }),
    ) {
        when {
            failed -> KixyuIconButton(onClick = { onRetry(task.bookUuid) }) {
                Icon(
                    KixyuSymbols.Refresh,
                    stringResource(R.string.settings_book_download_retry_named, task.title),
                )
            }
            running -> task.fraction?.let { fraction ->
                Text(
                    stringResource(R.string.settings_book_download_percent, (fraction * 100).toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (running) {
        KixyuProgressBar(
            progress = task.fraction,
            wavy = true,
            modifier = Modifier
                .padding(horizontal = KixyuSpacing.rowHorizontal)
                .padding(bottom = KixyuSpacing.small),
        )
    }
}
