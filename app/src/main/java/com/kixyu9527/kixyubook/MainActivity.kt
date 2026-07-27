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
import androidx.compose.ui.graphics.graphicsLayer
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
            val navController = rememberNavController()
            // The UI-style setting adds/removes a MIUIX theme provider. Keep
            // the navigation subtree movable so switching that provider does
            // not dispose and recreate the current screen or bottom bar.
            val appContent = remember {
                movableContentOf { KixyuNavHost(navController) }
            }
            val settings by appViewModel.settings.collectAsState()
            var renderedUiStyle by remember { mutableStateOf(settings.appUiStyle) }
            val styleTransitionVeil = remember { Animatable(0f) }
            LaunchedEffect(settings.appUiStyle) {
                if (settings.appUiStyle == renderedUiStyle) return@LaunchedEffect
                styleTransitionVeil.animateTo(1f, tween(110))
                renderedUiStyle = settings.appUiStyle
                // Let the new component tree settle behind an opaque frame,
                // then reveal it as one surface instead of exposing a hitch.
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
            SideEffect {
                // Edge-to-edge is a window invariant, not a page preference.
                // Re-assert it after theme/style changes so no component can
                // leave a contrast scrim behind the gesture navigation pill.
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
                requestHighestRefreshRate(view)
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
private fun KixyuNavHost(navController: NavHostController) {
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

    // The bar is an overlay outside NavHost. During predictive back the
    // destination underneath can therefore occupy the full window; the bar is
    // introduced only after the pop has committed to a top-level destination.
    val showBar = topLevelActive
    // Keep the floating bar composed while secondary destinations are shown.
    // Its visibility no longer changes the content insets of either route.
    var bottomBarPresented by remember { mutableStateOf(showBar) }
    LaunchedEffect(showBar) {
        if (showBar) {
            kotlinx.coroutines.delay(120)
            bottomBarPresented = true
        } else {
            bottomBarPresented = false
        }
    }
    val bottomBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (bottomBarPresented) 1f else 0f,
        animationSpec = tween(if (bottomBarPresented) 140 else 100),
        label = "bottomBarVisibility",
    )
    val navBackground = kixyuPageBackground()
    Box(Modifier.fillMaxSize().background(navBackground)) {
        NavHost(
            navController, Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                slideInHorizontally(tween(KixyuMotion.PageNavigationMillis)) { width -> width }
            },
            exitTransition = {
                slideOutHorizontally(tween(KixyuMotion.PageNavigationMillis)) { width -> -width }
            },
            popEnterTransition = {
                slideInHorizontally(tween(KixyuMotion.PageNavigationMillis)) { width -> -width }
            },
            popExitTransition = {
                slideOutHorizontally(tween(KixyuMotion.PageNavigationMillis)) { width -> width }
            },
        ) {
            composable(Routes.HOME) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = top.lastIndex,
                    key = { page -> top[page].route },
                ) { page ->
                    when (top[page].route) {
                        Routes.HOME -> HomeRoute(onOpenBook = { navController.navigate(Routes.reader(it)) })
                        Routes.LIBRARY -> LibraryRoute(onOpenBook = { navController.navigate(Routes.reader(it)) })
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
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer {
                alpha = bottomBarAlpha
                translationY = (1f - bottomBarAlpha) * size.height / 8f
            },
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
                                animationSpec = tween(KixyuMotion.PageNavigationMillis),
                            )
                        }
                    }
                },
            )
        }
    }
}
