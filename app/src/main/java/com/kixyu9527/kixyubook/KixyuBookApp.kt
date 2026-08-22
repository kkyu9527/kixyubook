package com.kixyu9527.kixyubook

import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kixyu9527.kixyubook.core.common.model.AppUpdateInfo
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.ReleaseNotesState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuOverlayHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSystemBarHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTransientStatusPopup
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuSystemBarHost
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.kixyuPageBackground
import com.kixyu9527.kixyubook.core.navigation.AppRoute
import com.kixyu9527.kixyubook.core.navigation.Routes
import com.kixyu9527.kixyubook.core.sync.CloudSyncPhase
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.LocalNotificationManager
import kotlinx.coroutines.delay

private const val NAVIGATION_PREFERENCES = "navigation_state"
private const val LAST_TOP_LEVEL_ROUTE = "last_top_level_route"

@Composable
internal fun KixyuBookApp(
    settings: ReaderSettings,
    updateState: AppUpdateState,
    releaseNotesState: ReleaseNotesState,
    cloudSyncState: CloudSyncState,
    pendingNotificationDestination: String?,
    pendingExternalBookImport: ExternalBookImportRequest?,
    window: Window,
    onNotificationDestinationConsumed: () -> Unit,
    onExternalImportConsumed: (Long) -> Unit,
    onCheckForUpdates: () -> Unit,
    onUpdateResultConsumed: () -> Unit,
    onLoadReleaseNotes: () -> Unit,
    onAnimationPriorityChanged: (Boolean) -> Unit,
    onPrioritizeBookSync: (String) -> Unit,
    onBookOpened: (String) -> Unit,
    onDownloadUpdate: (AppUpdateInfo) -> Boolean,
    onExitApp: () -> Unit,
) {
    val navigator = rememberKixyuNavigator()
    val context = LocalView.current.context
    val navigationPreferences = remember(context) {
        context.getSharedPreferences(NAVIGATION_PREFERENCES, android.content.Context.MODE_PRIVATE)
    }
    val initialTopLevelRoute = remember {
        navigationPreferences.getString(LAST_TOP_LEVEL_ROUTE, Routes.HOME)
    }
    val saveTopLevelRoute = remember(navigationPreferences) {
        { route: String ->
            navigationPreferences.edit { putString(LAST_TOP_LEVEL_ROUTE, route) }
        }
    }
    LaunchedEffect(pendingNotificationDestination) {
        val destination = pendingNotificationDestination ?: return@LaunchedEffect
        val route = when (destination) {
            LocalNotificationManager.DESTINATION_CLOUD_SYNC -> AppRoute.CloudSync
            LocalNotificationManager.DESTINATION_DATA_BACKUP -> AppRoute.Home
            else -> AppRoute.Home
        }
        if (route == AppRoute.Home) {
            navigator.popToHome()
        } else if (navigator.current() != route) {
            navigator.replaceAll(AppRoute.Home, route)
        }
        onNotificationDestinationConsumed()
    }

    val currentRoute = navigator.current()
    val latestSettings = rememberUpdatedState(settings)
    val latestUpdateState = rememberUpdatedState(updateState)
    val latestReleaseNotesState = rememberUpdatedState(releaseNotesState)
    val latestExternalBookImport = rememberUpdatedState(pendingExternalBookImport)
    val appContent = remember {
        movableContentOf {
            KixyuNavDisplay(
                navigator = navigator,
                initialTopLevelRoute = initialTopLevelRoute,
                onTopLevelRouteChanged = saveTopLevelRoute,
                initialReaderSettings = latestSettings.value,
                updateState = latestUpdateState.value,
                releaseNotesState = latestReleaseNotesState.value,
                onCheckForUpdates = onCheckForUpdates,
                onUpdateResultConsumed = onUpdateResultConsumed,
                onLoadReleaseNotes = onLoadReleaseNotes,
                onAnimationPriorityChanged = onAnimationPriorityChanged,
                onPrioritizeBookSync = onPrioritizeBookSync,
                onBookOpened = onBookOpened,
                externalImportRequestId = latestExternalBookImport.value?.id,
                externalImportUris = latestExternalBookImport.value?.uris.orEmpty(),
                onExternalImportConsumed = onExternalImportConsumed,
                onExitApp = onExitApp,
            )
        }
    }

    var renderedUiStyle by remember { mutableStateOf(settings.appUiStyle) }
    val styleTransitionVeil = remember { Animatable(0f) }
    LaunchedEffect(settings.appUiStyle) {
        if (settings.appUiStyle == renderedUiStyle) return@LaunchedEffect
        styleTransitionVeil.animateTo(1f, tween(110))
        renderedUiStyle = settings.appUiStyle
        withFrameNanos { }
        styleTransitionVeil.animateTo(0f, tween(190))
    }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val darkTheme = when (settings.theme) {
        ReaderTheme.DAY -> false
        ReaderTheme.NIGHT -> true
        ReaderTheme.SYSTEM -> systemDark
    }
    val systemBarHost = remember { KixyuSystemBarHost() }
    val systemBarPolicy = systemBarHost.policy
    val systemBarController = remember(window) {
        WindowCompat.getInsetsController(window, window.decorView)
    }
    val applySystemBars by rememberUpdatedState(newValue = {
        val policy = systemBarPolicy
        systemBarController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        systemBarController.isAppearanceLightStatusBars = policy?.useDarkIcons ?: !darkTheme
        systemBarController.isAppearanceLightNavigationBars = policy?.useDarkIcons ?: !darkTheme
        if (policy?.statusBarVisible == false) {
            systemBarController.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            systemBarController.show(WindowInsetsCompat.Type.statusBars())
        }
        if (policy?.navigationBarVisible == false) {
            systemBarController.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            systemBarController.show(WindowInsetsCompat.Type.navigationBars())
        }
    })
    SideEffect {
        applySystemBars()
    }
    DisposableEffect(window) {
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) applySystemBars()
        }
        window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            val observer = window.decorView.viewTreeObserver
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focusListener)
            systemBarController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    CompositionLocalProvider(
        LocalKixyuSystemBarHost provides systemBarHost,
    ) {
        KixyuBookTheme(
            themeMode = settings.theme,
            colorTheme = settings.appColorTheme,
            uiStyle = renderedUiStyle,
            glassEffectEnabled = settings.glassEffectEnabled,
            glassFrostLevel = settings.glassFrostLevel,
            predictiveBackEnabled = settings.predictiveBackEnabled,
        ) {
            val appBackground = kixyuPageBackground()
            KixyuOverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(appBackground)) {
                    appContent()
                    if (styleTransitionVeil.value > 0f) {
                        Box(
                            Modifier.fillMaxSize().background(
                                MaterialTheme.colorScheme.background.copy(alpha = styleTransitionVeil.value),
                            ),
                        )
                    }
                    var showGlobalSync by remember { mutableStateOf(false) }
                    LaunchedEffect(cloudSyncState.phase, currentRoute) {
                        showGlobalSync = false
                        if (cloudSyncState.phase == CloudSyncPhase.SYNCING &&
                            currentRoute !is AppRoute.Reader
                        ) {
                            delay(700)
                            showGlobalSync = cloudSyncState.phase == CloudSyncPhase.SYNCING
                        }
                    }
                    KixyuTransientStatusPopup(
                        visible = showGlobalSync,
                        message = "正在同步云端数据",
                    )
                }
                AvailableUpdateModal(
                    update = (updateState as? AppUpdateState.Available)?.update,
                    onDismiss = onUpdateResultConsumed,
                    onDownload = onDownloadUpdate,
                )
            }
        }
    }
}
