package com.kixyu9527.kixyubook.feature.settings

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSnackbarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSettingsRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationContentPadding
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.displayName
import com.kixyu9527.kixyubook.core.sync.SyncAccount
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch

@Composable
fun GoogleAccountRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val account = state.cloudSync.account
    val openAccountError = stringResource(R.string.settings_open_account_error)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val navigationContentPadding = LocalKixyuNavigationContentPadding.current
    // A Custom Tab is hosted by the browser, so this app cannot make that Activity
    // edge-to-edge. Keep every browser-owned system surface on the same color to avoid
    // a visually detached navigation-bar strip.
    val customTabSystemSurfaceColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val customTabColors = remember(
        customTabSystemSurfaceColor,
    ) {
        CustomTabColorSchemeParams.Builder()
            .setToolbarColor(customTabSystemSurfaceColor)
            .setNavigationBarColor(customTabSystemSurfaceColor)
            .setNavigationBarDividerColor(customTabSystemSurfaceColor)
            .build()
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (activity != null) viewModel.finishGoogleAuthorization(activity, result.data)
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.authorizationRequests.collect { pendingIntent ->
            authorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }

    if (account == null) {
        KixyuPageScaffold(
            title = stringResource(R.string.settings_manage_google_account),
            largeTitle = false,
            modifier = Modifier.fillMaxSize(),
            navigationIcon = {
                KixyuIconButton(onClick = onBack) {
                    Icon(KixyuSymbols.ArrowBack, stringResource(R.string.settings_back_to_google_drive))
                }
            },
            snackbarHost = {
                KixyuSnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.settings_not_connected),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        GoogleAccountPage(
            account = account,
            snackbar = snackbar,
            navigationContentPadding = navigationContentPadding,
            onBack = onBack,
            onManageGoogleAccount = {
                val uri = Uri.Builder()
                    .scheme("https")
                    .authority("myaccount.google.com")
                    .appendQueryParameter("authuser", account.email)
                    .build()
                runCatching {
                    CustomTabsIntent.Builder()
                        .setDefaultColorSchemeParams(customTabColors)
                        .setShowTitle(false)
                        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                        .setUrlBarHidingEnabled(true)
                        .build()
                        .launchUrl(context, uri)
                }.recoverCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }.onFailure {
                    scope.launch { snackbar.showSnackbar(openAccountError) }
                }
            },
            onSwitchAccount = { activity?.let(viewModel::switchGoogleAccount) },
            onReconnect = { activity?.let(viewModel::connectGoogle) },
            onDisconnect = {
                viewModel.disconnectGoogle()
                onBack()
            },
            onDeleteCloudData = { confirmDelete = true },
        )
    }

    KixyuActionDialog(
        show = confirmDelete,
        title = stringResource(R.string.settings_delete_cloud_data_question),
        onDismissRequest = { confirmDelete = false },
        confirmLabel = stringResource(R.string.settings_delete_permanently),
        onConfirm = {
            confirmDelete = false
            activity?.let(viewModel::deleteCloudData)
        },
    ) {
        Text(stringResource(R.string.settings_delete_cloud_warning))
    }
}

@Composable
private fun GoogleAccountPage(
    account: SyncAccount,
    snackbar: SnackbarHostState,
    navigationContentPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onManageGoogleAccount: () -> Unit,
    onSwitchAccount: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDeleteCloudData: () -> Unit,
) {
    KixyuPageScaffold(
        title = stringResource(R.string.settings_manage_google_account),
        largeTitle = false,
        modifier = Modifier.fillMaxSize(),
        navigationIcon = {
            KixyuIconButton(onClick = onBack) {
                Icon(KixyuSymbols.ArrowBack, stringResource(R.string.settings_back_to_google_drive))
            }
        },
        snackbarHost = {
            KixyuSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.padding(horizontal = KixyuSpacing.screenHorizontal),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .kixyuPageContentWidth()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                KixyuSection {
                    KixyuSettingsRow(
                        title = account.displayName,
                        supportingText = account.email,
                        leading = { GoogleAccountAvatar(account) },
                    ) {
                        Text(
                            stringResource(R.string.settings_connected),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item {
                KixyuSection(title = stringResource(R.string.settings_account_authorization_section)) {
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_manage_your_google_account),
                        supportingText = stringResource(R.string.settings_manage_account_summary),
                        icon = KixyuSymbols.AccountCircle,
                        onClick = onManageGoogleAccount,
                    ) {
                        Icon(
                            KixyuSymbols.OpenInNew,
                            null,
                            Modifier.size(KixyuSize.icon),
                        )
                    }
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_switch_google_account),
                        supportingText = stringResource(R.string.settings_switch_account_summary),
                        icon = KixyuSymbols.SwitchAccount,
                        onClick = onSwitchAccount,
                    ) {
                        Icon(
                            KixyuSymbols.KeyboardArrowRight,
                            null,
                            Modifier.size(KixyuSize.icon),
                        )
                    }
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_reauthorize_google_drive),
                        supportingText = stringResource(R.string.settings_reauthorize_summary),
                        icon = KixyuSymbols.Refresh,
                        onClick = onReconnect,
                    )
                }
            }
            item {
                KixyuSection(title = stringResource(R.string.settings_account_actions_section)) {
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_disconnect_google_account),
                        supportingText = stringResource(R.string.settings_disconnect_account_summary),
                        icon = KixyuSymbols.Cloud,
                        contentColor = MaterialTheme.colorScheme.error,
                        onClick = onDisconnect,
                    )
                    KixyuDivider()
                    KixyuSettingsRow(
                        title = stringResource(R.string.settings_delete_cloud_data),
                        supportingText = stringResource(R.string.settings_delete_cloud_summary),
                        icon = KixyuSymbols.DeleteOutline,
                        contentColor = MaterialTheme.colorScheme.error,
                        onClick = onDeleteCloudData,
                    )
                }
            }
            item { KixyuBottomContentSpacer() }
        }
    }
}

@Composable
internal fun GoogleAccountAvatar(account: SyncAccount) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.size(KixyuSize.accountAvatar).clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                account.displayName.trim().firstOrNull()?.uppercase() ?: "G",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            account.avatarUrl?.let { avatarUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.settings_account_avatar, account.displayName),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
        }
    }
}
