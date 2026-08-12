package com.kixyu9527.kixyubook

import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.content.IntentCompat
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationBar
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationRail
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuAdaptiveModal
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuOverlayHost
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTransientStatusPopup
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuWindowWidthClass
import com.kixyu9527.kixyubook.core.designsystem.component.LocalKixyuNavigationContentPadding
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowWidthClass
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuUsesNavigationRail
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuDetailPageEnterTransition
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuDetailPageExitTransition
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.kixyuPageBackground
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.AppUpdateState
import com.kixyu9527.kixyubook.core.common.model.ReleaseNotesState
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.navigation.Routes
import com.kixyu9527.kixyubook.feature.home.HomeRoute
import com.kixyu9527.kixyubook.feature.library.LibraryRoute
import com.kixyu9527.kixyubook.feature.reader.ReaderRoute
import com.kixyu9527.kixyubook.feature.reader.CorrectionManagementRoute
import com.kixyu9527.kixyubook.feature.settings.SettingsRoute
import com.kixyu9527.kixyubook.feature.settings.ReadingSettingsRoute
import com.kixyu9527.kixyubook.feature.settings.CloudSyncRoute
import com.kixyu9527.kixyubook.feature.settings.GoogleAccountRoute
import com.kixyu9527.kixyubook.feature.settings.DataAndBackupRoute
import com.kixyu9527.kixyubook.feature.settings.AboutRoute
import com.kixyu9527.kixyubook.feature.settings.DiagnosticLogRoute
import com.kixyu9527.kixyubook.feature.settings.DiagnosticLogCategoryRoute
import com.kixyu9527.kixyubook.update.AppUpdateDownloader
import com.kixyu9527.kixyubook.update.ReleaseNotesMarkdown
import com.kixyu9527.kixyubook.core.sync.CloudSyncPhase
import com.kixyu9527.kixyubook.core.sync.CloudSyncState
import com.kixyu9527.kixyubook.core.sync.LocalNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
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
                KixyuBookApp(loadedSettings, updateState, releaseNotesState, cloudSyncState)
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
        val receivedUris = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> buildList {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
                addClipDataUris(intent)
            }
            Intent.ACTION_SEND_MULTIPLE -> buildList {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let(::addAll)
                addClipDataUris(intent)
            }
            else -> return
        }.distinct()
        val supportedUris = receivedUris.filter { uri ->
            val displayPath = uri.lastPathSegment.orEmpty()
            intent.type in setOf("text/plain", "application/epub+zip") ||
                displayPath.endsWith(".txt", ignoreCase = true) ||
                displayPath.endsWith(".epub", ignoreCase = true)
        }
        if (supportedUris.isEmpty()) return
        externalImportSequence += 1
        externalBookImport.value = ExternalBookImportRequest(
            externalImportSequence,
            supportedUris.map(Uri::toString),
        )
    }

    private fun MutableList<Uri>.addClipDataUris(intent: Intent) {
        val clipData = intent.clipData ?: return
        repeat(clipData.itemCount) { index -> clipData.getItemAt(index).uri?.let(::add) }
    }

    @Composable
    private fun KixyuBookApp(
        settings: com.kixyu9527.kixyubook.core.common.model.ReaderSettings,
        updateState: AppUpdateState,
        releaseNotesState: ReleaseNotesState,
        cloudSyncState: CloudSyncState,
    ) {
        val navController = rememberNavController()
        val pendingNotificationDestination by notificationDestination.collectAsState()
        val pendingExternalBookImport by externalBookImport.collectAsState()
        LaunchedEffect(pendingNotificationDestination) {
            val destination = pendingNotificationDestination ?: return@LaunchedEffect
            val route = when (destination) {
                LocalNotificationManager.DESTINATION_CLOUD_SYNC -> Routes.CLOUD_SYNC
                LocalNotificationManager.DESTINATION_DATA_BACKUP -> Routes.DATA_AND_BACKUP
                else -> Routes.HOME
            }
            if (route == Routes.HOME) {
                // HOME is the only top-level NavHost destination. Never push another HOME above
                // Reader (HOME -> Reader -> HOME), otherwise Back would reveal the old reader.
                if (navController.currentDestination?.route != Routes.HOME) {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            } else if (navController.currentDestination?.route != route) {
                // A notification opens a child of the top level, not a child of whichever screen
                // happened to be visible. Clear Reader/other details before adding that child so
                // its Back action always returns to HOME.
                navController.navigate(route) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            }
            notificationDestination.value = null
        }
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        // The UI-style setting adds/removes a MIUIX theme provider. Keep the
        // navigation subtree movable so a deliberate runtime style switch does
        // not recreate the current screen or bottom bar.
        val latestSettings = rememberUpdatedState(settings)
        val latestUpdateState = rememberUpdatedState(updateState)
        val latestReleaseNotesState = rememberUpdatedState(releaseNotesState)
        val latestExternalBookImport = rememberUpdatedState(pendingExternalBookImport)
        val appContent = remember {
            movableContentOf {
                KixyuNavHost(
                    navController = navController,
                    initialReaderSettings = latestSettings.value,
                    updateState = latestUpdateState.value,
                    releaseNotesState = latestReleaseNotesState.value,
                    onCheckForUpdates = appViewModel::checkForUpdates,
                    onUpdateResultConsumed = appViewModel::clearUpdateResult,
                    onLoadReleaseNotes = appViewModel::loadCurrentReleaseNotes,
                    onAnimationPriorityChanged = appViewModel::setAnimationActive,
                    onPrioritizeBookSync = appViewModel::prioritizeBookSync,
                    onBookOpened = bookRepository::markBookOpened,
                    externalImportRequestId = latestExternalBookImport.value?.id,
                    externalImportUris = latestExternalBookImport.value?.uris.orEmpty(),
                    onExternalImportConsumed = { requestId ->
                        if (externalBookImport.value?.id == requestId) externalBookImport.value = null
                    },
                    onExitApp = { finish() },
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
        val systemDark = isSystemInDarkTheme()
        val darkTheme = when (settings.theme) {
            ReaderTheme.DAY -> false
            ReaderTheme.NIGHT -> true
            ReaderTheme.SYSTEM -> systemDark
        }
        val view = LocalView.current
        DisposableEffect(view, darkTheme) {
            // Edge-to-edge is a window invariant, not a page preference.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            onDispose { }
        }
        KixyuBookTheme(
            themeMode = settings.theme,
            colorTheme = settings.appColorTheme,
            uiStyle = renderedUiStyle,
        ) {
            val appBackground = kixyuPageBackground()
            val availableUpdate = updateState as? AppUpdateState.Available
            val uriHandler = LocalUriHandler.current
            val updateUsesBottomSheet = kixyuWindowWidthClass() == KixyuWindowWidthClass.COMPACT
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
                            currentRoute?.startsWith("reader") != true
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
                KixyuAdaptiveModal(
                    show = availableUpdate != null,
                    onDismissRequest = appViewModel::clearUpdateResult,
                ) {
                    val update = availableUpdate?.update
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .then(if (updateUsesBottomSheet) Modifier.navigationBarsPadding() else Modifier)
                            .padding(
                                start = KixyuSpacing.large,
                                end = KixyuSpacing.large,
                                top = KixyuSpacing.medium,
                                bottom = KixyuSpacing.large,
                            ),
                        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
                    ) {
                        Text(
                            text = "发现新版本 ${update?.versionName.orEmpty()}",
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                        )
                        Text(
                            text = "当前版本 ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ReleaseNotesMarkdown(
                            markdown = update?.releaseNotes?.takeIf { it.isNotBlank() }
                                ?: "新版本已经发布，下载完成后将自动打开系统安装页面。",
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        update?.releaseUrl?.let { releaseUrl ->
                            Text(
                                text = "前往 GitHub 发布页",
                                modifier = Modifier.clickable {
                                    runCatching { uriHandler.openUri(releaseUrl) }
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    textDecoration = TextDecoration.Underline,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                KixyuSpacing.small,
                                Alignment.End,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KixyuTextButton(
                                text = "取消",
                                onClick = appViewModel::clearUpdateResult,
                            )
                            KixyuButton(
                                text = "下载",
                                onClick = {
                                    if (update != null && updateDownloader.download(update)) {
                                        appViewModel.clearUpdateResult()
                                    }
                                },
                                enabled = update?.downloadUrl != null,
                            )
                        }
                    }
                }
            }
        }
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

private data class TopDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private data class ExternalBookImportRequest(val id: Long, val uris: List<String>)

@Composable
private fun KixyuNavHost(
    navController: NavHostController,
    initialReaderSettings: ReaderSettings,
    updateState: AppUpdateState,
    releaseNotesState: ReleaseNotesState,
    onCheckForUpdates: () -> Unit,
    onUpdateResultConsumed: () -> Unit,
    onLoadReleaseNotes: () -> Unit,
    onAnimationPriorityChanged: (Boolean) -> Unit,
    onPrioritizeBookSync: (String) -> Unit,
    onBookOpened: (String) -> Unit,
    externalImportRequestId: Long?,
    externalImportUris: List<String>,
    onExternalImportConsumed: (Long) -> Unit,
    onExitApp: () -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val top = remember {
        listOf(
            TopDestination(Routes.HOME, "阅读", Icons.Outlined.AutoStories),
            TopDestination(Routes.LIBRARY, "书库", Icons.AutoMirrored.Outlined.LibraryBooks),
            TopDestination(Routes.SETTINGS, "设置", Icons.Outlined.Settings),
        )
    }
    val useNavigationRail = kixyuUsesNavigationRail()
    val pagerState = rememberPagerState(pageCount = { top.size })
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val uriHandler = LocalUriHandler.current
    var pageAnimation by remember { mutableStateOf<Job?>(null) }
    var animationPriorityJob by remember { mutableStateOf<Job?>(null) }
    var bookNavigationPending by remember { mutableStateOf(false) }
    var bookReorderAfterReaderExitJob by remember { mutableStateOf<Job?>(null) }
    var releaseNotesVisible by rememberSaveable { mutableStateOf(false) }
    var diagnosticOnlyFailures by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(externalImportRequestId) {
        if (externalImportRequestId == null) return@LaunchedEffect
        if (navController.currentDestination?.route != Routes.HOME) {
            navController.popBackStack(Routes.HOME, inclusive = false)
        }
        val libraryPage = top.indexOfFirst { it.route == Routes.LIBRARY }
        if (libraryPage >= 0 && pagerState.currentPage != libraryPage) {
            pagerState.scrollToPage(libraryPage)
        }
    }
    val topLevelActive = route == null || route == Routes.HOME
    // Home, Library and Settings are sibling pages inside the single HOME destination. At that
    // level Back exits the task; it must never pop an accidentally restored detail/reader entry.
    BackHandler(enabled = topLevelActive, onBack = onExitApp)
    val prioritizeAnimation: () -> Unit = {
        onAnimationPriorityChanged(true)
        animationPriorityJob?.cancel()
        animationPriorityJob = scope.launch {
            kotlinx.coroutines.delay(KixyuMotion.PageNavigationMillis.toLong())
            withFrameNanos { }
            withFrameNanos { }
            onAnimationPriorityChanged(false)
        }
    }
    var previousRoute by remember { mutableStateOf(route) }
    LaunchedEffect(route) {
        if (previousRoute != route) {
            previousRoute = route
            prioritizeAnimation()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            animationPriorityJob?.cancel()
            bookReorderAfterReaderExitJob?.cancel()
            onAnimationPriorityChanged(false)
        }
    }

    val openBook: (String) -> Unit = { bookUuid ->
        val sourceRoute = navController.currentDestination?.route
        if (sourceRoute in setOf(Routes.HOME, Routes.HIDDEN_LIBRARY) && !bookNavigationPending) {
            bookNavigationPending = true
            onPrioritizeBookSync(bookUuid)
            // Leave the current input dispatch, like Readest's setTimeout(0), without resuming
            // from a Compose frame callback. withFrameNanos resumed at the beginning of the next
            // VSYNC and placed destination creation directly inside that frame's 8.3 ms budget.
            view.post {
                if (navController.currentDestination?.route == sourceRoute) {
                    prioritizeAnimation()
                    navController.navigate(Routes.reader(bookUuid))
                }
                bookNavigationPending = false
            }
        }
    }

    // The bar is an overlay outside NavHost. During predictive back the
    // destination underneath can therefore occupy the full window; the bar is
    // introduced only after the pop has committed to a top-level destination.
    val showBar = topLevelActive
    // Delay its return until the top-level destination has committed. On exit,
    // AnimatedVisibility removes the bar after the short transition so an
    // invisible navigation item cannot intercept touches on secondary pages.
    var bottomBarPresented by remember { mutableStateOf(showBar) }
    LaunchedEffect(showBar) {
        if (showBar) {
            kotlinx.coroutines.delay(120)
            bottomBarPresented = true
        } else {
            bottomBarPresented = false
        }
    }
    val selectTopDestination: (KixyuNavigationItem) -> Unit = { destination ->
        val targetPage = top.indexOfFirst { it.route == destination.route }
        if (targetPage >= 0 && targetPage != pagerState.settledPage) {
            pageAnimation?.cancel()
            prioritizeAnimation()
            pageAnimation = scope.launch {
                pagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = tween(
                        durationMillis = KixyuMotion.PageNavigationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }
    val navBackground = kixyuPageBackground()
    CompositionLocalProvider(
        LocalKixyuNavigationContentPadding provides if (useNavigationRail) {
            0.dp
        } else {
            KixyuSize.bottomNavigationContentHeight
        },
    ) {
        Box(Modifier.fillMaxSize().background(navBackground)) {
            NavHost(
                navController,
                Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { kixyuDetailPageEnterTransition() },
                // Secondary destinations are a new surface above the current page. Keeping the
                // source stationary avoids translating two complete Compose trees at once and
                // preserves the visual hierarchy of a stacked detail page.
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { kixyuDetailPageExitTransition() },
            ) {
                composable(Routes.HOME) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().padding(
                            start = if (useNavigationRail) KixyuSize.navigationRailContentWidth else 0.dp,
                        ),
                        // Keep the adjacent library ready, but do not build all three complete page
                        // trees in the launch frame. Compose's pager prefetches the next page while
                        // retaining each page's saveable state, so the first frame no longer pays for
                        // Home + Library + Settings simultaneously.
                        beyondViewportPageCount = 1,
                        key = { page -> top[page].route },
                    ) { page ->
                        when (top[page].route) {
                            Routes.HOME -> HomeRoute(onOpenBook = openBook)
                            Routes.LIBRARY -> LibraryRoute(
                                onOpenBook = openBook,
                                onOpenHiddenLibrary = {
                                    prioritizeAnimation()
                                    navController.navigate(Routes.HIDDEN_LIBRARY) {
                                        launchSingleTop = true
                                    }
                                },
                                externalImportRequestId = externalImportRequestId,
                                externalImportUris = externalImportUris,
                                onExternalImportConsumed = onExternalImportConsumed,
                            )
                            Routes.SETTINGS -> SettingsRoute(
                                currentVersion = BuildConfig.VERSION_NAME,
                                onAppearance = {
                                    prioritizeAnimation()
                                    navController.navigate(Routes.APPEARANCE)
                                },
                                onCloudSync = {
                                    prioritizeAnimation()
                                    navController.navigate(Routes.CLOUD_SYNC)
                                },
                                onReadingSettings = {
                                    prioritizeAnimation()
                                    navController.navigate(Routes.READING_SETTINGS)
                                },
                                onDataAndBackup = {
                                    prioritizeAnimation()
                                    navController.navigate(Routes.DATA_AND_BACKUP)
                                },
                                onAbout = {
                                    prioritizeAnimation()
                                    navController.navigate(Routes.ABOUT)
                                },
                                detailContent = { pane ->
                                    when (pane) {
                                        com.kixyu9527.kixyubook.feature.settings.SettingsPane.CLOUD_SYNC ->
                                            CloudSyncRoute(
                                                onBack = {},
                                                onGoogleAccount = {
                                                    prioritizeAnimation()
                                                    navController.navigate(Routes.GOOGLE_ACCOUNT) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                embedded = true,
                                            )
                                        com.kixyu9527.kixyubook.feature.settings.SettingsPane.READING ->
                                            ReadingSettingsRoute(onBack = {}, embedded = true)
                                        com.kixyu9527.kixyubook.feature.settings.SettingsPane.APPEARANCE ->
                                            com.kixyu9527.kixyubook.feature.settings.AppearanceRoute(
                                                onBack = {},
                                                embedded = true,
                                            )
                                        com.kixyu9527.kixyubook.feature.settings.SettingsPane.DATA_AND_BACKUP ->
                                            DataAndBackupRoute(
                                                onBack = {},
                                                onOpenDiagnosticLog = {
                                                    prioritizeAnimation()
                                                    navController.navigate(Routes.DIAGNOSTIC_LOG)
                                                },
                                                embedded = true,
                                            )
                                        com.kixyu9527.kixyubook.feature.settings.SettingsPane.ABOUT ->
                                            AboutRoute(
                                                updateState = updateState,
                                                currentVersion = BuildConfig.VERSION_NAME,
                                                onCheckForUpdates = onCheckForUpdates,
                                                onUpdateResultConsumed = onUpdateResultConsumed,
                                                onShowReleaseNotes = {
                                                    onLoadReleaseNotes()
                                                    releaseNotesVisible = true
                                                },
                                                onOpenProjectSource = {
                                                    runCatching { uriHandler.openUri(PROJECT_SOURCE_URL) }.isSuccess
                                                },
                                                onContactTelegram = {
                                                    runCatching { uriHandler.openUri(TELEGRAM_CONTACT_URL) }.isSuccess
                                                },
                                                appLogo = {
                                                    Image(
                                                        painter = painterResource(R.drawable.ic_launcher_foreground),
                                                        contentDescription = "Kixyu Book Logo",
                                                        modifier = Modifier.size(56.dp),
                                                    )
                                                },
                                                onBack = {},
                                                embedded = true,
                                            )
                                    }
                                },
                            )
                        }
                    }
                }
                composable(Routes.HIDDEN_LIBRARY) {
                    LibraryRoute(
                        onOpenBook = openBook,
                        hiddenOnly = true,
                        onBack = {
                            prioritizeAnimation()
                            navController.popBackStack()
                        },
                    )
                }
                composable(Routes.APPEARANCE) {
                    com.kixyu9527.kixyubook.feature.settings.AppearanceRoute(onBack = {
                        prioritizeAnimation()
                        navController.popBackStack()
                    })
                }
                composable(Routes.READING_SETTINGS) {
                    ReadingSettingsRoute(onBack = {
                        prioritizeAnimation()
                        navController.popBackStack()
                    })
                }
                composable(Routes.CLOUD_SYNC) {
                    CloudSyncRoute(
                        onBack = {
                            prioritizeAnimation()
                            navController.popBackStack()
                        },
                        onGoogleAccount = {
                            prioritizeAnimation()
                            navController.navigate(Routes.GOOGLE_ACCOUNT) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(Routes.GOOGLE_ACCOUNT) {
                    GoogleAccountRoute(onBack = {
                        prioritizeAnimation()
                        navController.popBackStack()
                    })
                }
                composable(Routes.DATA_AND_BACKUP) {
                    DataAndBackupRoute(
                        onBack = {
                            prioritizeAnimation()
                            navController.popBackStack()
                        },
                        onOpenDiagnosticLog = {
                            prioritizeAnimation()
                            navController.navigate(Routes.DIAGNOSTIC_LOG)
                        },
                    )
                }
                composable(Routes.ABOUT) {
                    AboutRoute(
                        updateState = updateState,
                        currentVersion = BuildConfig.VERSION_NAME,
                        onCheckForUpdates = onCheckForUpdates,
                        onUpdateResultConsumed = onUpdateResultConsumed,
                        onShowReleaseNotes = {
                            onLoadReleaseNotes()
                            releaseNotesVisible = true
                        },
                        onOpenProjectSource = {
                            runCatching { uriHandler.openUri(PROJECT_SOURCE_URL) }.isSuccess
                        },
                        onContactTelegram = {
                            runCatching { uriHandler.openUri(TELEGRAM_CONTACT_URL) }.isSuccess
                        },
                        appLogo = {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = "Kixyu Book Logo",
                                modifier = Modifier.size(56.dp),
                            )
                        },
                        onBack = {
                            prioritizeAnimation()
                            navController.popBackStack()
                        },
                    )
                }
                composable(Routes.DIAGNOSTIC_LOG) {
                    DiagnosticLogRoute(
                        onlyFailures = diagnosticOnlyFailures,
                        onOnlyFailuresChanged = { diagnosticOnlyFailures = it },
                        onBack = {
                            prioritizeAnimation()
                            navController.popBackStack()
                        },
                        onOpenCategory = { category ->
                            prioritizeAnimation()
                            navController.navigate(Routes.diagnosticLogCategory(category))
                        },
                    )
                }
                composable(
                    route = Routes.DIAGNOSTIC_LOG_CATEGORY,
                    arguments = listOf(navArgument("category") { type = NavType.StringType }),
                ) { entry ->
                    DiagnosticLogCategoryRoute(
                        categoryKey = entry.arguments?.getString("category").orEmpty(),
                        onlyFailures = diagnosticOnlyFailures,
                        onOnlyFailuresChanged = { diagnosticOnlyFailures = it },
                        onBack = {
                            prioritizeAnimation()
                            navController.popBackStack()
                        },
                    )
                }
                composable(
                    route = Routes.READER,
                    arguments = listOf(navArgument("bookUuid") { type = NavType.StringType }),
                ) { entry ->
                    val bookUuid = entry.arguments?.getString("bookUuid").orEmpty()
                    ReaderRoute(
                        initialSettings = initialReaderSettings,
                        onManageCorrections = {
                            prioritizeAnimation()
                            navController.navigate(Routes.textCorrections(bookUuid))
                        },
                        onExit = {
                            prioritizeAnimation()
                            val returnToHiddenLibrary = navController.previousBackStackEntry
                                ?.destination?.route == Routes.HIDDEN_LIBRARY
                            val returned = if (returnToHiddenLibrary) {
                                navController.popBackStack()
                            } else {
                                // Normal reader entry belongs directly to the top level. Pop to that
                                // exact parent instead of trusting an arbitrary historical entry.
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            }
                            if (returned) {
                                bookUuid.takeIf(String::isNotBlank)?.let { exitedBookUuid ->
                                    bookReorderAfterReaderExitJob?.cancel()
                                    bookReorderAfterReaderExitJob = scope.launch {
                                        // Use the very same duration as the reader's pop transition:
                                        // the shelf order changes as soon as the return animation ends,
                                        // without waiting for another route recomposition or frame.
                                        kotlinx.coroutines.delay(
                                            KixyuMotion.PageNavigationMillis.toLong(),
                                        )
                                        onBookOpened(exitedBookUuid)
                                    }
                                }
                            } else {
                                onExitApp()
                            }
                        },
                    )
                }
                composable(
                    route = Routes.TEXT_CORRECTIONS,
                    arguments = listOf(navArgument("bookUuid") { type = NavType.StringType }),
                ) {
                    CorrectionManagementRoute(onBack = {
                        prioritizeAnimation()
                        navController.popBackStack()
                    })
                }
            }
            AnimatedVisibility(
                visible = bottomBarPresented && !useNavigationRail,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { height -> height / 8 },
                exit = fadeOut(tween(100)) + slideOutVertically(tween(100)) { height -> height / 8 },
            ) {
                KixyuNavigationBar(
                    items = top.map { KixyuNavigationItem(it.route, it.label, it.icon) },
                    selectedKey = top.getOrNull(pagerState.settledPage)?.route,
                    enabled = bottomBarPresented,
                    onSelected = selectTopDestination,
                )
            }
            AnimatedVisibility(
                visible = bottomBarPresented && useNavigationRail,
                modifier = Modifier.align(Alignment.CenterStart),
                enter = fadeIn(tween(160)) + slideInHorizontally(tween(160)) { width -> -width / 8 },
                exit = fadeOut(tween(100)) + slideOutHorizontally(tween(100)) { width -> -width / 8 },
            ) {
                KixyuNavigationRail(
                    items = top.map { KixyuNavigationItem(it.route, it.label, it.icon) },
                    selectedKey = top.getOrNull(pagerState.settledPage)?.route,
                    enabled = bottomBarPresented,
                    onSelected = selectTopDestination,
                )
            }
            ReleaseNotesModal(
                show = releaseNotesVisible,
                state = releaseNotesState,
                onDismiss = { releaseNotesVisible = false },
                onRetry = onLoadReleaseNotes,
                onOpenReleasePage = {
                    runCatching {
                        uriHandler.openUri(
                            (releaseNotesState as? ReleaseNotesState.Available)
                                ?.release
                                ?.releaseUrl
                                ?: "$PROJECT_SOURCE_URL/releases",
                        )
                    }.isSuccess
                },
            )
        }
    }
}

@Composable
private fun ReleaseNotesModal(
    show: Boolean,
    state: ReleaseNotesState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenReleasePage: () -> Boolean,
) {
    val useBottomSheet = kixyuWindowWidthClass() == KixyuWindowWidthClass.COMPACT
    KixyuAdaptiveModal(show = show, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .then(if (useBottomSheet) Modifier.navigationBarsPadding() else Modifier)
                .padding(
                    start = KixyuSpacing.large,
                    end = KixyuSpacing.large,
                    top = KixyuSpacing.medium,
                    bottom = KixyuSpacing.large,
                ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
        ) {
            Text(
                text = "更新日志 · v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
            when (state) {
                ReleaseNotesState.Idle,
                ReleaseNotesState.Loading,
                -> Box(
                    Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is ReleaseNotesState.Available -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                ) {
                    Text(
                        text = state.release.releaseName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ReleaseNotesMarkdown(
                        markdown = state.release.releaseNotes.takeIf { it.isNotBlank() }
                            ?: "此版本未填写 Release Note。",
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "在 GitHub 查看此版本",
                        modifier = Modifier.clickable { onOpenReleasePage() },
                        style = MaterialTheme.typography.labelLarge.copy(
                            textDecoration = TextDecoration.Underline,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is ReleaseNotesState.Unavailable -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    KixyuButton(text = "重试", onClick = onRetry)
                    KixyuTextButton(text = "前往 GitHub", onClick = { onOpenReleasePage() })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                KixyuTextButton(text = "关闭", onClick = onDismiss)
            }
        }
    }
}

private const val PROJECT_SOURCE_URL = "https://github.com/kkyu9527/kixyubook"
private const val TELEGRAM_CONTACT_URL = "https://t.me/kkyu9527s_bot"
