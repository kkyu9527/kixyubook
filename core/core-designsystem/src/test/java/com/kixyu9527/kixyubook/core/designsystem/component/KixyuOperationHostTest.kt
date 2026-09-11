package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.operation.UserOperationController
import com.kixyu9527.kixyubook.core.common.operation.UserOperationKind
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class KixyuOperationHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deleteReportsProgressAndCompletionAroundTheWrite() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        val controller = UserOperationController(scope)
        try {
            compose.setContent {
                KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                    KixyuOperationHost(controller) { Box(Modifier.fillMaxSize()) }
                }
            }
            compose.runOnIdle {
                controller.submit(kind = UserOperationKind.DELETE) { gate.await() }
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Deleting…").fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { gate.complete(Unit) }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Deleted").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test fun completionNoticeReusesThePopupWithoutASpinner() {
        val indeterminate = SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate,
        )
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = AppUiStyle.MATERIAL) {
                KixyuTransientStatusPopup(visible = true, message = "Deleted", progress = false)
            }
        }
        compose.onNodeWithText("Deleted").assertIsDisplayed()
        compose.onAllNodes(indeterminate).assertCountEquals(0)
    }
}
