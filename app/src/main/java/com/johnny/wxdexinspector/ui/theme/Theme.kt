package com.johnny.wxdexinspector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.johnny.wxdexinspector.data.model.ThemeMode

/** 4 套全局主题统一入口，页面/弹窗自动适配 */
@Composable
fun WxDexTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.MIUI -> systemDark
    }
    val scheme = when {
        mode == ThemeMode.MIUI && dark -> MiuiDark
        mode == ThemeMode.MIUI -> MiuiLight
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun isCurrentDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.MIUI -> isSystemInDarkTheme()
}
