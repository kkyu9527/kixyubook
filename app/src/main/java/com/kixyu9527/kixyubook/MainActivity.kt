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
        val appContent = remember {
            movableContentOf { KixyuNavHost(navController) }
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
        DisposableEffect(view) {
            requestHighestRefreshRate(view)
            onDispose { }
        }
        SideEffect {
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
    var pageAnimation by remember { mutableStateOf<Job?>(null) }
    val topLevelActive = route == null || route == Routes.HOME

    val openBook: (String) -> Unit = { bookUuid ->
        if (navController.currentDestination?.route == Routes.HOME) {
            navController.navigate(Routes.reader(bookUuid))
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
                slideInHorizontally(tween(KixyuMotion.PageNavigationMillis, easing = FastOutSlowInEasing)) { width -> width }
            },
            exitTransition = {
                slideOutHorizontally(tween(KixyuMotion.PageNavigationMillis, easing = FastOutSlowInEasing)) { width -> -width }
            },
            popEnterTransition = {
                slideInHorizontally(tween(KixyuMotion.PageNavigationMillis, easing = FastOutSlowInEasing)) { width -> -width }
            },
            popExitTransition = {
                slideOutHorizontally(tween(KixyuMotion.PageNavigationMillis, easing = FastOutSlowInEasing)) { width -> width }
            },
        ) {
            composable(Routes.HOME) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // The three top-level pages are small and fixed. Build all
                    // of them immediately so the first horizontal switch never
                    // pays a one-time composition cost.
                    beyondViewportPageCount = top.lastIndex,
                    key = { page -> top[page].route },
                ) { page ->
                    when (top[page].route) {
                        Routes.HOME -> HomeRoute(onOpenBook = openBook)
                        Routes.LIBRARY -> LibraryRoute(onOpenBook = openBook)
                        Routes.SETTINGS -> SettingsRoute(
                            onAppearance = { navController.navigate(Routes.APPEARANCE) },
                            onReadingSettings = { navController.navigate(Routes.READING_SETTINGS) },
                        )
                    }
                }
            }
            composable(Routes.APPEARANCE) {
                com.kixyu9527.kixyubook.feature.settings.AppearanceRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.READING_SETTINGS) {
                ReadingSettingsRoute(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.READER,
                arguments = listOf(navArgument("bookUuid") { type = NavType.StringType }),
            ) {
                ReaderRoute(onExit = { navController.popBackStack() })
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
