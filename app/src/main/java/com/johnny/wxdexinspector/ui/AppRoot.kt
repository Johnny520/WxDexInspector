package com.johnny.wxdexinspector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.johnny.wxdexinspector.core.util.findActivity
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.data.model.NavStyle
import com.johnny.wxdexinspector.data.model.ThemeMode
import com.johnny.wxdexinspector.ui.components.WxBottomBar
import com.johnny.wxdexinspector.ui.dialog.DisclaimerDialog
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import com.johnny.wxdexinspector.ui.screens.AboutScreen
import com.johnny.wxdexinspector.ui.screens.HistoryScreen
import com.johnny.wxdexinspector.ui.screens.HomeScreen
import com.johnny.wxdexinspector.ui.screens.SettingsScreen
import com.johnny.wxdexinspector.ui.theme.WxDexTheme
import com.johnny.wxdexinspector.ui.theme.isCurrentDark

@Composable
fun AppRoot() {
    val view = LocalView.current

    var themeMode by remember { mutableStateOf(AppPrefs.themeMode) }
    var navStyle by remember { mutableStateOf(AppPrefs.navStyle) }
    var glassAlpha by remember { mutableFloatStateOf(AppPrefs.glassAlpha) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    var showDisclaimer by remember { mutableStateOf(!AppPrefs.disclaimerAccepted) }
    var statusDialog by remember { mutableStateOf(false) }

    WxDexTheme(themeMode) {
        val dark = isCurrentDark(themeMode)
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                WxBottomBar(
                    style = navStyle,
                    alpha = glassAlpha,
                    current = tab,
                    onSelect = { tab = it }
                )
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (tab) {
                    0 -> HomeScreen(
                        onOpenStatus = { statusDialog = true },
                        onOpenMode = { }
                    )
                    1 -> HistoryScreen()
                    2 -> SettingsScreen(
                        themeMode = themeMode,
                        onThemeMode = { themeMode = it; AppPrefs.themeMode = it },
                        navStyle = navStyle,
                        onNavStyle = { navStyle = it; AppPrefs.navStyle = it },
                        glassAlpha = glassAlpha,
                        onGlassAlpha = { glassAlpha = it; AppPrefs.glassAlpha = it },
                        onOpenStatus = { statusDialog = true }
                    )
                    else -> AboutScreen()
                }
            }
        }

        if (showDisclaimer) {
            DisclaimerDialog(
                onAccept = {
                    AppPrefs.disclaimerAccepted = true
                    showDisclaimer = false
                }
            )
        }

        if (statusDialog) {
            InfoDialog(
                title = "权限状态",
                message = "当前工作模式：自动检测\n\n（详细权限检测将在后续版本接入）",
                onClose = { statusDialog = false }
            )
        }
    }
}
