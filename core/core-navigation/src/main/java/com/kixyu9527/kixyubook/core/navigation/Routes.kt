package com.kixyu9527.kixyubook.core.navigation

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"
    const val READING_SETTINGS = "reading_settings"
    const val CLOUD_SYNC = "cloud_sync"
    const val DATA_AND_BACKUP = "data_and_backup"
    const val ABOUT = "about"
    const val DIAGNOSTIC_LOG = "diagnostic_log"
    const val DIAGNOSTIC_LOG_CATEGORY = "diagnostic_log/{category}"
    const val READER = "reader/{bookUuid}"
    fun diagnosticLogCategory(category: String) = "diagnostic_log/$category"
    fun reader(bookUuid: String) = "reader/$bookUuid"
}
