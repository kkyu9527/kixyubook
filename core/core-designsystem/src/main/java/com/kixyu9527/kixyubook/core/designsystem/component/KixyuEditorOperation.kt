package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.kixyu9527.kixyubook.core.common.operation.UserOperationState

class KixyuEditorOperation(
    val running: Boolean,
    val failed: Boolean,
    val submit: (() -> Unit) -> Unit,
    val edited: () -> Unit,
)

/** Editors close after the matching write succeeds, not after merely dispatching a callback. */
@Composable
fun rememberKixyuEditorOperation(onSaved: () -> Unit): KixyuEditorOperation {
    val controller = LocalKixyuOperationController.current
    val operation = controller?.state?.collectAsState()?.value
    var pending by rememberSaveable { mutableStateOf<Long?>(null) }
    val saved by rememberUpdatedState(onSaved)
    LaunchedEffect(operation) {
        if (pending != null && operation?.attempt == pending && operation is UserOperationState.Succeeded) {
            pending = null
            saved()
        }
    }
    return KixyuEditorOperation(
        running = operation is UserOperationState.Running,
        failed = pending != null && operation?.attempt == pending && operation is UserOperationState.Failed,
        submit = { action ->
            if (operation !is UserOperationState.Running) {
                action()
                pending = controller?.state?.value?.attempt
                if (controller == null) saved()
            }
        },
        edited = {
            // Once the draft changes, an old snackbar must not save the failed OLD payload
            // and close the editor over the user's new text.
            pending?.let { controller?.dismissFailure(it) }
            if (operation is UserOperationState.Failed) pending = null
        },
    )
}
