package com.kixyu9527.kixyubook

import com.kixyu9527.kixyubook.core.navigation.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KixyuNavigatorTest {
    @Test
    fun pushIsSingleTopAndPopRestoresPreviousDestination() {
        val navigator = KixyuNavigator(AppRoute.Home)

        navigator.push(AppRoute.Appearance)
        navigator.push(AppRoute.Appearance)

        assertEquals(listOf(AppRoute.Home, AppRoute.Appearance), navigator.backStack)
        assertEquals(AppRoute.Home, navigator.previous())
        assertTrue(navigator.pop())
        assertEquals(AppRoute.Home, navigator.current())
        assertFalse(navigator.pop())
    }

    @Test
    fun popToHomeClearsReaderAndNestedDestinations() {
        val navigator = KixyuNavigator(AppRoute.Home)
        navigator.push(AppRoute.Reader("book"))
        navigator.push(AppRoute.TextCorrections("book"))

        assertTrue(navigator.popToHome())

        assertEquals(listOf(AppRoute.Home), navigator.backStack)
        assertFalse(navigator.popToHome())
    }

    @Test
    fun replaceAllCreatesNotificationBackStack() {
        val navigator = KixyuNavigator(AppRoute.Reader("book"))

        navigator.replaceAll(AppRoute.Home, AppRoute.CloudSync)

        assertEquals(AppRoute.CloudSync, navigator.current())
        assertEquals(AppRoute.Home, navigator.previous())
    }
}
