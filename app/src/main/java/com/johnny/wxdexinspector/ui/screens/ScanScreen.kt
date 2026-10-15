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
import com.johnny.wxdexinspector.core.scan.ScanOrchestrator
import com.johnny.wxdexinspector.core.scan.ScanResult
import com.johnny.wxdexinspector.data.model.ScanMode
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logQueue = remember { ConcurrentLinkedQueue<String>() }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var exportInfo by remember { mutableStateOf<String?>(null) }

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
        }
    }

    LaunchedEffect(apkPath) {
        running = true
        logs = emptyList()
        try {
            val r = withContext(Dispatchers.IO) {
                ScanOrchestrator.scan(context, apkPath, source, scanMode) { line -> logQueue.add(line) }
            }
            result = r
        } catch (t: Throwable) {
            error = t.message ?: t.toString()
        }
        running = false
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

        if (running) LinearProgressIndicator(Modifier.fillMaxWidth())

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
                    SectionCard(title = "扫描失败") {
                        Text(
                            error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
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
