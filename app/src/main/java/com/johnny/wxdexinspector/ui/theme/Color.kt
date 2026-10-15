package com.johnny.wxdexinspector.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ===== 品牌色 =====
val WxBlue = Color(0xFF3B6EF6)
val WxBlueLight = Color(0xFF6E96FF)
val WxBlueContainerLight = Color(0xFFDDE6FF)
val WxBlueContainerDark = Color(0xFF1B3A78)

val WxTeal = Color(0xFF12B5A6)
val WxAmber = Color(0xFFF5A524)
val WxRed = Color(0xFFE5484D)
val WxGreen = Color(0xFF2FBF71)

// ===== 默认亮色 =====
val LightColors = lightColorScheme(
    primary = WxBlue,
    onPrimary = Color.White,
    primaryContainer = WxBlueContainerLight,
    onPrimaryContainer = Color(0xFF001A44),
    secondary = Color(0xFF5A6172),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE2EC),
    onSecondaryContainer = Color(0xFF171C29),
    tertiary = WxTeal,
    onTertiary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainer = Color(0xFFF1F3F6),
    surfaceContainerHigh = Color(0xFFEAECF1),
    outline = Color(0xFFC3C6CF),
    outlineVariant = Color(0xFFE1E3E8),
    error = WxRed,
    onError = Color.White
)

// ===== 默认暗色 =====
val DarkColors = darkColorScheme(
    primary = WxBlueLight,
    onPrimary = Color(0xFF001A44),
    primaryContainer = WxBlueContainerDark,
    onPrimaryContainer = Color(0xFFDDE6FF),
    secondary = Color(0xFFBFC6D6),
    onSecondary = Color(0xFF293040),
    secondaryContainer = Color(0xFF3F4756),
    onSecondaryContainer = Color(0xFFDEE2EC),
    tertiary = Color(0xFF63D6C9),
    onTertiary = Color(0xFF00382F),
    background = Color(0xFF101114),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF16181C),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF262A31),
    onSurfaceVariant = Color(0xFFC4C7CE),
    surfaceContainer = Color(0xFF1C1F24),
    surfaceContainerHigh = Color(0xFF23272E),
    outline = Color(0xFF4A4E57),
    outlineVariant = Color(0xFF2E323A),
    error = Color(0xFFFF6B6F),
    onError = Color(0xFF3A0004)
)

// ===== MIUI 亮色 =====
val MiuiLight = lightColorScheme(
    primary = Color(0xFF3482FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FF),
    onPrimaryContainer = Color(0xFF00214D),
    secondary = Color(0xFF5C6470),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E3EA),
    onSecondaryContainer = Color(0xFF191C22),
    tertiary = Color(0xFF0FB5A0),
    onTertiary = Color.White,
    background = Color(0xFFF2F3F5),
    onBackground = Color(0xFF14161A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14161A),
    surfaceVariant = Color(0xFFEBECEF),
    onSurfaceVariant = Color(0xFF42464E),
    surfaceContainer = Color(0xFFF6F7F9),
    surfaceContainerHigh = Color(0xFFEEF0F3),
    outline = Color(0xFFBFC3CB),
    outlineVariant = Color(0xFFE0E2E7),
    error = Color(0xFFE5484D),
    onError = Color.White
)

// ===== MIUI 暗色 =====
val MiuiDark = darkColorScheme(
    primary = Color(0xFF6EA8FF),
    onPrimary = Color(0xFF00214D),
    primaryContainer = Color(0xFF24466F),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFFBCC2CC),
    onSecondary = Color(0xFF2A2E36),
    secondaryContainer = Color(0xFF3B414B),
    onSecondaryContainer = Color(0xFFE1E3EA),
    tertiary = Color(0xFF5FD8C6),
    onTertiary = Color(0xFF003730),
    background = Color(0xFF121317),
    onBackground = Color(0xFFE5E6EA),
    surface = Color(0xFF181A1F),
    onSurface = Color(0xFFE5E6EA),
    surfaceVariant = Color(0xFF262A31),
    onSurfaceVariant = Color(0xFFC2C6CE),
    surfaceContainer = Color(0xFF1E2026),
    surfaceContainerHigh = Color(0xFF242830),
    outline = Color(0xFF484C55),
    outlineVariant = Color(0xFF2E323A),
    error = Color(0xFFFF6B6F),
    onError = Color(0xFF3A0004)
)

// 日志级别色
val LogInfoColor = Color(0xFF4C8DFF)
val LogWarnColor = Color(0xFFF5A524)
val LogErrorColor = Color(0xFFE5484D)
val LogDebugColor = Color(0xFF8A8F99)
