package com.johnny.wxdexinspector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.johnny.wxdexinspector.core.permission.PermissionManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.johnny.wxdexinspector.core.util.findActivity
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.core.util.toast
import com.johnny.wxdexinspector.data.model.NavStyle
import com.johnny.wxdexinspector.data.model.WorkMode
import com.johnny.wxdexinspector.ui.dialog.PermissionOverviewDialog
import com.johnny.wxdexinspector.data.model.ScanMode
import com.johnny.wxdexinspector.data.model.ThemeMode
import com.johnny.wxdexinspector.ui.components.OnResume
import com.johnny.wxdexinspector.ui.components.WxBottomBar
import com.johnny.wxdexinspector.ui.dialog.DisclaimerDialog
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import com.johnny.wxdexinspector.ui.screens.AboutScreen
import com.johnny.wxdexinspector.ui.screens.ApkDetailScreen
import com.johnny.wxdexinspector.ui.screens.BatchScreen
import com.johnny.wxdexinspector.ui.screens.DiffScreen
import com.johnny.wxdexinspector.ui.screens.ExtractScreen
import com.johnny.wxdexinspector.ui.screens.HistoryScreen
import com.johnny.wxdexinspector.ui.screens.HomeScreen
import com.johnny.wxdexinspector.ui.screens.LogScreen
import com.johnny.wxdexinspector.ui.screens.ScanScreen
import com.johnny.wxdexinspector.ui.screens.SettingsScreen
import com.johnny.wxdexinspector.ui.screens.SnapshotDetailScreen
import com.johnny.wxdexinspector.ui.theme.WxDexTheme
import com.johnny.wxdexinspector.ui.theme.isCurrentDark
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Route {
    data object Extract : Route
    data class Detail(val path: String, val source: String) : Route
    data class Scan(val path: String, val source: String, val mode: ScanMode) : Route
    data class SnapshotDetail(val path: String) : Route
    data class Diff(val initial: String?) : Route
    data object Batch : Route
    data object Logs : Route
}

@Composable
fun AppRoot() {
    val view = LocalView.current
    val context = LocalContext.current

    var themeMode by remember { mutableStateOf(AppPrefs.themeMode) }
    var navStyle by remember { mutableStateOf(AppPrefs.navStyle) }
    var glassAlpha by remember { mutableFloatStateOf(AppPrefs.glassAlpha) }
    var tab by remember { mutableIntStateOf(0) }

    var showDisclaimer by remember { mutableStateOf(!AppPrefs.disclaimerAccepted) }
    var statusDialog by remember { mutableStateOf(false) }
    var permText by remember { mutableStateOf("检测中…") }
    var routeStack = remember { mutableStateListOf<Route>() }

    var permTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // 切回前台 / 授权返回时实时刷新权限状态
    OnResume { permTick++ }

    LaunchedEffect(tab, permTick) {
        permText = withContext(Dispatchers.IO) {
            // 仅在「期望 Root 且尚未缓存授权」时真实探测一次（此时会弹出授权窗口）
            val wantRoot = AppPrefs.workMode == WorkMode.ROOT || AppPrefs.workMode == WorkMode.AUTO
            val probe = wantRoot && !AppPrefs.rootGranted && PermissionManager.isRootAvailable()
            val s = PermissionManager.snapshot(context, doRootProbe = probe)
            "当前工作模式：${s.effective.label}\n\n" +
                "Root：${s.root.label}\n" +
                "Shizuku：${s.shizuku.label}\n" +
                "文件存储权限：${s.storage.label}"
        }
    }

    // 首页/设置页切换工作模式后，立刻申请对应权限并刷新状态
    fun applyWorkMode(mode: WorkMode, onDone: () -> Unit) {
        scope.launch {
            when (mode) {
                WorkMode.ROOT -> {
                    if (PermissionManager.isRootAvailable()) {
                        context.toast("正在申请 Root 权限，请在授权窗口中点击「允许」…")
                        val ok = withContext(Dispatchers.IO) { PermissionManager.requestRoot() }
                        context.toast(if (ok) "✅ 已获得 Root 权限" else "未获得 Root 权限（可能被拒绝）")
                    } else {
                        context.toast("未检测到 su，当前设备可能未 Root")
                    }
                }
                WorkMode.SHIZUKU -> {
                    if (PermissionManager.isShizukuInstalled(context)) {
                        PermissionManager.requestShizukuPermission { ok ->
                            context.toast(if (ok) "Shizuku 已授权" else "Shizuku 授权失败或被拒绝")
                        }
                    } else {
                        context.toast("未检测到 Shizuku，请先安装并启动 Shizuku")
                    }
                }
                else -> Unit
            }
            onDone()
        }
    }

    WxDexTheme(themeMode) {
        val dark = isCurrentDark(themeMode)
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }

        val current = routeStack.lastOrNull()
        val backdrop = rememberLayerBackdrop()
        if (current == null) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = { }
            ) { padding ->
                Box(Modifier.fillMaxSize()) {
                    // 内容层：作为液态玻璃的采样背景（允许内容滑到导航栏下方）
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                    when (tab) {
                        0 -> HomeScreen(
                            modeLabel = permText.lineSequence().firstOrNull()?.removePrefix("当前工作模式：") ?: "自动检测",
                            onOpenStatus = { statusDialog = true },
                            onOpenMode = { mode ->
                                when (mode) {
                                    ScanMode.SINGLE -> routeStack.add(Route.Extract)
                                    ScanMode.DIFF -> routeStack.add(Route.Diff(null))
                                    ScanMode.BATCH -> routeStack.add(Route.Batch)
                                    ScanMode.SNAPSHOT_DIFF -> routeStack.add(Route.Diff(null))
                                    ScanMode.SNAPSHOT_BROWSE -> tab = 1
                                }
                            },
                            onOpenExtract = { routeStack.add(Route.Extract) },
                            onOpenLogs = { routeStack.add(Route.Logs) }
                        )
                        1 -> HistoryScreen(
                            onOpenSnapshot = { routeStack.add(Route.SnapshotDetail(it)) },
                            onOpenDiff = { routeStack.add(Route.Diff(null)) },
                            onOpenLogs = { routeStack.add(Route.Logs) }
                        )
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
                    WxBottomBar(
                        style = navStyle,
                        alpha = glassAlpha,
                        current = tab,
                        onSelect = { tab = it },
                        backdrop = backdrop,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = padding.calculateBottomPadding())
                    )
                }
            }
        } else {
            BackHandler { routeStack.removeAt(routeStack.lastIndex) }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    when (current) {
                        is Route.Extract -> ExtractScreen(
                            onBack = { routeStack.removeAt(routeStack.lastIndex) },
                            onOpenDetail = { p, s -> routeStack.add(Route.Detail(p, s)) },
                            onScan = { p, s -> routeStack.add(Route.Scan(p, s, ScanMode.SINGLE)) }
                        )
                        is Route.Detail -> ApkDetailScreen(
                            apkPath = current.path,
                            source = current.source,
                            onBack = { routeStack.removeAt(routeStack.lastIndex) },
                            onScan = { p, s -> routeStack.add(Route.Scan(p, s, ScanMode.SINGLE)) }
                        )
                        is Route.Scan -> ScanScreen(
                            apkPath = current.path,
                            source = current.source,
                            scanMode = current.mode,
                            onBack = { routeStack.removeAt(routeStack.lastIndex) }
                        )
                        is Route.SnapshotDetail -> SnapshotDetailScreen(
                            filePath = current.path,
                            onBack = { routeStack.removeAt(routeStack.lastIndex) },
                            onDiff = { routeStack.add(Route.Diff(it)) }
                        )
                        is Route.Diff -> DiffScreen(
                            initialFile = current.initial,
                            onBack = { routeStack.removeAt(routeStack.lastIndex) }
                        )
                        is Route.Batch -> BatchScreen(
                            onBack = { routeStack.removeAt(routeStack.lastIndex) }
                        )
                        is Route.Logs -> LogScreen(onBack = { routeStack.removeAt(routeStack.lastIndex) })
                    }
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
            PermissionOverviewDialog(
                onDismiss = { statusDialog = false },
                showModeSelector = true,
                onModeChange = { mode -> applyWorkMode(mode) { permTick++ } }
            )
        }
    }
}
