package com.kixyu9527.kixyubook.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kixyu9527.kixyubook.core.common.model.AppColorTheme
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.DEFAULT_GLASS_FROST_LEVEL
import com.kixyu9527.kixyubook.core.common.model.MAX_GLASS_FROST_LEVEL
import com.kixyu9527.kixyubook.core.common.model.MIN_GLASS_FROST_LEVEL
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuGlassStyle
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.GlassDimensions
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.LocalGlassDimensions
import io.github.neoruaa.glasmio.core.defaultGlassColors
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private data class ThemeSeed(
    val lightPrimary: Color,
    val lightContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
)

private val seeds = mapOf(
    AppColorTheme.SAGE to ThemeSeed(Color(0xFF3F5A4B), Color(0xFFD6E5DA), Color(0xFFB8CCBD), Color(0xFF354A3B)),
    AppColorTheme.OCEAN to ThemeSeed(Color(0xFF3D5B6D), Color(0xFFD2E5ED), Color(0xFFAFCADD), Color(0xFF2D4757)),
    AppColorTheme.VIOLET to ThemeSeed(Color(0xFF5A5168), Color(0xFFE5DEED), Color(0xFFCDBEE0), Color(0xFF463D54)),
    AppColorTheme.AMBER to ThemeSeed(Color(0xFF6B5435), Color(0xFFEFE0C4), Color(0xFFE1C08C), Color(0xFF4C3A22)),
)

/**
 * Optional Quiet Editorial palettes: warm paper surfaces, ink text and a low-saturation accent.
 * These are opt-in color themes; [AppColorTheme.DEFAULT] keeps the component libraries' official
 * baseline palettes so Material and MIUIX look like their stock design systems first.
 */
internal fun lightColors(theme: AppColorTheme) = (seeds[theme] ?: seeds.getValue(AppColorTheme.SAGE)).let { seed ->
    lightColorScheme(
        primary = seed.lightPrimary,
        onPrimary = Color.White,
        primaryContainer = seed.lightContainer,
        onPrimaryContainer = Color(0xFF14251B),
        secondary = Color(0xFF6C6252),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEFE2CE),
        onSecondaryContainer = Color(0xFF2A2115),
        tertiary = Color(0xFF8A6A3B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF0E1C6),
        onTertiaryContainer = Color(0xFF2B1F0E),
        background = Color(0xFFF7F3E9),
        onBackground = Color(0xFF1C201B),
        surface = Color(0xFFFCF9F2),
        onSurface = Color(0xFF1C201B),
        surfaceVariant = Color(0xFFE7E1D4),
        onSurfaceVariant = Color(0xFF556055),
        surfaceContainerLowest = Color(0xFFFFFDF8),
        surfaceContainerLow = Color(0xFFECE5D6),
        surfaceContainer = Color(0xFFE3DBC9),
        surfaceContainerHigh = Color(0xFFDED5C1),
        surfaceContainerHighest = Color(0xFFD6CCB6),
        outline = Color(0xFF788176),
        outlineVariant = Color(0xFFCFC7B6),
        inverseSurface = Color(0xFF2F332D),
        inverseOnSurface = Color(0xFFF1F1E9),
        inversePrimary = Color(0xFFB8CCBD),
        scrim = Color.Black,
    )
}

internal fun darkColors(theme: AppColorTheme) = (seeds[theme] ?: seeds.getValue(AppColorTheme.SAGE)).let { seed ->
    darkColorScheme(
        primary = seed.darkPrimary,
        onPrimary = Color(0xFF233529),
        primaryContainer = seed.darkContainer,
        onPrimaryContainer = Color(0xFFD3E7D8),
        secondary = Color(0xFFCBC2B2),
        onSecondary = Color(0xFF332F26),
        secondaryContainer = Color(0xFF4A4134),
        onSecondaryContainer = Color(0xFFE8DFCE),
        tertiary = Color(0xFFE4C08D),
        onTertiary = Color(0xFF3F2D11),
        tertiaryContainer = Color(0xFF5A431F),
        onTertiaryContainer = Color(0xFFFFDFA8),
        background = Color(0xFF10130F),
        onBackground = Color(0xFFE6E9E1),
        surface = Color(0xFF161A15),
        onSurface = Color(0xFFE6E9E1),
        surfaceVariant = Color(0xFF2A3028),
        onSurfaceVariant = Color(0xFFC1C8BC),
        surfaceContainerLowest = Color(0xFF0C0F0B),
        surfaceContainerLow = Color(0xFF20261E),
        surfaceContainer = Color(0xFF252C24),
        surfaceContainerHigh = Color(0xFF2B332A),
        surfaceContainerHighest = Color(0xFF333B31),
        outline = Color(0xFF8C9487),
        outlineVariant = Color(0xFF3E463C),
        inverseSurface = Color(0xFFE6E9E1),
        inverseOnSurface = Color(0xFF2F332D),
        inversePrimary = Color(0xFF3F5A4B),
        scrim = Color.Black,
    )
}

private fun whiteColors() = lightColorScheme(
    primary = Color(0xFF3367D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF526071),
    background = Color.White,
    surface = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    onSurface = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC7C7D0),
)

private val KixyuTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

// One material language, semantic structure: Material's shape slots follow the purpose of the
// surface (small controls, fields, cards, sheets) instead of every control sharing one radius.
// Material and MIUIX resolve to the same roles so the two styles stay interchangeable.
private val KixyuShapes = Shapes(
    extraSmall = RoundedCornerShape(KixyuSize.controlCornerRadius),
    small = RoundedCornerShape(KixyuSize.fieldCornerRadius),
    medium = RoundedCornerShape(KixyuSize.cardCornerRadius),
    large = RoundedCornerShape(KixyuSize.sheetCornerRadius),
    extraLarge = RoundedCornerShape(KixyuSize.sheetCornerRadius),
)

val LocalAppUiStyle = staticCompositionLocalOf { AppUiStyle.MATERIAL }
val LocalKixyuGlassEffectEnabled = staticCompositionLocalOf { true }
val LocalKixyuPredictiveBackEnabled = staticCompositionLocalOf { false }

/** The opaque window background shared by navigation and page scaffolds. */
@Composable
fun kixyuPageBackground(): Color = if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
    MiuixTheme.colorScheme.surface
} else {
    MaterialTheme.colorScheme.background
}

/**
 * Material components are still used for a few controls inside MIUIX pages.
 * Bridge MIUIX semantic colors into MaterialTheme so those controls follow the
 * active MIUIX palette instead of leaking Material's default purple tokens.
 */
@Composable
private fun miuixMaterialColorScheme(base: ColorScheme): ColorScheme {
    val miuix = MiuixTheme.colorScheme
    return base.copy(
        primary = miuix.primary,
        onPrimary = miuix.onPrimary,
        primaryContainer = miuix.primaryContainer,
        onPrimaryContainer = miuix.onPrimaryContainer,
        secondary = miuix.secondary,
        onSecondary = miuix.onSecondary,
        secondaryContainer = miuix.secondaryContainer,
        onSecondaryContainer = miuix.onSecondaryContainer,
        tertiary = miuix.primaryVariant,
        onTertiary = miuix.onPrimaryVariant,
        tertiaryContainer = miuix.tertiaryContainer,
        onTertiaryContainer = miuix.onTertiaryContainer,
        error = miuix.error,
        onError = miuix.onError,
        errorContainer = miuix.errorContainer,
        onErrorContainer = miuix.onErrorContainer,
        background = miuix.background,
        onBackground = miuix.onBackground,
        surface = miuix.surface,
        onSurface = miuix.onSurface,
        surfaceVariant = miuix.surfaceVariant,
        onSurfaceVariant = miuix.onSurfaceSecondary,
        outline = miuix.outline,
        outlineVariant = miuix.dividerLine,
        surfaceTint = miuix.primary,
        surfaceContainerLowest = miuix.surfaceContainer,
        surfaceContainerLow = miuix.surfaceContainer,
        surfaceContainer = miuix.surfaceContainer,
        surfaceContainerHigh = miuix.surfaceContainerHigh,
        surfaceContainerHighest = miuix.surfaceContainerHighest,
    )
}

@Composable
fun KixyuBookTheme(
    themeMode: ReaderTheme = ReaderTheme.SYSTEM,
    colorTheme: AppColorTheme = AppColorTheme.DEFAULT,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL,
    glassEffectEnabled: Boolean = true,
    glassFrostLevel: Float = DEFAULT_GLASS_FROST_LEVEL,
    predictiveBackEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ReaderTheme.DAY -> false
        ReaderTheme.NIGHT -> true
        ReaderTheme.SYSTEM -> systemDark
    }
    val context = LocalContext.current
    val colors = when {
        colorTheme == AppColorTheme.DEFAULT -> {
            // Stock Material baseline: this is what a plain MaterialTheme would render.
            if (darkTheme) darkColorScheme() else lightColorScheme()
        }
        colorTheme == AppColorTheme.WHITE -> {
            if (darkTheme) darkColorScheme() else whiteColors()
        }
        colorTheme == AppColorTheme.DYNAMIC -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColors(colorTheme)
        else -> lightColors(colorTheme)
    }
    val glassColors = remember(darkTheme, colors.primary) {
        defaultGlassColors(isDark = darkTheme, accent = colors.primary).let { base ->
            // Apple's bars are much clearer than the default 64% container. Keep the native
            // material/refraction, but lower the opaque fill to roughly 30%.
            base.copy(
                container = KixyuGlassStyle.fallbackTint(darkTheme),
            )
        }
    }
    val glassContent: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalGlassColors provides glassColors,
            LocalGlassAppearance provides GlassAppearance(
                liquidGlassEnabled = glassEffectEnabled,
                reduceTransparency = !glassEffectEnabled,
            ),
            LocalGlassDimensions provides GlassDimensions(),
        ) {
            content()
        }
    }
    val themedContent: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalAppUiStyle provides uiStyle,
            LocalKixyuGlassEffectEnabled provides glassEffectEnabled,
            LocalKixyuPredictiveBackEnabled provides predictiveBackEnabled,
        ) {
            if (uiStyle == AppUiStyle.MIUIX) {
                val miuixMode = when (colorTheme) {
                    // Keep MIUIX's own white/blue baseline instead of inheriting the wallpaper
                    // Monet palette or the Material baseline purple.
                    AppColorTheme.DEFAULT,
                    AppColorTheme.WHITE,
                    -> if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light

                    else -> if (darkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight
                }
                val keyColor = colors.primary.takeIf {
                    colorTheme != AppColorTheme.DEFAULT &&
                        colorTheme != AppColorTheme.WHITE &&
                        colorTheme != AppColorTheme.DYNAMIC
                }
                val controller = remember(miuixMode, keyColor) {
                    ThemeController(colorSchemeMode = miuixMode, keyColor = keyColor)
                }
                MiuixTheme(controller = controller) {
                    MaterialTheme(
                        colorScheme = miuixMaterialColorScheme(colors),
                        typography = KixyuTypography,
                        shapes = KixyuShapes,
                        content = glassContent,
                    )
                }
            } else {
                glassContent()
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = KixyuTypography,
        shapes = KixyuShapes,
        content = themedContent,
    )
}
