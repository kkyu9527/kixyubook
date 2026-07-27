package com.kixyu9527.kixyubook

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.navigation.Routes
import com.kixyu9527.kixyubook.feature.home.HomeRoute
import com.kixyu9527.kixyubook.feature.library.LibraryRoute
import com.kixyu9527.kixyubook.feature.reader.ReaderRoute
import com.kixyu9527.kixyubook.feature.settings.SettingsRoute
import dagger.hilt.android.AndroidEntryPoint

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
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.theme) {
                ReaderTheme.DAY -> false
                ReaderTheme.NIGHT -> true
                ReaderTheme.SYSTEM -> systemDark
            }
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            KixyuBookTheme(
                themeMode = settings.theme,
                colorTheme = settings.appColorTheme,
            ) { KixyuNavHost() }
        }
    }
}

private data class TopDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun KixyuNavHost() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val top = listOf(
        TopDestination(Routes.HOME, "首页", Icons.Outlined.Home),
        TopDestination(Routes.LIBRARY, "书库", Icons.AutoMirrored.Outlined.LibraryBooks),
        TopDestination(Routes.SETTINGS, "设置", Icons.Outlined.Settings),
    )
    // The bar is an overlay outside NavHost. During predictive back the
    // destination underneath can therefore occupy the full window; the bar is
    // introduced only after the pop has committed to a top-level destination.
    val showBar = route == null || route in top.map { it.route }
    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController, Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(260)) + scaleIn(tween(360), initialScale = .92f) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) + scaleIn(tween(320), initialScale = 1.04f) },
            popExitTransition = { fadeOut(tween(240)) + scaleOut(tween(320), targetScale = .92f) },
        ) {
            composable(Routes.HOME) {
                HomeRoute(onOpenBook = { navController.navigate(Routes.reader(it)) })
            }
            composable(Routes.LIBRARY) {
                LibraryRoute(onOpenBook = { navController.navigate(Routes.reader(it)) })
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(onAppearance = { navController.navigate(Routes.APPEARANCE) })
            }
            composable(Routes.APPEARANCE) {
                com.kixyu9527.kixyubook.feature.settings.AppearanceRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.READER, arguments = listOf(navArgument("bookUuid") { type = NavType.StringType })) {
                ReaderRoute(onExit = { navController.popBackStack() })
            }
        }
        AnimatedVisibility(
            visible = showBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(220)) + slideInVertically(tween(320)) { it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(280)) { it },
        ) {
            NavigationBar {
                top.forEach { destination ->
                    NavigationBarItem(
                        selected = route == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        }
    }
}
