package com.kixyu9527.kixyubook.feature.cloud

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.BookPreparationProgress
import com.kixyu9527.kixyubook.core.common.model.BookPreparationStage
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.sync.CloudBookDownload
import com.kixyu9527.kixyubook.feature.cloud.R
import com.kixyu9527.kixyubook.core.sync.CloudBookDownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp")
class BookDownloadsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun materialThemeGroupsTasksAndRetriesOnlyFailures() {
        assertQueueBehaviour(AppUiStyle.MATERIAL)
    }

    @Test
    fun miuixThemeGroupsTasksAndRetriesOnlyFailures() {
        assertQueueBehaviour(AppUiStyle.MIUIX)
    }

    @Test
    fun emptyQueueExplainsThatNoActionIsNeeded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            KixyuBookTheme(uiStyle = AppUiStyle.MATERIAL) {
                BookDownloadsScreen(downloads = emptyList(), onBack = {}, onRetry = {})
            }
        }

        compose.onAllNodesWithText(context.getString(R.string.settings_book_download_empty)).onFirst().assertExists()
        compose.onAllNodesWithText(context.getString(R.string.settings_book_download_empty_summary)).onFirst().assertExists()
        compose.onAllNodesWithContentDescription(
            context.getString(R.string.settings_book_download_retry_named, "missing"),
        ).assertCountEquals(0)
    }

    private fun assertQueueBehaviour(style: AppUiStyle) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var retried: String? = null
        val failed = download("failed", "下载失败的书", CloudBookDownloadStatus.FAILED, attempts = 2)
        val running = download(
            "running",
            "下载中的书",
            CloudBookDownloadStatus.RUNNING,
            progress = BookPreparationProgress(BookPreparationStage.DOWNLOADING, 50, 100),
        )
        val waiting = download("waiting", "等待网络的书", CloudBookDownloadStatus.WAITING_NETWORK)
        val pending = download("pending", "排队的书", CloudBookDownloadStatus.PENDING)

        compose.setContent {
            KixyuBookTheme(uiStyle = style) {
                BookDownloadsScreen(
                    downloads = listOf(failed, running, waiting, pending),
                    onBack = {},
                    onRetry = { retried = it },
                )
            }
        }

        compose.onAllNodesWithText(context.getString(R.string.settings_book_download_section_failed, 1))
            .onFirst().assertExists()
        compose.onAllNodesWithText(context.getString(R.string.settings_book_download_section_running, 1))
            .onFirst().assertExists()
        compose.onAllNodesWithText(context.getString(R.string.settings_book_download_section_waiting, 2))
            .onFirst().assertExists()
        compose.onAllNodesWithText(running.title).onFirst().assertExists()
        compose.onAllNodesWithText(context.getString(R.string.settings_book_download_percent, 50))
            .onFirst().assertExists()
        compose.onAllNodesWithContentDescription(
            context.getString(R.string.settings_book_download_retry_named, failed.title),
        ).assertCountEquals(1)

        compose.onAllNodesWithContentDescription(
            context.getString(R.string.settings_book_download_retry_named, failed.title),
        )
            .onFirst()
            .performClick()
        compose.waitForIdle()
        assertEquals(failed.bookUuid, retried)
    }

    private fun download(
        uuid: String,
        title: String,
        status: CloudBookDownloadStatus,
        attempts: Int = 0,
        progress: BookPreparationProgress? = null,
    ) = CloudBookDownload(
        bookUuid = uuid,
        title = title,
        status = status,
        attempts = attempts,
        progress = progress,
    )
}
