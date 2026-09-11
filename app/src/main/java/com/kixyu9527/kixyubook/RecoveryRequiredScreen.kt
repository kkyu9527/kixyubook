package com.kixyu9527.kixyubook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.component.*
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import kotlinx.coroutines.launch

/** Deliberately independent of Hilt/Room/DataStore: incomplete restored files stay inaccessible. */
@Composable
internal fun RecoveryRequiredScreen(onClose: () -> Unit, onRetry: suspend () -> Unit) {
    val context = LocalContext.current
    val uiStyle = remember {
        runCatching { AppUiStyle.valueOf(context.getSharedPreferences("recovery_ui", 0)
            .getString("style", AppUiStyle.MATERIAL.name)!!) }.getOrDefault(AppUiStyle.MATERIAL)
    }
    var working by remember { mutableStateOf(false) }
    var retried by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    KixyuBookTheme(uiStyle = uiStyle) {
        KixyuPageScaffold(title = stringResource(R.string.recovery_title)) { padding ->
            LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding), contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium)) {
                item {
                    Text(stringResource(if (retried) R.string.recovery_retry_failed else R.string.recovery_message),
                        Modifier.padding(horizontal = KixyuSpacing.large).semantics { liveRegion = LiveRegionMode.Polite })
                }
                item {
                    KixyuButton(text = stringResource(R.string.recovery_retry), onClick = {
                        working = true
                        scope.launch { try { onRetry(); retried = true } finally { working = false } }
                    }, enabled = !working, modifier = Modifier.padding(horizontal = KixyuSpacing.large))
                }
                item {
                    KixyuSecondaryButton(text = stringResource(R.string.recovery_close), onClick = onClose, enabled = !working,
                        modifier = Modifier.padding(horizontal = KixyuSpacing.large))
                }
                item { KixyuBottomContentSpacer() }
            }
        }
    }
}
