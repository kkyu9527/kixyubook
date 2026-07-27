package com.kixyu9527.kixyubook.core.navigation

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"
    const val READING_SETTINGS = "reading_settings"
    const val READER = "reader/{bookUuid}"
    fun reader(bookUuid: String) = "reader/$bookUuid"
}
