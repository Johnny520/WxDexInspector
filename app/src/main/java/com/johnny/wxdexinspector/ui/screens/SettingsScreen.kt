package com.johnny.wxdexinspector.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.johnny.wxdexinspector.BuildConfig
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.crash.CrashHandler
import com.johnny.wxdexinspector.core.permission.PermissionManager
import com.johnny.wxdexinspector.core.update.UpdateChecker
import com.johnny.wxdexinspector.core.util.openUrl
import com.johnny.wxdexinspector.core.util.toast
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.data.model.NavStyle
import com.johnny.wxdexinspector.data.model.PermState
import com.johnny.wxdexinspector.data.model.ThemeMode
import com.johnny.wxdexinspector.data.model.WorkMode
import com.johnny.wxdexinspector.ui.components.ActionRow
import com.johnny.wxdexinspector.ui.components.CheckUpdateActionRow
import com.johnny.wxdexinspector.ui.components.OnResume
import com.johnny.wxdexinspector.ui.components.RadioRow
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.components.SettingRow
import com.johnny.wxdexinspector.ui.components.SwitchRow
import com.johnny.wxdexinspector.ui.dialog.DISCLAIMER_TEXT
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import com.johnny.wxdexinspector.ui.dialog.PermissionOverviewDialog
import com.johnny.wxdexinspector.ui.dialog.WxAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    navStyle: NavStyle,
    onNavStyle: (NavStyle) -> Unit,
    glassAlpha: Float,
    onGlassAlpha: (Float) -> Unit,
    onOpenStatus: () -> Unit,
    onOpenPluginStore: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var workMode by remember { mutableStateOf(AppPrefs.workMode) }
    var visualReport by remember { mutableStateOf(AppPrefs.visualReport) }
    var extMaster by remember { mutableStateOf(AppPrefs.extMaster) }
    var extHardening by remember { mutableStateOf(AppPrefs.extHardening) }
    var extSdk by remember { mutableStateOf(AppPrefs.extSdk) }
    var extComponent by remember { mutableStateOf(AppPrefs.extComponent) }
    var extPreview by remember { mutableStateOf(AppPrefs.extPreview) }
    var extArsc by remember { mutableStateOf(AppPrefs.extArsc) }
    var autoClean by remember { mutableStateOf(AppPrefs.autoCleanTemp) }
    var backupLog by remember { mutableStateOf(AppPrefs.backupLog) }

    var rootState by remember { mutableStateOf(PermState.UNKNOWN) }
    var shizukuState by remember { mutableStateOf(PermState.UNKNOWN) }
    var storageState by remember { mutableStateOf(PermState.UNKNOWN) }
    var refreshKey by remember { mutableStateOf(0) }
    var forceProbe by remember { mutableStateOf(false) }
    var showPermOverview by remember { mutableStateOf(false) }

    var showDisclaimer by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var crashCount by remember { mutableStateOf(0) }
    var lastCrash by remember { mutableStateOf<String?>(null) }
    var showPathEditor by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf(AppPrefs.storageRoot) }
    var storagePath by remember { mutableStateOf(StorageManager.rootPath(context)) }

    // 主动申请 Root 授权（会触发授权管理器弹窗，需在子线程等待用户点击）
    val requestRootPermission: () -> Unit = {
        scope.launch {
            context.toast("正在申请 Root 权限，请在弹出的授权窗口中点击「允许」…")
            val ok = withContext(Dispatchers.IO) { PermissionManager.requestRoot() }
            context.toast(if (ok) "✅ 已获得 Root 权限" else "未获得 Root 权限（可能被拒绝或设备未 Root）")
            refreshKey++
        }
    }

    // 从系统设置 / 授权页切回前台时自动刷新（权限状态实时更新）
    OnResume { refreshKey++ }

    LaunchedEffect(refreshKey) {
        val probe = forceProbe
        forceProbe = false
        val snap = withContext(Dispatchers.IO) { PermissionManager.snapshot(context, doRootProbe = probe) }
        rootState = snap.root
        shizukuState = snap.shizuku
        storageState = snap.storage
        storagePath = StorageManager.rootPath(context)
        crashCount = CrashHandler.count()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard(title = "工作模式") {
            WorkMode.entries.forEach { m ->
                RadioRow(
                    title = m.label,
                    selected = m == workMode,
                    onClick = {
                        workMode = m
                        AppPrefs.workMode = m
                        refreshKey++
                        // 选择高权限模式时，若对应权限未就绪则立即发起申请
                        when (m) {
                            WorkMode.ROOT -> {
                                if (PermissionManager.isRootAvailable()) requestRootPermission()
                                else context.toast("未检测到 su，当前设备可能未 Root")
                            }
                            WorkMode.SHIZUKU -> {
                                if (PermissionManager.isShizukuInstalled(context)) {
                                    PermissionManager.requestShizukuPermission { ok ->
                                        context.toast(if (ok) "Shizuku 已授权" else "Shizuku 授权失败或被拒绝")
                                        refreshKey++
                                    }
                                } else {
                                    context.toast("未检测到 Shizuku，请先安装并启动 Shizuku")
                                }
                            }
                            else -> Unit
                        }
                    }
                )
            }
            ActionRow("重新检测权限", Icons.Filled.GppGood, onClick = { forceProbe = true; refreshKey++ })
            ActionRow("权限总览（查看缺失项）", Icons.Filled.FactCheck, onClick = { showPermOverview = true })
            ActionRow("一键申请/修复权限", Icons.Filled.Lock, onClick = {
                when {
                    rootState != PermState.GRANTED && PermissionManager.isRootAvailable() -> requestRootPermission()
                    shizukuState != PermState.GRANTED && shizukuState != PermState.UNAVAILABLE ->
                        PermissionManager.requestShizukuPermission { ok ->
                            context.toast(if (ok) "Shizuku 已授权" else "Shizuku 授权失败或被拒绝")
                            refreshKey++
                        }
                    storageState != PermState.GRANTED -> requestAllFiles(context)
                    else -> { context.toast("权限已就绪"); refreshKey++ }
                }
            })
        }

        SectionCard(title = "权限状态（点击条目即可申请）") {
            SettingRow(
                "Root 权限",
                subtitle = when (rootState) {
                    PermState.GRANTED -> "${rootState.label()}（点击可重新检测）"
                    PermState.UNAVAILABLE -> "设备未检测到 su，无法申请"
                    else -> "点击申请 Root 授权（会弹出授权窗口）"
                },
                icon = Icons.Filled.Lock,
                onClick = {
                    if (PermissionManager.isRootAvailable()) requestRootPermission()
                    else context.toast("未检测到 su，当前设备可能未 Root")
                }
            )
            SettingRow(
                "Shizuku 权限",
                subtitle = when (shizukuState) {
                    PermState.GRANTED -> "${shizukuState.label()}（点击可重新检测）"
                    PermState.UNAVAILABLE -> PermissionManager.shizukuStatusText(context)
                    else -> "点击申请 Shizuku 授权"
                },
                icon = Icons.Filled.GppGood,
                onClick = {
                    if (shizukuState == PermState.UNAVAILABLE) {
                        context.toast("未检测到 Shizuku，请先安装并启动 Shizuku 应用")
                    } else {
                        PermissionManager.requestShizukuPermission { ok ->
                            context.toast(if (ok) "Shizuku 已授权" else "Shizuku 授权失败或被拒绝")
                            refreshKey++
                        }
                    }
                }
            )
            SettingRow(
                "文件存储权限",
                subtitle = if (storageState == PermState.GRANTED) "${storageState.label()}（点击查看）"
                else "点击前往系统设置授予「所有文件访问」权限",
                icon = Icons.Filled.Storage,
                onClick = {
                    if (storageState == PermState.GRANTED) context.toast("存储权限已就绪")
                    else PermissionManager.openAllFilesSettings(context)
                }
            )
            SettingRow(
                "通知权限",
                subtitle = "扫描完成等提示需要通知权限（Android 13+）",
                icon = Icons.Filled.Notifications,
                onClick = { context.toast("如未授权，可在系统「应用信息 → 通知」中开启") }
            )
        }

        SectionCard(title = "导航栏设置") {
            NavStyle.entries.forEach { s ->
                RadioRow(title = s.label, selected = s == navStyle, onClick = { onNavStyle(s) })
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("液态玻璃透明度", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(String.format("%.2f", glassAlpha), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = glassAlpha,
                    onValueChange = onGlassAlpha,
                    valueRange = AppPrefs.MIN_ALPHA..AppPrefs.MAX_ALPHA,
                    steps = 6
                )
                Text(
                    "取值范围 0.2 ~ 0.9，实时预览即时生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        SectionCard(title = "主题设置") {
            ThemeMode.entries.forEach { t ->
                RadioRow(title = t.label, selected = t == themeMode, onClick = { onThemeMode(t) })
            }
        }

        SectionCard(title = "扫描功能设置") {
            SwitchRow("扫描生成可视化图片报告", visualReport, { visualReport = it; AppPrefs.visualReport = it })
        }

        SectionCard(title = "拓展功能（附加可选检测模块）") {
            Text(
                "提示：拓展功能属于顺手附加能力，本软件核心功能为微信APK模块适配。关闭拓展可减少扫描耗时。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            SwitchRow("启用拓展功能总开关", extMaster, { extMaster = it; AppPrefs.extMaster = it })
            SwitchRow("启用加固识别检测", extHardening, { extHardening = it; AppPrefs.extHardening = it },
                subtitle = "识别乐固、360加固、爱加密、梆梆等，仅静态识别不脱壳", enabled = extMaster)
            SwitchRow("启用第三方SDK & 广告SDK静态检测", extSdk, { extSdk = it; AppPrefs.extSdk = it }, enabled = extMaster)
            SwitchRow("启用组件、权限、静态Intent调用检索", extComponent, { extComponent = it; AppPrefs.extComponent = it }, enabled = extMaster)
            SwitchRow("APK内部资源预览（图片/文本）", extPreview, { extPreview = it; AppPrefs.extPreview = it }, enabled = extMaster)
            SwitchRow("解析 resources.arsc 资源字符串表", extArsc, { extArsc = it; AppPrefs.extArsc = it }, enabled = extMaster)
        }

        SectionCard(title = "存储与文件管理") {
            SettingRow("存储根目录", storagePath, icon = Icons.Filled.Storage)
            ActionRow("打开存储文件夹", Icons.Filled.FolderOpen) { openFolder(context, StorageManager.rootDir(context)) }
            ActionRow("修改存储目录", Icons.Filled.Description) {
                pathInput = AppPrefs.storageRoot
                showPathEditor = true
            }
            SwitchRow("扫描完成自动清理临时缓存", autoClean, {
                autoClean = it; AppPrefs.autoCleanTemp = it
                if (it) {
                    scope.launch {
                        val freed = withContext(Dispatchers.IO) { StorageManager.cleanTemp(context) }
                        context.toast("已清理临时缓存 ${freed / 1024} KB")
                    }
                }
            })
            SwitchRow("独立备份日志文件", backupLog, { backupLog = it; AppPrefs.backupLog = it })
        }

        SectionCard(title = "插件与扩展") {
            ActionRow("打开插件商店", Icons.Filled.Extension, onClick = { onOpenPluginStore() })
            SettingRow(
                "插件说明",
                "在线安装加固 / SDK / Hook 特征包，扩展识别能力（不含破解类内容）",
                icon = Icons.Filled.Extension
            )
        }

        SectionCard(title = "稳定性") {
            SettingRow(
                "崩溃记录",
                if (crashCount == 0) "暂无（运行良好）" else "$crashCount 条",
                icon = Icons.Filled.BugReport
            )
            ActionRow("查看最近崩溃", Icons.Filled.BugReport) {
                val t = CrashHandler.lastCrashText()
                lastCrash = if (t.isNullOrBlank()) "暂无崩溃记录 🎉" else t
            }
            ActionRow("清空崩溃记录", Icons.Filled.Delete) {
                val n = CrashHandler.clear()
                crashCount = CrashHandler.count()
                context.toast("已清空 $n 条崩溃记录")
            }
        }

        SectionCard(title = "关于与更新") {
            SettingRow("当前版本", "v${BuildConfig.VERSION_NAME} · Apache-2.0", icon = Icons.Filled.Info)
            CheckUpdateActionRow()
            ActionRow("项目主页 (GitHub)", Icons.Filled.Link) { context.openUrl(UpdateChecker.GITHUB_REPO) }
            ActionRow("问题反馈 / 建议", Icons.Filled.BugReport) { context.openUrl(UpdateChecker.ISSUES_PAGE) }
        }

        SectionCard(title = "法律与声明") {
            ActionRow("查看免责声明", Icons.Filled.Description) { showDisclaimer = true }
            ActionRow("查看开源协议", Icons.Filled.GppGood) { showLicense = true }
        }

        Spacer(Modifier.height(108.dp))
    }

    lastCrash?.let { text ->
        InfoDialog("最近崩溃记录", text) { lastCrash = null }
    }

    if (showDisclaimer) InfoDialog("免责声明 & 使用须知", DISCLAIMER_TEXT) { showDisclaimer = false }
    if (showLicense) InfoDialog("开源协议", "Apache License 2.0\n\nCopyright (c) 2026 Johnny520\n\nLicensed under the Apache License, Version 2.0. 完整协议文本见仓库根目录 LICENSE 文件。") { showLicense = false }

    if (showPermOverview) {
        PermissionOverviewDialog(onDismiss = { showPermOverview = false })
    }

    if (showPathEditor) {
        WxAlertDialog(
            onDismissRequest = { showPathEditor = false },
            title = "修改存储目录",
            confirmText = "保存",
            onConfirm = {
                AppPrefs.storageRoot = pathInput.trim()
                StorageManager.invalidateCache()
                StorageManager.ensureDirs(context)
                storagePath = StorageManager.rootPath(context)
                showPathEditor = false
            },
            dismissText = "取消",
            onDismiss = { showPathEditor = false }
        ) {
            Column {
                Text("留空则使用默认路径", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("/storage/emulated/0/WxDexInspector") }
                )
            }
        }
    }
}

private fun PermState.label(): String = when (this) {
    PermState.GRANTED -> "✅ 已授权"
    PermState.DENIED -> "❌ 未授权"
    PermState.UNAVAILABLE -> "⚠ 不可用"
    PermState.UNKNOWN -> "… 检测中"
}

private fun openFolder(context: Context, dir: File) {
    runCatching {
        if (!dir.exists()) dir.mkdirs()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", dir)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure { context.toast("无法打开文件夹：${it.message}") }
}

private fun requestAllFiles(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        } else {
            context.toast("存储权限已就绪")
        }
    } catch (t: Throwable) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }.onFailure { context.toast("无法打开授权页面：${t.message}") }
    }
}
