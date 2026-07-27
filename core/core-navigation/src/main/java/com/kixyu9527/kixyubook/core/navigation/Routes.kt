package com.kixyu9527.kixyubook.core.navigation

object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val READER = "reader/{bookId}"

    fun reader(bookId: Long) = "reader/$bookId"
}
