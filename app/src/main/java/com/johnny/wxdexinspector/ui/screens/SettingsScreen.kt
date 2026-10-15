package com.johnny.wxdexinspector.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.util.toast
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.data.model.NavStyle
import com.johnny.wxdexinspector.data.model.ThemeMode
import com.johnny.wxdexinspector.data.model.WorkMode
import com.johnny.wxdexinspector.ui.components.ActionRow
import com.johnny.wxdexinspector.ui.components.RadioRow
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.components.SettingRow
import com.johnny.wxdexinspector.ui.components.SwitchRow
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import com.johnny.wxdexinspector.ui.dialog.WxAlertDialog
import java.io.File

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    navStyle: NavStyle,
    onNavStyle: (NavStyle) -> Unit,
    glassAlpha: Float,
    onGlassAlpha: (Float) -> Unit,
    onOpenStatus: () -> Unit
) {
    val context = LocalContext.current

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

    var showDisclaimer by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var showPathEditor by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf(AppPrefs.storageRoot) }

    var storagePath by remember { mutableStateOf(StorageManager.rootPath(context)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // ===== 工作模式 =====
        SectionCard(title = "工作模式") {
            WorkMode.entries.forEach { m ->
                RadioRow(
                    title = m.label,
                    selected = m == workMode,
                    onClick = { workMode = m; AppPrefs.workMode = m }
                )
            }
            ActionRow("重新检测权限", Icons.Filled.GppGood, onClick = onOpenStatus)
            ActionRow("重新申请授权", Icons.Filled.Lock, onClick = onOpenStatus)
        }

        // ===== 权限状态卡片 =====
        SectionCard(title = "权限状态") {
            SettingRow("Root", permLabel(false), icon = Icons.Filled.Lock, onClick = onOpenStatus)
            SettingRow("Shizuku", permLabel(false), icon = Icons.Filled.GppGood, onClick = onOpenStatus)
            SettingRow("文件存储权限", permLabel(true), icon = Icons.Filled.Storage, onClick = onOpenStatus)
        }

        // ===== 导航栏设置 =====
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

        // ===== 主题设置 =====
        SectionCard(title = "主题设置") {
            ThemeMode.entries.forEach { t ->
                RadioRow(title = t.label, selected = t == themeMode, onClick = { onThemeMode(t) })
            }
        }

        // ===== 扫描功能设置 =====
        SectionCard(title = "扫描功能设置") {
            SwitchRow("扫描生成可视化图片报告", visualReport, { visualReport = it; AppPrefs.visualReport = it })
        }

        // ===== 拓展功能 =====
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

        // ===== 存储与文件管理 =====
        SectionCard(title = "存储与文件管理") {
            SettingRow("存储根目录", storagePath, icon = Icons.Filled.Storage)
            ActionRow("打开存储文件夹", Icons.Filled.FolderOpen) {
                openFolder(context, StorageManager.rootDir(context))
            }
            ActionRow("修改存储目录", Icons.Filled.Description) {
                pathInput = AppPrefs.storageRoot
                showPathEditor = true
            }
            SwitchRow("扫描完成自动清理临时缓存", autoClean, { autoClean = it; AppPrefs.autoCleanTemp = it })
            SwitchRow("独立备份日志文件", backupLog, { backupLog = it; AppPrefs.backupLog = it })
        }

        // ===== 法律与声明 =====
        SectionCard(title = "法律与声明") {
            ActionRow("查看免责声明", Icons.Filled.Description) { showDisclaimer = true }
            ActionRow("查看开源协议", Icons.Filled.GppGood) { showLicense = true }
        }

        Spacer(Modifier.height(28.dp))
    }

    if (showDisclaimer) {
        InfoDialog("免责声明 & 使用须知", com.johnny.wxdexinspector.ui.dialog.DISCLAIMER_TEXT) { showDisclaimer = false }
    }
    if (showLicense) {
        InfoDialog("开源协议", "Apache License 2.0\n\nCopyright (c) 2026 Johnny520\n\nLicensed under the Apache License, Version 2.0. 完整协议文本见仓库根目录 LICENSE 文件。") { showLicense = false }
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

private fun permLabel(ok: Boolean): String = if (ok) "✅ 已授权" else "❌ 未授权"

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
