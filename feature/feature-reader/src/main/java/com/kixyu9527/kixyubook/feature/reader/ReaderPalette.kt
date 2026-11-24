package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt
import com.kixyu9527.kixyubook.core.common.model.ReaderColorScheme
import com.kixyu9527.kixyubook.core.common.model.ReaderSettings
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.reader.engine.ReaderRenderPalette

/** Reading, the opening surface and browser EPUB all consume exactly the same palette. */
@Composable
internal fun rememberReaderPalette(settings: ReaderSettings): ReaderRenderPalette {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    // Wallpaper changes update configuration; don't freeze a dynamic scheme for this session.
    val configuration = LocalConfiguration.current
    val dark = settings.usesNightColors(systemDark)
    val dynamic = remember(context, configuration, dark, settings.readerColorScheme) {
        if (settings.readerColorScheme == ReaderColorScheme.DYNAMIC) {
            val colors = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            ReaderRenderPalette(colors.surface, colors.onSurface, colors.onSurface, colors.primary, colors.onSurfaceVariant)
        } else null
    }
    return remember(settings.readerColorScheme, settings.customDayTheme, settings.customNightTheme, dark, dynamic) {
        readerPalette(settings, systemDark, dynamic)
    }
}

private fun ReaderSettings.usesNightColors(systemDark: Boolean) = when (theme) {
    ReaderTheme.SYSTEM -> systemDark
    ReaderTheme.DAY -> false
    ReaderTheme.NIGHT -> true
}

internal fun readerPalette(
    settings: ReaderSettings,
    systemDark: Boolean,
    dynamic: ReaderRenderPalette? = null,
): ReaderRenderPalette {
    val night = settings.usesNightColors(systemDark)
    val fallback = if (night) {
        palette(0xFF000000, 0xFFD9D9D0, 0xFFF0F0E7, 0xFFB8CCBD, 0xFF92948B)
    } else {
        palette(0xFFFAF8F2, 0xFF282620, 0xFF171713, 0xFF52655A, 0xFF716F67)
    }
    return when (settings.readerColorScheme) {
        ReaderColorScheme.DEFAULT -> fallback
        ReaderColorScheme.DYNAMIC -> dynamic ?: fallback
        ReaderColorScheme.PAPER -> if (night) {
            palette(0xFF191814, 0xFFDCD8CC, 0xFFF1EDDF, 0xFFC5B991, 0xFFAAA598)
        } else palette(0xFFF5F1E8, 0xFF302D27, 0xFF201E19, 0xFF685B3E, 0xFF686256)
        ReaderColorScheme.SEPIA -> if (night) {
            palette(0xFF211A14, 0xFFE2D2BB, 0xFFF2E3CF, 0xFFD7B58A, 0xFFB7A58C)
        } else palette(0xFFF2E4C8, 0xFF443526, 0xFF302419, 0xFF775332, 0xFF776249)
        ReaderColorScheme.GREEN -> if (night) {
            palette(0xFF142019, 0xFFCDDFD0, 0xFFE1F0E3, 0xFFA1C8AE, 0xFF9EB4A3)
        } else palette(0xFFDCE9D5, 0xFF263C2B, 0xFF1A2D1F, 0xFF3E6248, 0xFF52664F)
        ReaderColorScheme.GRAY -> if (night) {
            palette(0xFF1C1C1E, 0xFFD7D7DA, 0xFFF0F0F2, 0xFFB7C6E0, 0xFFAAAAAF)
        } else palette(0xFFE6E6E4, 0xFF2D2D30, 0xFF1C1C1F, 0xFF4B5F7A, 0xFF616165)
        ReaderColorScheme.CUSTOM -> {
            val custom = if (night) settings.customNightTheme else settings.customDayTheme
            ReaderRenderPalette(
                custom.backgroundHex.colorOr(fallback.background), custom.bodyHex.colorOr(fallback.body),
                custom.titleHex.colorOr(fallback.title), custom.accentHex.colorOr(fallback.accent),
                custom.bodyHex.colorOr(fallback.secondary).copy(alpha = .62f),
            )
        }
    }
}

private fun palette(background: Long, body: Long, title: Long, accent: Long, secondary: Long) =
    ReaderRenderPalette(Color(background), Color(body), Color(title), Color(accent), Color(secondary))

internal fun String.colorOr(fallback: Color) = runCatching { Color(toColorInt()) }.getOrDefault(fallback)
