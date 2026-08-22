package com.kixyu9527.kixyubook

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.sync.LocalNotificationManager
import com.kixyu9527.kixyubook.update.AppUpdateDownloader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    @Inject lateinit var updateDownloader: AppUpdateDownloader
    @Inject lateinit var localNotifications: LocalNotificationManager
    @Inject lateinit var bookRepository: BookRepository
    private val notificationDestination = MutableStateFlow<String?>(null)
    private val externalBookImport = MutableStateFlow<ExternalBookImportRequest?>(null)
    private var externalImportSequence = 0L

    override fun onResume() {
        super.onResume()
        updateDownloader.resumePendingInstallIfPermitted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { appViewModel.settings.value == null }
        // Suppress action-needed notifications while this new window is becoming visible. The
        // process lifecycle owns the matching background transition so another visible window
        // cannot be mistaken for the whole app going to the background.
        localNotifications.onAppForeground()
        notificationDestination.value = intent.getStringExtra(
            LocalNotificationManager.EXTRA_NOTIFICATION_DESTINATION,
        )
        if (savedInstanceState == null) enqueueExternalBookImport(intent)
        // Select the target display mode before Compose produces its first frame. Changing the
        // window mode from a DisposableEffect after the initial draw can trigger an avoidable
        // relayout and a visible 60 -> 120 Hz hitch during cold start.
        requestHighestRefreshRate(window.decorView)
        setContent {
            val settings by appViewModel.settings.collectAsState()
            val updateState by appViewModel.updateState.collectAsState()
            val releaseNotesState by appViewModel.releaseNotesState.collectAsState()
            val cloudSyncState by appViewModel.cloudSyncState.collectAsState()
            val pendingNotificationDestination by notificationDestination.collectAsState()
            val pendingExternalBookImport by externalBookImport.collectAsState()
            val loadedSettings = settings
            if (loadedSettings == null) {
                // This surface normally exists for only a few milliseconds. It
                // deliberately contains no Material/MIUIX components or motion,
                // so the wrong component family can never flash on cold start.
                val bootstrapBackground = if (isSystemInDarkTheme()) {
                    Color(0xFF101113)
                } else {
                    Color(0xFFF7F7F9)
                }
                Box(Modifier.fillMaxSize().background(bootstrapBackground))
            } else {
                KixyuBookApp(
                    settings = loadedSettings,
                    updateState = updateState,
                    releaseNotesState = releaseNotesState,
                    cloudSyncState = cloudSyncState,
                    pendingNotificationDestination = pendingNotificationDestination,
                    pendingExternalBookImport = pendingExternalBookImport,
                    window = window,
                    onNotificationDestinationConsumed = { notificationDestination.value = null },
                    onExternalImportConsumed = { requestId ->
                        if (externalBookImport.value?.id == requestId) externalBookImport.value = null
                    },
                    onCheckForUpdates = appViewModel::checkForUpdates,
                    onUpdateResultConsumed = appViewModel::clearUpdateResult,
                    onLoadReleaseNotes = appViewModel::loadCurrentReleaseNotes,
                    onAnimationPriorityChanged = appViewModel::setAnimationActive,
                    onPrioritizeBookSync = appViewModel::prioritizeBookSync,
                    onBookOpened = bookRepository::markBookOpened,
                    onDownloadUpdate = updateDownloader::download,
                    onExitApp = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestination.value = intent.getStringExtra(
            LocalNotificationManager.EXTRA_NOTIFICATION_DESTINATION,
        )
        enqueueExternalBookImport(intent)
    }

    private fun enqueueExternalBookImport(intent: Intent) {
        val supportedUris = intent.supportedBookImportUris()
        if (supportedUris.isEmpty()) return
        externalImportSequence += 1
        externalBookImport.value = ExternalBookImportRequest(
            externalImportSequence,
            supportedUris,
        )
    }


    private fun requestHighestRefreshRate(contentView: View) {
        val highestRefreshRate = contentView.display
            ?.supportedModes
            ?.maxOfOrNull { it.refreshRate }
            ?: return

        val layoutParams = window.attributes
        if (layoutParams.preferredRefreshRate != highestRefreshRate) {
            layoutParams.preferredRefreshRate = highestRefreshRate
            window.attributes = layoutParams
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Compose renders through this AndroidComposeView. Request the
            // display's highest available rate without assuming 90/120 Hz.
            contentView.requestedFrameRate = highestRefreshRate
        }
    }
}
