package com.johnny.wxdexinspector.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnny.wxdexinspector.core.apk.ApkInfoReader
import com.johnny.wxdexinspector.core.apk.ApkMeta
import com.johnny.wxdexinspector.core.ext.BinaryRes
import com.johnny.wxdexinspector.core.ext.ExtensionAnalyzer
import com.johnny.wxdexinspector.core.ext.ExtensionResult
import com.johnny.wxdexinspector.core.util.copyToClipboard
import com.johnny.wxdexinspector.core.util.toast
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.components.SettingRow
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import com.johnny.wxdexinspector.ui.theme.WxRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass

@Composable
fun ApkDetailScreen(
    apkPath: String,
    source: String,
    onBack: () -> Unit,
    onScan: (String, String) -> Unit
) {
    val context = LocalContext.current
    var meta by remember { mutableStateOf<ApkMeta?>(null) }
    var ext by remember { mutableStateOf<ExtensionResult?>(null) }
    var sdkList by remember { mutableStateOf<List<String>?>(null) }
    var hardening by remember { mutableStateOf<List<String>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var arsc by remember { mutableStateOf<List<String>?>(null) }
    var previewText by remember { mutableStateOf<Pair<String, String>?>(null) }
    var previewImg by remember { mutableStateOf<Pair<String, android.graphics.Bitmap>?>(null) }

    LaunchedEffect(apkPath) {
        loading = true
        val r = withContext(Dispatchers.IO) {
            val m = ApkInfoReader.read(context, apkPath, source)
            val entries = BinaryRes.listZipEntries(apkPath)
            // 组件/权限解析是核心能力，始终执行；SDK/资源等重解析由「拓展功能」开关控制
            val e = ExtensionAnalyzer.analyze(
                apkPath, emptyList(), entries,
                parseArsc = false, previewFiles = AppPrefs.extPreview, onLog = {}
            )
            Triple(m, e, entries)
        }
        meta = r.first
        ext = r.second
        hardening = r.second?.hardening
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text(meta?.appLabel?.ifBlank { "APK 详情" } ?: "APK 详情", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(meta?.packageName ?: apkPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = { onScan(apkPath, source) }) { Icon(Icons.Filled.PlayArrow, contentDescription = "扫描") }
        }

        val tabs = ArrayList<String>().apply {
            add("概览"); add("组件"); add("证书")
            if (AppPrefs.extActive) add("SDK&加固")
            add("文件")
            if (AppPrefs.extActive) add("资源")
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        val m = meta ?: return@Column
        when (tabs.getOrNull(tab)) {
            "概览" -> OverviewTab(m, context)
            "组件" -> ComponentsTab(ext)
            "SDK&加固" -> SdkTab(
                hardening = hardening,
                sdkList = sdkList,
                onDetect = {
                    sdkList = emptyList()
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launchDetect(context, apkPath) { h, s ->
                        hardening = h
                        sdkList = s
                    }
                }
            )
            "证书" -> CertTab(m, context)
            "资源" -> ArscTab(arsc) {
                arsc = emptyList()
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val list = BinaryRes.readArscStrings(apkPath)
                    withContext(Dispatchers.Main) { arsc = list }
                }
            }
            "文件" -> FilesTab(ext?.fileEntries ?: emptyList(), apkPath) { name, isImage ->
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    if (isImage) {
                        val b = BinaryRes.readZipEntry(apkPath, name)?.let { d ->
                            runCatching { android.graphics.BitmapFactory.decodeByteArray(d, 0, d.size) }.getOrNull()
                        }
                        withContext(Dispatchers.Main) { if (b != null) previewImg = name to b else context.toast("无法预览该图片") }
                    } else {
                        val bytes = BinaryRes.readZipEntry(apkPath, name)
                        val text = bytes?.let { String(it, 0, minOf(it.size, 200_000), Charsets.UTF_8) } ?: ""
                        withContext(Dispatchers.Main) { previewText = name to text }
                    }
                }
            }
        }
    }

    previewText?.let { (name, text) ->
        InfoDialog(name, if (text.isBlank()) "(无法以文本方式显示)" else text) { previewText = null }
    }
    previewImg?.let { (name, bmp) ->
        com.johnny.wxdexinspector.ui.dialog.WxAlertDialog(
            onDismissRequest = { previewImg = null },
            title = name,
            confirmText = "关闭",
            onConfirm = { previewImg = null }
        ) {
            Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OverviewTab(m: ApkMeta, context: Context) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard(title = "基础信息") {
                Column(Modifier.padding(16.dp)) {
                    InfoLine("应用名称", m.appLabel)
                    InfoLine("包名", m.packageName)
                    InfoLine("版本", "${m.versionName} (${m.versionCode})")
                    InfoLine("minSdk / targetSdk", "${m.minSdk} / ${m.targetSdk}")
                    InfoLine("文件大小", "${m.sizeBytes / 1024 / 1024} MB")
                    InfoLine("来源", m.source)
                    InfoLine("文件路径", m.filePath)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = {
                        val text = buildString {
                            appendLine("应用名称: ${m.appLabel}")
                            appendLine("包名: ${m.packageName}")
                            appendLine("版本: ${m.versionName} (${m.versionCode})")
                            appendLine("minSdk/targetSdk: ${m.minSdk}/${m.targetSdk}")
                            appendLine("大小: ${m.sizeBytes} bytes")
                            appendLine("来源: ${m.source}")
                            appendLine("路径: ${m.filePath}")
                            appendLine("MD5: ${m.md5}")
                            appendLine("SHA1: ${m.sha1}")
                            appendLine("SHA256: ${m.sha256}")
                        }
                        context.copyToClipboard(text, "APK概览")
                        context.toast("已复制概览信息")
                    }) { Text("复制全部信息") }
                }
            }
        }
    }
}

@Composable
private fun ComponentsTab(ext: ExtensionResult?) {
    val context = LocalContext.current
    if (ext == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("解析失败：未获取到组件信息", textAlign = TextAlign.Center)
        }
        return
    }
    val componentTotal = ext.activities.size + ext.services.size + ext.receivers.size + ext.providers.size
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard(title = "组件统计") {
                Column(Modifier.padding(16.dp)) {
                    InfoLine("四大组件总数", componentTotal.toString())
                    InfoLine("Activity", ext.activities.size.toString())
                    InfoLine("Service", ext.services.size.toString())
                    InfoLine("Receiver", ext.receivers.size.toString())
                    InfoLine("Provider", ext.providers.size.toString())
                    InfoLine("使用权限", "${ext.permissions.size} 项")
                    InfoLine("自定义权限", "${ext.declaredPermissions.size} 项")
                    InfoLine("Intent-Filter", "${ext.intentFilters.size} 项")
                }
            }
        }
        item { ComponentListCard("使用权限", ext.permissions, context) }
        if (ext.declaredPermissions.isNotEmpty()) {
            item { ComponentListCard("自定义权限", ext.declaredPermissions, context) }
        }
        item { ComponentListCard("Activity", ext.activities, context) }
        item { ComponentListCard("Service", ext.services, context) }
        item { ComponentListCard("Receiver", ext.receivers, context) }
        item { ComponentListCard("Provider", ext.providers, context) }
        if (ext.intentFilters.isNotEmpty()) {
            item {
                SectionCard(title = "Intent-Filter (${ext.intentFilters.size})") {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("点击可复制单条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                val txt = ext.intentFilters.joinToString("\n\n") { f ->
                                    buildString {
                                        append("[${f.componentType}] ${f.componentName}")
                                        if (f.actions.isNotEmpty()) append("\n  action: ${f.actions.joinToString(", ")}")
                                        if (f.categories.isNotEmpty()) append("\n  category: ${f.categories.joinToString(", ")}")
                                        if (f.schemes.isNotEmpty()) append("\n  data: ${f.schemes.joinToString(", ")}")
                                    }
                                }
                                context.copyToClipboard(txt, "Intent-Filter")
                                context.toast("已复制 Intent-Filter")
                            }) { Text("复制全部") }
                        }
                        ext.intentFilters.take(200).forEach { f ->
                            val line = buildString {
                                append("[${f.componentType}] ${f.componentName}")
                                if (f.actions.isNotEmpty()) append("\n  action: ${f.actions.joinToString(", ")}")
                                if (f.categories.isNotEmpty()) append("\n  category: ${f.categories.joinToString(", ")}")
                                if (f.schemes.isNotEmpty()) append("\n  data: ${f.schemes.joinToString(", ")}")
                            }
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { context.copyToClipboard(line, "Intent-Filter"); context.toast("已复制") }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
        if (ext.manifestText.isNotBlank()) {
            item { ManifestCard(ext.manifestText, context) }
        }
    }
}

/** 可点击复制单项、并可一键复制全部的组件/权限列表卡片 */
@Composable
private fun ComponentListCard(title: String, list: List<String>, context: Context) {
    SectionCard(title = "$title (${list.size})") {
        Column(Modifier.padding(14.dp)) {
            if (list.isEmpty()) {
                Text("(空)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("点击任意条目复制", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        context.copyToClipboard(list.joinToString("\n"), title)
                        context.toast("已复制全部 $title")
                    }) { Text("复制全部") }
                }
                list.take(300).forEach { item ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { context.copyToClipboard(item, title); context.toast("已复制") }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("• $item", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "复制",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp).size(15.dp)
                        )
                    }
                }
                if (list.size > 300) {
                    Text("… 仅显示前 300 条，可用「复制全部」获取完整列表", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ManifestCard(text: String, context: Context) {
    SectionCard(title = "AndroidManifest.xml") {
        Column(Modifier.padding(14.dp)) {
            TextButton(onClick = {
                context.copyToClipboard(text, "AndroidManifest.xml")
                context.toast("已复制 Manifest")
            }) { Text("复制全部") }
            Text(text.take(8000), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun SdkTab(hardening: List<String>?, sdkList: List<String>?, onDetect: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard(title = "加固识别") {
                Column(Modifier.padding(16.dp)) {
                    if (hardening == null) Text("未解析")
                    else if (hardening.isEmpty()) Text("✅ 未检测到已知加固特征")
                    else hardening.forEach { Text("⚠ 检测到：$it", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(6.dp))
                    Text("仅静态特征识别，不进行任何脱壳操作", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionCard(title = "第三方 / 广告 SDK") {
                Column(Modifier.padding(16.dp)) {
                    when {
                        sdkList == null -> {
                            Text("SDK 识别需要解析 DEX 类列表", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onDetect, shape = RoundedCornerShape(16.dp)) { Text("运行 SDK 识别") }
                        }
                        sdkList.isEmpty() -> Text("识别中…")
                        else -> sdkList.forEach { Text("• $it") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CertTab(m: ApkMeta, context: Context) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard(title = "文件摘要") {
                Column(Modifier.padding(16.dp)) {
                    CopyLine("MD5", m.md5, context)
                    CopyLine("SHA1", m.sha1, context)
                    CopyLine("SHA256", m.sha256, context)
                }
            }
        }
        item {
            SectionCard(title = "签名证书") {
                Column(Modifier.padding(16.dp)) {
                    CopyLine("证书 MD5", m.signMd5, context)
                    CopyLine("证书 SHA1", m.signSha1, context)
                    CopyLine("证书 SHA256", m.signSha256, context)
                    InfoLine("Subject", m.signSubject)
                    InfoLine("Issuer", m.signIssuer)
                }
            }
        }
    }
}

@Composable
private fun ArscTab(arsc: List<String>?, onLoad: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            SectionCard(title = "resources.arsc 字符串") {
                Column(Modifier.padding(16.dp)) {
                    when {
                        arsc == null -> {
                            Button(onClick = onLoad, shape = RoundedCornerShape(16.dp)) { Text("解析资源字符串表") }
                        }
                        arsc.isEmpty() -> Text("解析中…")
                        else -> {
                            Text("共 ${arsc.size} 条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            arsc.take(500).forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesTab(entries: List<String>, apkPath: String, onOpen: (String, Boolean) -> Unit) {
    val imageExt = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp")
    val textExt = listOf(".txt", ".json", ".xml", ".html", ".js", ".css", ".md", ".properties", ".ini")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text("APK 内部文件（${entries.size}）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        items(entries.take(3000)) { name ->
            val lower = name.lowercase()
            val isImg = imageExt.any { lower.endsWith(it) }
            val isTxt = textExt.any { lower.endsWith(it) }
            Row(
                Modifier.fillMaxWidth().clickable(enabled = isImg || isTxt) { onOpen(name, isImg) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isImg) Icons.Filled.Image else Icons.Filled.Description,
                    contentDescription = null,
                    tint = if (isImg || isTxt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}



@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CopyLine(label: String, value: String, context: Context) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(label, value))
                context.toast("已复制 $label")
            }) { Icon(Icons.Filled.ContentCopy, contentDescription = "复制") }
        }
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchDetect(
    context: Context,
    apkPath: String,
    onDone: (List<String>, List<String>) -> Unit
) {
    launch {
        val result = withContext(Dispatchers.IO) {
            val classNames = runCatching {
                val bridge = DexKitBridge.create(apkPath)
                try {
                    bridge.findClass(FindClass.create()).mapNotNull { runCatching { it.name }.getOrNull() }
                } finally {
                    runCatching { bridge.close() }
                }
            }.getOrDefault(emptyList())
            val entries = BinaryRes.listZipEntries(apkPath)
            ExtensionAnalyzer.detectHardening(entries, classNames) to ExtensionAnalyzer.detectSdks(classNames)
        }
        withContext(Dispatchers.Main) { onDone(result.first, result.second) }
    }
}
