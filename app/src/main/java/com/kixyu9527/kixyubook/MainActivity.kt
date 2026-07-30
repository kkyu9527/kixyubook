package com.kixyu9527.kixyubook

import android.os.Bundle
import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationBar
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuNavigationItem
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.designsystem.theme.kixyuPageBackground
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.navigation.Routes
import com.kixyu9527.kixyubook.feature.home.HomeRoute
import com.kixyu9527.kixyubook.feature.library.LibraryRoute
import com.kixyu9527.kixyubook.feature.reader.ReaderRoute
import com.kixyu9527.kixyubook.feature.settings.SettingsRoute
import com.kixyu9527.kixyubook.feature.settings.ReadingSettingsRoute
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        // Select the target display mode before Compose produces its first frame. Changing the
        // window mode from a DisposableEffect after the initial draw can trigger an avoidable
        // relayout and a visible 60 -> 120 Hz hitch during cold start.
        requestHighestRefreshRate(window.decorView)
        setContent {
            val settings by appViewModel.settings.collectAsState()
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
                KixyuBookApp(loadedSettings)
            }
        }
    }

    @Composable
    private fun KixyuBookApp(settings: com.kixyu9527.kixyubook.core.common.model.ReaderSettings) {
        val navController = rememberNavController()
        // The UI-style setting adds/removes a MIUIX theme provider. Keep the
        // navigation subtree movable so a deliberate runtime style switch does
        // not recreate the current screen or bottom bar.
        val latestSettings = rememberUpdatedState(settings)
        val appContent = remember {
            movableContentOf {
                KixyuNavHost(
                    navController = navController,
                    initialReaderSettings = latestSettings.value,
                    onAnimationPriorityChanged = appViewModel::setAnimationActive,
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
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
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
            Box(Modifier.fillMaxSize().background(appBackground)) {
                appContent()
                if (styleTransitionVeil.value > 0f) {
                    Box(
                        Modifier.fillMaxSize().background(
                            MaterialTheme.colorScheme.background.copy(alpha = styleTransitionVeil.value),
                        ),
                    )
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

@Composable
private fun KixyuNavHost(
    navController: NavHostController,
    initialReaderSettings: ReaderSettings,
    onAnimationPriorityChanged: (Boolean) -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val top = remember {
        listOf(
            TopDestination(Routes.HOME, "首页", Icons.Outlined.Home),
            TopDestination(Routes.LIBRARY, "书库", Icons.AutoMirrored.Outlined.LibraryBooks),
            TopDestination(Routes.SETTINGS, "设置", Icons.Outlined.Settings),
        )
    }
    val pagerState = rememberPagerState(pageCount = { top.size })
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var pageAnimation by remember { mutableStateOf<Job?>(null) }
    var animationPriorityJob by remember { mutableStateOf<Job?>(null) }
    var bookNavigationPending by remember { mutableStateOf(false) }
    val topLevelActive = route == null || route == Routes.HOME
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
            onAnimationPriorityChanged(false)
        }
    }

    val openBook: (String) -> Unit = { bookUuid ->
        if (navController.currentDestination?.route == Routes.HOME && !bookNavigationPending) {
            bookNavigationPending = true
            // Leave the current input dispatch, like Readest's setTimeout(0), without resuming
            // from a Compose frame callback. withFrameNanos resumed at the beginning of the next
            // VSYNC and placed destination creation directly inside that frame's 8.3 ms budget.
            view.post {
                if (navController.currentDestination?.route == Routes.HOME) {
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
    val navBackground = kixyuPageBackground()
    Box(Modifier.fillMaxSize().background(navBackground)) {
        NavHost(
            navController, Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                slideInHorizontally(
                    tween(KixyuMotion.PageNavigationMillis, easing = FastOutSlowInEasing),
                ) { width -> width }
            },
            // Secondary destinations are a new surface above the current page. Keeping the
            // source stationary avoids translating two complete Compose trees at once and
            // preserves the visual hierarchy of a stacked detail page.
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = {
                slideOutHorizontally(
                    tween(KixyuMotion.PageNavigationMillis, easing = FastOutSlowInEasing),
                ) { width -> width }
            },
        ) {
            composable(Routes.HOME) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // Keep the adjacent library ready, but do not build all three complete page
                    // trees in the launch frame. Compose's pager prefetches the next page while
                    // retaining each page's saveable state, so the first frame no longer pays for
                    // Home + Library + Settings simultaneously.
                    beyondViewportPageCount = 1,
                    key = { page -> top[page].route },
                ) { page ->
                    when (top[page].route) {
                        Routes.HOME -> HomeRoute(onOpenBook = openBook)
                        Routes.LIBRARY -> LibraryRoute(onOpenBook = openBook)
                        Routes.SETTINGS -> SettingsRoute(
                            onAppearance = {
                                prioritizeAnimation()
                                navController.navigate(Routes.APPEARANCE)
                            },
                            onReadingSettings = {
                                prioritizeAnimation()
                                navController.navigate(Routes.READING_SETTINGS)
                            },
                        )
                    }
                }
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
            composable(
                route = Routes.READER,
                arguments = listOf(navArgument("bookUuid") { type = NavType.StringType }),
            ) {
                ReaderRoute(
                    initialSettings = initialReaderSettings,
                    onExit = {
                        prioritizeAnimation()
                        navController.popBackStack()
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = bottomBarPresented,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { height -> height / 8 },
            exit = fadeOut(tween(100)) + slideOutVertically(tween(100)) { height -> height / 8 },
        ) {
            KixyuNavigationBar(
                items = top.map { KixyuNavigationItem(it.route, it.label, it.icon) },
                selectedKey = top.getOrNull(pagerState.settledPage)?.route,
                enabled = bottomBarPresented,
                onSelected = { destination ->
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
                },
            )
        }
    }
}
