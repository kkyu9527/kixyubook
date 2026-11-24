package com.kixyu9527.kixyubook.core.common.model

/**
 * Reading colours. [DEFAULT] is the calm paper palette; [APP] follows the application's
 * Material colour scheme (including dynamic colour and the colour theme selected in Appearance);
 * [DYNAMIC] follows the wallpaper directly; the named schemes are built-in reading presets.
 */
enum class ReaderColorScheme {
    DEFAULT,
    APP,
    DYNAMIC,
    WHITE,
    PAPER,
    SEPIA,
    GREEN,
    GRAY,
    SAGE,
    OCEAN,
    VIOLET,
    AMBER,
    CUSTOM,
}

fun restoreReaderColorScheme(stored: String?, legacyCustom: Boolean = false): ReaderColorScheme =
    ReaderColorScheme.entries.firstOrNull { it.name == stored }
        ?: if (legacyCustom) ReaderColorScheme.CUSTOM else ReaderColorScheme.DEFAULT
