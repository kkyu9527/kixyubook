package com.kixyu9527.kixyubook

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelNavigationStateTest {
    private val routes = listOf("home", "library", "settings")

    @Test
    fun restoresThePreviouslySelectedTopLevelRoute() {
        assertEquals(1, topLevelPageForRoute("library", routes))
        assertEquals(2, topLevelPageForRoute("settings", routes))
    }

    @Test
    fun unknownOrMissingRouteFallsBackToReading() {
        assertEquals(0, topLevelPageForRoute(null, routes))
        assertEquals(0, topLevelPageForRoute("removed_route", routes))
    }
}
