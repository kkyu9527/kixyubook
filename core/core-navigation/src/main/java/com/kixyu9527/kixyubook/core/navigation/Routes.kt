package com.kixyu9527.kixyubook.core.navigation

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val HIDDEN_LIBRARY = "hidden_library"
    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"
    const val READING_SETTINGS = "reading_settings"
    const val READING_INFORMATION = "reading_settings/information"
    const val FONT_MANAGEMENT = "reading_settings/fonts"
    const val CLOUD_SYNC = "cloud_sync"
    const val GOOGLE_ACCOUNT = "cloud_sync/google_account"
    const val ABOUT = "about"
    const val DIAGNOSTIC_LOG = "diagnostic_log"
    const val DIAGNOSTIC_LOG_CATEGORY = "diagnostic_log/{category}"
    const val READER = "reader/{bookUuid}"
    const val TEXT_CORRECTIONS = "reader/{bookUuid}/corrections"
    fun diagnosticLogCategory(category: String) = "diagnostic_log/$category"
    fun reader(bookUuid: String) = "reader/$bookUuid"
    fun textCorrections(bookUuid: String) = "reader/$bookUuid/corrections"
}
