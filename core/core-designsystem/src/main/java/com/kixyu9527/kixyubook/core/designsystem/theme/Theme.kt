package com.kixyu9527.kixyubook.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kixyu9527.kixyubook.core.common.model.AppColorTheme
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme

private data class ThemeSeed(
    val lightPrimary: Color,
    val lightContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
)

private val seeds = mapOf(
    AppColorTheme.SAGE to ThemeSeed(Color(0xFF52655A), Color(0xFFDCE8DF), Color(0xFFB8CCBD), Color(0xFF3A4D40)),
    AppColorTheme.OCEAN to ThemeSeed(Color(0xFF496477), Color(0xFFD4E5F2), Color(0xFFB3CCDE), Color(0xFF314958)),
    AppColorTheme.VIOLET to ThemeSeed(Color(0xFF665D78), Color(0xFFE8DEF2), Color(0xFFCEC2E1), Color(0xFF4C435C)),
    AppColorTheme.AMBER to ThemeSeed(Color(0xFF775D38), Color(0xFFF3E1C3), Color(0xFFE5C38F), Color(0xFF574426)),
)

private fun lightColors(theme: AppColorTheme) = (seeds[theme] ?: seeds.getValue(AppColorTheme.SAGE)).let { seed ->
    lightColorScheme(
        primary = seed.lightPrimary,
        onPrimary = Color.White,
        primaryContainer = seed.lightContainer,
        onPrimaryContainer = Color(0xFF18201B),
        secondary = Color(0xFF66645E),
        background = Color(0xFFFAF9F5),
        surface = Color(0xFFFAF9F5),
        surfaceContainer = Color(0xFFF0EFEB),
        surfaceContainerLow = Color(0xFFF6F5F1),
        surfaceContainerHigh = Color(0xFFE9E8E4),
        onSurface = Color(0xFF20211E),
        onSurfaceVariant = Color(0xFF656661),
        outline = Color(0xFF858680),
        outlineVariant = Color(0xFFDEDFD9),
    )
}

private fun darkColors(theme: AppColorTheme) = (seeds[theme] ?: seeds.getValue(AppColorTheme.SAGE)).let { seed ->
    darkColorScheme(
        primary = seed.darkPrimary,
        onPrimary = Color(0xFF203027),
        primaryContainer = seed.darkContainer,
        onPrimaryContainer = Color(0xFFE1E9E3),
        secondary = Color(0xFFC9C5BC),
        background = Color(0xFF121310),
        surface = Color(0xFF121310),
        surfaceContainer = Color(0xFF1C1E1A),
        surfaceContainerLow = Color(0xFF181A17),
        surfaceContainerHigh = Color(0xFF272925),
        onSurface = Color(0xFFE7E8E1),
        onSurfaceVariant = Color(0xFFC5C6BF),
        outline = Color(0xFF8F918A),
        outlineVariant = Color(0xFF41433E),
    )
}

private val KixyuTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 39.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val KixyuShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun KixyuBookTheme(
    themeMode: ReaderTheme = ReaderTheme.SYSTEM,
    colorTheme: AppColorTheme = AppColorTheme.DYNAMIC,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ReaderTheme.DAY -> false
        ReaderTheme.NIGHT -> true
        ReaderTheme.SYSTEM, ReaderTheme.CUSTOM -> systemDark
    }
    val context = LocalContext.current
    val colors = if (colorTheme == AppColorTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        darkColors(colorTheme)
    } else {
        lightColors(colorTheme)
    }
    MaterialTheme(
        colorScheme = colors,
        typography = KixyuTypography,
        shapes = KixyuShapes,
        content = content,
    )
}
