package com.sayaem.nebula.ui.theme

import androidx.compose.ui.graphics.Color


// ── Brand ──────────────────────────────────────────────────────────
val NebulaViolet     = Color(0xFF7B6EFF)
val NebulaVioletLight= Color(0xFFA99AFF)
val NebulaVioletDark = Color(0xFF5547D4)
val NebulaPink       = Color(0xFFFF6B9D)
val NebulaPinkLight  = Color(0xFFFF9DBE)
val NebulaCyan       = Color(0xFF00D9FF)
val NebulaAmber      = Color(0xFFFFAB2E)
val NebulaGreen      = Color(0xFF00E5A0)
val NebulaRed        = Color(0xFFFF4F6E)

// ── Dark surfaces ──────────────────────────────────────────────────
val DarkBg           = Color(0xFF080810)
val DarkBgSecondary  = Color(0xFF0F0F1C)
val DarkSurface      = Color(0xFF14141F)
val DarkSurface2     = Color(0xFF1A1A2A)
val DarkCard         = Color(0xFF1E1E30)
val DarkCardHover    = Color(0xFF252540)
val DarkBorder       = Color(0xFF2A2A45)
val DarkBorderSubtle = Color(0xFF1E1E35)

// ── Light surfaces ──────────────────────────────────────────────────
val LightBg          = Color(0xFFF8F7FF)
val LightSurface     = Color(0xFFFFFFFF)
val LightCard        = Color(0xFFFFFFFF)
val LightBorder      = Color(0xFFE4E0FF)

// ── Text ──────────────────────────────────────────────────────────
val TextPrimaryDark   = Color(0xFFF0EFFF)
val TextSecondaryDark = Color(0xFF9D9DBF)
val TextTertiaryDark  = Color(0xFF5A5A80)

val TextPrimaryLight   = Color(0xFF0A091F)
val TextSecondaryLight = Color(0xFF5A5870)
val TextTertiaryLight  = Color(0xFF8A88A0)

// ── Visualizer bars ────────────────────────────────────────────────
val VisualizerColors = listOf(
    Color(0xFF7B6EFF), Color(0xFFAA6EFF),
    Color(0xFFFF6B9D), Color(0xFFFF8B6E),
    Color(0xFF00D9FF), Color(0xFF00E5A0)
)

// ── Theme-aware color provider ────────────────────────────────────
data class AppColors(
    val bg: Color,
    val bgSecondary: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
)

val DarkAppColors = AppColors(
    bg           = DarkBg,
    bgSecondary  = DarkBgSecondary,
    surface      = DarkSurface,
    card         = DarkCard,
    border       = DarkBorder,
    borderSubtle = DarkBorderSubtle,
    textPrimary  = TextPrimaryDark,
    textSecondary= TextSecondaryDark,
    textTertiary = TextTertiaryDark,
)

val LightAppColors = AppColors(
    bg           = LightBg,
    bgSecondary  = Color(0xFFEEECFF),
    surface      = LightSurface,
    card         = LightCard,
    border       = LightBorder,
    borderSubtle = Color(0xFFF0EEFF),
    textPrimary  = TextPrimaryLight,
    textSecondary= TextSecondaryLight,
    textTertiary = TextTertiaryLight,
)

val LocalAppColors = androidx.compose.runtime.staticCompositionLocalOf { DarkAppColors }
