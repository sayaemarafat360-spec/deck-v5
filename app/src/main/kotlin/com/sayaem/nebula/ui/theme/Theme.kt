package com.sayaem.nebula.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


// ── Typography ───────────────────────────────────────────────────────
val NebulaTypography = Typography(
    displayLarge  = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold,   letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold,   letterSpacing = (-1.0).sp),
    displaySmall  = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold,   letterSpacing = (-0.3).sp),
    headlineMedium= TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold,   letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleLarge    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleMedium   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    titleSmall    = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge     = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 25.sp),
    bodyMedium    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodySmall     = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelSmall    = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
)

// ── Dark colour scheme ───────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary           = NebulaViolet,
    onPrimary         = TextPrimaryDark,
    primaryContainer  = DarkCard,
    secondary         = NebulaPink,
    onSecondary       = TextPrimaryDark,
    tertiary          = NebulaCyan,
    background        = DarkBg,
    surface           = DarkSurface,
    surfaceVariant    = DarkCard,
    onBackground      = TextPrimaryDark,
    onSurface         = TextPrimaryDark,
    onSurfaceVariant  = TextSecondaryDark,
    outline           = DarkBorder,
    error             = NebulaRed,
)

// ── Light colour scheme ──────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary           = NebulaViolet,
    onPrimary         = LightSurface,
    primaryContainer  = LightCard,
    secondary         = NebulaPink,
    tertiary          = NebulaCyan,
    background        = LightBg,
    surface           = LightSurface,
    surfaceVariant    = LightCard,
    onBackground      = TextPrimaryLight,
    onSurface         = TextPrimaryLight,
    onSurfaceVariant  = TextSecondaryLight,
    outline           = LightBorder,
    error             = NebulaRed,
)

@Composable
fun DeckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicAccentColor: androidx.compose.ui.graphics.Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors   = if (darkTheme) DarkAppColors else LightAppColors
    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = NebulaTypography,
            content     = content,
        )
    }
}
