package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.common.operation.UserOperationController
import com.kixyu9527.kixyubook.core.common.operation.UserOperationKind
import com.kixyu9527.kixyubook.core.common.operation.UserOperationState
import com.kixyu9527.kixyubook.core.common.operation.OperationFailure
import com.kixyu9527.kixyubook.core.designsystem.R
import kotlinx.coroutines.delay

val LocalKixyuOperationController = compositionLocalOf<UserOperationController?> { null }

/** Shared glass feedback and retry ownership, without replacing the screen or back handler. */
@Composable
fun KixyuOperationHost(controller: UserOperationController, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKixyuOperationController provides controller) {
        Box(Modifier.fillMaxSize()) {
            content()
            OperationFeedback(controller, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun OperationFeedback(controller: UserOperationController, modifier: Modifier) {
    val state by controller.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val failure = stringResource(when ((state as? UserOperationState.Failed)?.failure) {
        OperationFailure.STORAGE_FULL -> R.string.kixyu_operation_storage_full
        OperationFailure.PERMISSION -> R.string.kixyu_operation_permission
        OperationFailure.SOURCE_MISSING -> R.string.kixyu_operation_source_missing
        OperationFailure.RESTART_REQUIRED -> R.string.kixyu_operation_restart
        OperationFailure.INVALID_INPUT -> R.string.kixyu_operation_invalid
        else -> R.string.kixyu_operation_failed
    })
    val retry = stringResource(R.string.kixyu_operation_retry)
    LaunchedEffect(state) {
        val failed = state as? UserOperationState.Failed ?: return@LaunchedEffect
        if (snackbar.showSnackbar(
                failure,
                actionLabel = retry.takeIf { failed.retryable },
                withDismissAction = true,
            ) == SnackbarResult.ActionPerformed
        ) {
            controller.retry(failed.attempt)
        } else {
            controller.dismissFailure(failed.attempt)
        }
    }
    val deleting = (state as? UserOperationState.Running)?.kind == UserOperationKind.DELETE
    var showDeleted by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if ((state as? UserOperationState.Succeeded)?.kind == UserOperationKind.DELETE) {
            showDeleted = true
            // A newer operation cancels this effect; the finally block still clears the notice so a
            // success prompt cannot outlive its timer when another write starts within 1.8 seconds.
            try {
                delay(1_800)
            } finally {
                showDeleted = false
            }
        } else {
            showDeleted = false
        }
    }
    KixyuSnackbarHost(snackbar, modifier)
    // Unified transient popup for both deletion states; only the delete kind opts in.
    KixyuTransientStatusPopup(
        visible = deleting,
        message = stringResource(R.string.kixyu_operation_deleting),
    )
    KixyuTransientStatusPopup(
        visible = showDeleted,
        message = stringResource(R.string.kixyu_operation_deleted),
        progress = false,
    )
    val confirming = state as? UserOperationState.Confirming
    KixyuActionDialog(
        show = confirming != null,
        title = stringResource(R.string.kixyu_operation_delete_title),
        onDismissRequest = controller::cancelConfirmation,
        confirmLabel = stringResource(R.string.kixyu_operation_delete),
        onConfirm = controller::acceptConfirmation,
    ) {
        Text(confirming?.targetLabel?.let { stringResource(R.string.kixyu_operation_delete_target, it) }
            ?: stringResource(R.string.kixyu_operation_delete_warning))
    }
}
