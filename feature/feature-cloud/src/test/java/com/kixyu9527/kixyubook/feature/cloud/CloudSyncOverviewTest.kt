package com.kixyu9527.kixyubook.feature.cloud

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.sync.CloudSyncPhase
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.SyncAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp-mdpi")
class CloudSyncOverviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialProgressStaysInsideStatusAndFinishesOnOneLine() = verifyTransition(AppUiStyle.MATERIAL)
    @Test fun miuixProgressStaysInsideStatusAndFinishesOnOneLine() = verifyTransition(AppUiStyle.MIUIX)

    @Test @Config(qualifiers = "en-rUS-w412dp-h915dp-mdpi")
    fun englishMaterialTimeFitsOneLine() = verifyTransition(AppUiStyle.MATERIAL)

    @Test @Config(qualifiers = "en-rUS-w412dp-h915dp-mdpi")
    fun englishMiuixTimeFitsOneLine() = verifyTransition(AppUiStyle.MIUIX)

    @Test @Config(qualifiers = "zh-rTW-w412dp-h915dp-mdpi")
    fun traditionalMaterialTimeFitsOneLine() = verifyTransition(AppUiStyle.MATERIAL)

    @Test @Config(qualifiers = "zh-rTW-w412dp-h915dp-mdpi")
    fun traditionalMiuixTimeFitsOneLine() = verifyTransition(AppUiStyle.MIUIX)

    @Test @Config(qualifiers = "ja-rJP-w412dp-h915dp-mdpi")
    fun japaneseMaterialTimeFitsOneLine() = verifyTransition(AppUiStyle.MATERIAL)

    @Test @Config(qualifiers = "ja-rJP-w412dp-h915dp-mdpi")
    fun japaneseMiuixTimeFitsOneLine() = verifyTransition(AppUiStyle.MIUIX)

    private fun verifyTransition(style: AppUiStyle) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var state by mutableStateOf(CloudSyncState(enabled = true, phase = CloudSyncPhase.SYNCING, pendingBookDownloads = 2))
        var syncActions = 0
        var downloadActions = 0
        compose.setContent {
            KixyuBookTheme(uiStyle = style) {
                Box(Modifier.width(360.dp)) {
                    CloudSyncOverviewCard(
                        state = state,
                        account = SyncAccount("test", "reader@example.com", "Reader", null),
                        onAccountClick = {},
                        onBookDownloads = { downloadActions++ },
                        onConnect = {},
                        connectEnabled = true,
                        onSyncAction = { syncActions++ },
                    )
                }
            }
        }
        val progressMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        val progress = compose.onNode(progressMatcher, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("cloud_sync_status").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText(context.getString(R.string.settings_sync_status_syncing), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Progress must align with status text", title.left, progress.left, 1f)
        assertTrue("Progress must have bottom breathing room inside the status row", row.bottom - progress.bottom >= 8f)
        compose.onNodeWithTag("cloud_sync_status").performClick()
        compose.runOnIdle { assertEquals(1, downloadActions) }

        compose.runOnIdle {
            state = state.copy(phase = CloudSyncPhase.SUCCESS, pendingBookDownloads = 0, lastSyncTime = 1_799_999_999_000L)
        }
        compose.onNode(progressMatcher, useUnmergedTree = true).assertDoesNotExist()
        val prefix = context.getString(R.string.settings_last_sync, "").trim()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(prefix, substring = true, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(1, results.single().lineCount)
        assertFalse("The complete localized timestamp should fit at normal phone width", results.single().isLineEllipsized(0))
        compose.onNodeWithContentDescription(context.getString(R.string.settings_sync_now)).performClick()
        compose.runOnIdle { assertEquals(1, syncActions) }
    }
}
