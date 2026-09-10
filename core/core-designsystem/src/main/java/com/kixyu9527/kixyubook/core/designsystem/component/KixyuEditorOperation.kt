package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

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
        if (pending != null && operation?.attempt == pending && operation?.succeeded == true) {
            pending = null
            saved()
        }
    }
    return KixyuEditorOperation(
        running = operation?.running == true,
        failed = pending != null && operation?.attempt == pending && operation?.failed == true,
        submit = { action ->
            if (operation?.running != true) {
                action()
                pending = controller?.state?.value?.attempt
                if (controller == null) saved()
            }
        },
        edited = {
            // Once the draft changes, an old snackbar must not save the failed OLD payload
            // and close the editor over the user's new text.
            pending?.let { controller?.dismissFailure(it) }
            if (operation?.failed == true) pending = null
        },
    )
}
