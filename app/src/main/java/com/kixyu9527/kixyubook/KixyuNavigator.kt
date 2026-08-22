package com.kixyu9527.kixyubook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.kixyu9527.kixyubook.core.navigation.AppRoute

@Stable
internal class KixyuNavigator(initialRoute: AppRoute) {
    val backStack = mutableStateListOf(initialRoute)

    fun current(): AppRoute = backStack.last()

    fun previous(): AppRoute? = backStack.getOrNull(backStack.lastIndex - 1)

    fun push(route: AppRoute, launchSingleTop: Boolean = true) {
        if (!launchSingleTop || current() != route) backStack.add(route)
    }

    fun replaceAll(vararg routes: AppRoute) {
        require(routes.isNotEmpty())
        backStack.clear()
        backStack.addAll(routes)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun popToHome(): Boolean {
        if (backStack.size == 1 && current() == AppRoute.Home) return false
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (backStack.firstOrNull() != AppRoute.Home) replaceAll(AppRoute.Home)
        return true
    }
}

private val KixyuNavigatorSaver = listSaver<KixyuNavigator, AppRoute>(
    save = { navigator -> navigator.backStack.toList() },
    restore = { routes ->
        KixyuNavigator(routes.firstOrNull() ?: AppRoute.Home).apply {
            if (routes.size > 1) backStack.addAll(routes.drop(1))
        }
    },
)

@Composable
internal fun rememberKixyuNavigator(): KixyuNavigator =
    rememberSaveable(saver = KixyuNavigatorSaver) { KixyuNavigator(AppRoute.Home) }
