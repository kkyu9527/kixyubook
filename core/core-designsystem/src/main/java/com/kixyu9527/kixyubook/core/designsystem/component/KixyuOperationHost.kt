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
import com.kixyu9527.kixyubook.core.designsystem.R

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
    val failure = stringResource(R.string.kixyu_operation_failed)
    val retry = stringResource(R.string.kixyu_operation_retry)
    LaunchedEffect(state.attempt, state.failed) {
        if (state.failed && snackbar.showSnackbar(failure, actionLabel = retry, withDismissAction = true) == SnackbarResult.ActionPerformed) {
            controller.retry(state.attempt)
        }
    }
    KixyuSnackbarHost(snackbar, modifier)
    KixyuActionDialog(
        show = state.awaitingConfirmation,
        title = stringResource(R.string.kixyu_operation_delete_title),
        onDismissRequest = controller::cancelConfirmation,
        confirmLabel = stringResource(R.string.kixyu_operation_delete),
        onConfirm = controller::acceptConfirmation,
    ) {
        Text(stringResource(R.string.kixyu_operation_delete_warning))
    }
}
