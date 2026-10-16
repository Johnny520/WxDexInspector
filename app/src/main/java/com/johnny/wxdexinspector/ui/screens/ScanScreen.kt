package com.johnny.wxdexinspector.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnny.wxdexinspector.core.dex.ScanCancelledException
import com.johnny.wxdexinspector.core.scan.ScanOrchestrator
import com.johnny.wxdexinspector.core.scan.ScanResult
import com.johnny.wxdexinspector.data.model.ScanMode
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import com.johnny.wxdexinspector.ui.dialog.WxAlertDialog
import com.johnny.wxdexinspector.ui.theme.WxRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue

@Composable
fun ScanScreen(
    apkPath: String,
    source: String,
    scanMode: ScanMode,
    onBack: () -> Unit,
    onOpenLogs: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logQueue = remember { ConcurrentLinkedQueue<String>() }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var exportInfo by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var progressText by remember { mutableStateOf("准备中…") }
    var deepDex by remember { mutableStateOf(true) }
    val progressRef = remember { java.util.concurrent.atomic.AtomicReference(0f to "准备中…") }

    // 日志收集协程（避免跨线程改状态）
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(150)
            if (logQueue.isNotEmpty()) {
                val add = ArrayList<String>()
                while (true) {
                    val s = logQueue.poll() ?: break
                    add.add(s)
                }
                if (add.isNotEmpty()) logs = (logs + add).takeLast(500)
            }
            val p = progressRef.get()
            if (p.first != progress || p.second != progressText) {
                progress = p.first
                progressText = p.second
            }
        }
    }

    // 大体积 APK 先确认，避免用户误以为「卡死」
    var pendingBig by remember { mutableStateOf<Long?>(null) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(apkPath) {
        deepDex = true
        val size = withContext(Dispatchers.IO) {
            runCatching { java.io.File(apkPath).length() }.getOrDefault(0L)
        }
        if (size > 120L * 1024 * 1024) pendingBig = size else started = true
    }

    LaunchedEffect(apkPath, started) {
        if (!started) return@LaunchedEffect
        running = true
        logs = emptyList()
        result = null
        error = null
        progress = 0f
        progressText = "准备中…"
        progressRef.set(0f to "准备中…")
        try {
            val r = withContext(Dispatchers.IO) {
                ScanOrchestrator.scan(context, apkPath, source, scanMode, { line -> logQueue.add(line) }, deepDex) { p, text ->
                    progressRef.set(p.coerceIn(0f, 1f) to text)
                }
            }
            result = r
        } catch (ce: ScanCancelledException) {
            error = "扫描已取消。"
        } catch (t: Throwable) {
            error = (t.message ?: t.toString()) +
                "\n\n若为闪退/内存问题，可点下方「查看最近崩溃」，或到「设置 → 稳定性」查看记录。"
        }
        running = false
        progress = 1f
        progressText = if (error == null) "完成" else "已停止"
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text("扫描中…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(scanMode.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (running) CircularProgressIndicator(Modifier.padding(8.dp).height(24.dp).padding(4.dp))
        }

        if (running) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { ScanOrchestrator.cancelCurrent() },
                    shape = RoundedCornerShape(16.dp)
                ) { Text("取消扫描") }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val r = result
            if (r != null) {
                item { ResultSummary(r) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val zip = withContext(Dispatchers.IO) {
                                        ScanOrchestrator.export(context, r, com.johnny.wxdexinspector.data.AppPrefs.visualReport) { line -> logQueue.add(line) }
                                    }
                                    exportInfo = zip?.absolutePath ?: "导出失败"
                                }
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("导出ZIP报告") }
                        OutlinedButton(onClick = onBack, shape = RoundedCornerShape(16.dp)) { Text("返回") }
                    }
                }
            } else if (error != null) {
                item {
                    SectionCard(title = "扫描未完成") {
                        Text(
                            error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (onOpenLogs != null) {
                                OutlinedButton(onClick = onOpenLogs, shape = RoundedCornerShape(16.dp)) { Text("查看运行日志") }
                            }
                            Button(
                                onClick = {
                                    val t = com.johnny.wxdexinspector.core.crash.CrashHandler.lastCrashText()
                                    error = if (t.isNullOrBlank()) {
                                        (error ?: "") + "\n\n（暂无系统级崩溃记录，本次为可捕获的异常）"
                                    } else {
                                        t
                                    }
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("查看最近崩溃") }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "实时日志") {
                    if (logs.isEmpty()) {
                        Text(
                            "等待日志输出…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            logs.forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    color = logColor(line),
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingBig?.let { size ->
        WxAlertDialog(
            onDismissRequest = { pendingBig = null; onBack() },
            title = "大体积 APK 提示",
            confirmText = "继续扫描",
            onConfirm = { pendingBig = null; started = true },
            dismissText = "返回",
            onDismiss = { pendingBig = null; onBack() }
        ) {
            Column {
                Text(
                    "当前 APK 体积约 ${size / 1024 / 1024} MB，DEX 深度解析可能耗时较长并占用较多内存，" +
                        "存在被系统强制回收（表现为无提示闪退）的风险。\n\n" +
                        "系统可用内存：${com.johnny.wxdexinspector.core.util.MemInfo.availableText()}\n" +
                        "解析过程中可随时点「取消扫描」中断。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { pendingBig = null; deepDex = false; started = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("仅基础信息解析（不加载 dexkit，最省内存）") }
            }
        }
    }

    exportInfo?.let { msg ->
        InfoDialog("导出结果", msg) { exportInfo = null }
    }
}

@Composable
private fun ResultSummary(r: ScanResult) {
    SectionCard(title = "扫描结果：${r.displayName}") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            StatLine("包名", r.apk.packageName)
            StatLine("版本", "${r.apk.versionName} (${r.apk.versionCode})")
            StatLine("工作模式", r.modeLabel)
            StatLine("DEX / 类", "${r.stats.dexCount} / ${r.stats.classCount}")
            StatLine("方法 / 字段", "${r.stats.methodCount} / ${r.stats.fieldCount}")
            StatLine("字符串", r.stats.stringCount.toString())
            StatLine("Hook 候选", r.hookCandidates.size.toString())
            if (r.extEnabled) {
                StatLine("加固", r.ext?.hardening?.joinToString("、")?.ifBlank { "未检测到" } ?: "-")
                StatLine("SDK", r.ext?.sdks?.joinToString("、")?.ifBlank { "无" } ?: "-")
            }
            if (r.truncated) {
                Text("注：结果超过上限，部分明细已截断", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (r.hookCandidates.isNotEmpty()) {
            Text(
                "Hook 候选（Top 10）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            )
            r.hookCandidates.take(10).forEach { h ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(4.dp))
                    Column {
                        Text("[${h.confidence}] ${h.description}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text("${h.className}->${h.methodName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun logColor(line: String) = when {
    line.contains("ERROR", true) -> com.johnny.wxdexinspector.ui.theme.LogErrorColor
    line.contains("WARN", true) -> com.johnny.wxdexinspector.ui.theme.LogWarnColor
    else -> com.johnny.wxdexinspector.ui.theme.LogInfoColor
}
