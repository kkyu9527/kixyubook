package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.operation.UserOperationController
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuOperationController
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnnotationOperationUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun materialSaveFailureKeepsDraftAndSuccessClosesEditor() = exercise(AppUiStyle.MATERIAL)
    @Test fun miuixSaveFailureKeepsDraftAndSuccessClosesEditor() = exercise(AppUiStyle.MIUIX)
    @Test fun materialDirtyDraftRequiresExplicitDiscard() = discardDraft(AppUiStyle.MATERIAL)
    @Test fun miuixDirtyDraftRequiresExplicitDiscard() = discardDraft(AppUiStyle.MIUIX)
    @Test fun materialCancelDiscardReturnsFocusToDraft() = keepDraft(AppUiStyle.MATERIAL)
    @Test fun miuixCancelDiscardReturnsFocusToDraft() = keepDraft(AppUiStyle.MIUIX)

    private fun keepDraft(style: AppUiStyle) {
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                AnnotationNoteDialog("excerpt", "", {}, {})
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("draft")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        compose.onAllNodesWithText("取消").onLast().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("draft").assertIsFocused()
    }

    private fun discardDraft(style: AppUiStyle) {
        var open by mutableStateOf(true)
        compose.setContent {
            KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                if (open) AnnotationNoteDialog("excerpt", "", { open = false }, {})
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("draft")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        compose.runOnIdle { assertTrue(open) }
        compose.onNodeWithText("放弃修改").performClick()
        compose.runOnIdle { assertFalse(open) }
    }

    private fun exercise(style: AppUiStyle) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val operations = UserOperationController(scope)
        var open by mutableStateOf(true)
        var fail = true
        var saved = ""
        try {
            compose.setContent {
                KixyuBookTheme(themeMode = ReaderTheme.DAY, uiStyle = style) {
                    CompositionLocalProvider(LocalKixyuOperationController provides operations) {
                        if (open) AnnotationNoteDialog("excerpt", "", { open = false }, { draft ->
                            operations.submit {
                                if (fail) throw IOException("disk full")
                                saved = draft
                            }
                        })
                    }
                }
            }
            compose.onNode(hasSetTextAction()).performTextInput("keep my draft")
            compose.onNodeWithText("保存").performClick()
            compose.waitForIdle()
            compose.onNode(hasSetTextAction()).assertTextContains("keep my draft")
            compose.runOnIdle { assertTrue(open); assertEquals("", saved); fail = false }
            val failedAttempt = operations.state.value.attempt
            compose.onNode(hasSetTextAction()).performTextInput(" edited")
            compose.runOnIdle { operations.retry(failedAttempt); assertEquals("", saved); assertTrue(open) }
            compose.onNodeWithText("保存").performClick()
            compose.waitForIdle()
            compose.runOnIdle { assertFalse(open); assertEquals("keep my draft edited", saved) }
        } finally { scope.cancel() }
    }
}
