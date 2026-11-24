package com.kixyu9527.kixyubook.core.common.model

/** Reading colours are independent of application chrome and of light/dark mode. */
enum class ReaderColorScheme { DEFAULT, DYNAMIC, PAPER, SEPIA, GREEN, GRAY, CUSTOM }

fun restoreReaderColorScheme(stored: String?, legacyCustom: Boolean = false): ReaderColorScheme =
    ReaderColorScheme.entries.firstOrNull { it.name == stored }
        ?: if (legacyCustom) ReaderColorScheme.CUSTOM else ReaderColorScheme.DEFAULT
