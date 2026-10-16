package com.johnny.wxdexinspector.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.johnny.wxdexinspector.core.scan.ScanSession
import com.johnny.wxdexinspector.core.scan.ScanSessionStore
import com.johnny.wxdexinspector.data.model.ScanMode
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import com.johnny.wxdexinspector.ui.dialog.WxAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * 扫描页（分段可续扫）。
 *
 * 交互：逐 dex 串行解析，一段接着一段；只有当内存吃紧时才在 dex 边界暂停，
 * 页面会显示「继续扫描剩余 N 个 dex」，点一下从下一个 dex 接着扫，最终得到完整结果。
 */
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
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var session by remember { mutableStateOf<ScanSession?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var exportInfo by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var progressText by remember { mutableStateOf("准备中…") }
    var deepDex by remember { mutableStateOf(true) }
    val progressRef = remember { AtomicReference(0f to "准备中…") }

    // 大体积 APK 确认 / 未完成会话恢复
    var pendingBig by remember { mutableStateOf<Long?>(null) }
    var resumeCandidate by remember { mutableStateOf<ScanSession?>(null) }
    var autoStart by remember { mutableStateOf(0) } // 0=无 1=新扫描 2=续扫

    // 日志与进度收集协程（避免跨线程改状态）
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

    // 执行一段扫描（resume=true 表示从上一个 dex 接着扫）
    suspend fun runSegment(resume: Boolean) {
        if (running) return
        running = true
        error = null
        if (resume) {
            // 先释放上一段结果，避免「上一段 + 本段」双份内存
            result = null
            progressText = "继续扫描…"
        } else {
            logs = emptyList()
            result = null
            session = null
            progress = 0f
            progressText = "准备中…"
            progressRef.set(0f to "准备中…")
        }
        try {
            val run = withContext(Dispatchers.IO) {
                if (resume) {
                    val s = session ?: throw IllegalStateException("扫描会话已失效，请返回后重新扫描")
                    ScanOrchestrator.continueChunk(context, s, { line -> logQueue.add(line) }) { p, t ->
                        progressRef.set(p.coerceIn(0f, 1f) to t)
                    }
                } else {
                    ScanOrchestrator.startChunk(context, apkPath, source, scanMode, deepDex, { line -> logQueue.add(line) }) { p, t ->
                        progressRef.set(p.coerceIn(0f, 1f) to t)
                    }
                }
            }
            session = run.session
            result = run.result
        } catch (ce: ScanCancelledException) {
            error = "扫描已取消。"
        } catch (t: Throwable) {
            error = (t.message ?: t.toString()) +
                "\n\n若为闪退/内存问题，可点下方「查看最近崩溃」，或到「设置 → 稳定性」查看记录。"
        }
        running = false
        progressText = when {
            error != null -> "已停止"
            session?.finished == true -> "完成"
            else -> "本段完成，可继续"
        }
    }

    LaunchedEffect(autoStart) {
        when (autoStart) {
            1 -> runSegment(false)
            2 -> runSegment(true)
        }
        if (autoStart != 0) autoStart = 0
    }

    LaunchedEffect(apkPath) {
        deepDex = true
        val size = withContext(Dispatchers.IO) {
            runCatching { java.io.File(apkPath).length() }.getOrDefault(0L)
        }
        val existing = withContext(Dispatchers.IO) {
            runCatching {
                ScanSessionStore.load(context, ScanSessionStore.idOf(apkPath, size))?.takeIf { !it.finished }
            }.getOrNull()
        }
        when {
            existing != null -> resumeCandidate = existing
            size > 120L * 1024 * 1024 -> pendingBig = size
            else -> autoStart = 1
        }
    }

    val title = when {
        resumeCandidate != null -> "发现未完成扫描"
        pendingBig != null -> "等待确认"
        running -> "扫描中…"
        error != null -> "扫描未完成"
        session?.finished == true -> "扫描完成"
        session != null -> "本段完成（可继续）"
        else -> "准备中…"
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(scanMode.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (error != null && !running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // 固定位置的进度区（0% → 100%）：不再使用位置会跳动的转圈，且始终占位避免布局抖动
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (running) {
                    OutlinedButton(
                        onClick = { ScanOrchestrator.cancelCurrent() },
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("取消扫描") }
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val r = result
            val s = session
            if (r != null) {
                item { ResultSummary(r, s) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (s != null && !s.finished) {
                            Button(
                                onClick = { scope.launch { runSegment(true) } },
                                enabled = !running,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("继续扫描剩余 ${s.remaining} 个 dex") }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val zip = withContext(Dispatchers.IO) {
                                        ScanOrchestrator.export(context, r, com.johnny.wxdexinspector.data.AppPrefs.visualReport) { line -> logQueue.add(line) }
                                    }
                                    exportInfo = zip?.absolutePath ?: "导出失败"
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (s != null && !s.finished) "导出当前结果（尚未扫完）" else "导出 ZIP 报告") }
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("返回") }
                    }
                }
                item { ClassDirectory(r) }
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

    // 未完成会话：询问继续 / 重新开始
    resumeCandidate?.let { s ->
        WxAlertDialog(
            onDismissRequest = { resumeCandidate = null; onBack() },
            title = "发现未完成的扫描",
            confirmText = "继续扫描",
            onConfirm = {
                resumeCandidate = null
                session = s
                logs = emptyList()
                progress = s.progressPercent / 100f
                progressText = "继续扫描…"
                autoStart = 2
            },
            dismissText = "重新开始",
            onDismiss = {
                resumeCandidate = null
                ScanSessionStore.clear(context, s.id)
                autoStart = 1
            }
        ) {
            Text(
                "上次扫描到 ${s.nextDexIndex}/${s.totalDex} 个 dex（${s.progressPercent}%），还有 ${s.remaining} 个 dex 未扫描。\n\n" +
                    "「继续扫描」从下一个 dex 接着扫（保留已扫描部分）；「重新开始」会清空进度从头来。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    // 大体积 APK：先确认（可改为仅基础信息解析）
    pendingBig?.let { size ->
        WxAlertDialog(
            onDismissRequest = { pendingBig = null; onBack() },
            title = "大体积 APK 提示",
            confirmText = "开始深度扫描",
            onConfirm = { pendingBig = null; deepDex = true; autoStart = 1 },
            dismissText = "返回",
            onDismiss = { pendingBig = null; onBack() }
        ) {
            Column {
                Text(
                    "当前 APK 体积约 ${size / 1024 / 1024} MB。深度扫描会**逐个 dex 串行解析**（读一个、解析一个、释放一个），" +
                        "内存吃紧时会在 dex 边界自动暂停，点「继续扫描」接着来，直到全部扫完。\n\n" +
                        "系统可用内存：${com.johnny.wxdexinspector.core.util.MemInfo.availableText()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { pendingBig = null; deepDex = false; autoStart = 1 },
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
private fun ResultSummary(r: ScanResult, s: ScanSession?) {
    SectionCard(title = "扫描结果：${r.displayName}") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            StatLine("包名", r.apk.packageName)
            StatLine("版本", "${r.apk.versionName} (${r.apk.versionCode})")
            StatLine("工作模式", r.modeLabel)
            if (s != null) {
                StatLine("扫描进度", "${s.nextDexIndex}/${s.totalDex} 个 dex（${s.progressPercent}%）")
            }
            StatLine("DEX / 类", "${r.stats.dexCount} / ${r.stats.classCount}")
            StatLine("方法 / 字段", "${r.stats.methodCount} / ${r.stats.fieldCount}")
            StatLine("字符串", r.stats.stringCount.toString())
            StatLine("Hook 候选", r.hookCandidates.size.toString())
            if (r.extEnabled) {
                StatLine("加固", r.ext?.hardening?.joinToString("、")?.ifBlank { "未检测到" } ?: "（全部 dex 完成后分析）")
                StatLine("SDK", r.ext?.sdks?.joinToString("、")?.ifBlank { "无" } ?: "-")
            }
            if (s != null && !s.finished) {
                Text(
                    "⏳ 还有 ${s.remaining} 个 dex 未扫描。点下方「继续扫描剩余 ${s.remaining} 个 dex」接着来，扫完后即为完整结果。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (r.truncated && (s == null || s.finished)) {
                Text(
                    "注：明细列表已达容量上限（统计数字仍然完整）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
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

/** 类目录：像书的目录一样按包名分组，点击展开该组的类名 */
@Composable
private fun ClassDirectory(r: ScanResult) {
    val groups = remember(r) { buildPackageGroups(r.classNames) }
    if (groups.isEmpty()) return
    var expanded by remember { mutableStateOf<String?>(null) }
    SectionCard(title = "类目录（按包名分组，共 ${groups.size} 组）") {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            groups.take(40).forEach { g ->
                val isOpen = expanded == g.prefix
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (isOpen) null else g.prefix }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isOpen) "▼" else "▶",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.padding(horizontal = 5.dp))
                    Text(
                        g.prefix,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${g.count} 类",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isOpen) {
                    g.samples.forEach { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 22.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }
                    if (g.count > g.samples.size) {
                        Text(
                            "… 该组共 ${g.count} 个类（仅展示前 ${g.samples.size} 个）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 22.dp, top = 2.dp)
                        )
                    }
                }
            }
            if (groups.size > 40) {
                Text(
                    "… 共 ${groups.size} 组（仅展示前 40 组）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private data class PkgGroup(val prefix: String, val count: Int, val samples: List<String>)

private const val GROUP_SAMPLES = 30

private fun buildPackageGroups(classNames: List<String>): List<PkgGroup> {
    if (classNames.isEmpty()) return emptyList()
    val counts = HashMap<String, Int>()
    val samples = HashMap<String, MutableList<String>>()
    classNames.forEach { name ->
        val key = packagePrefix(name)
        counts[key] = (counts[key] ?: 0) + 1
        val list = samples.getOrPut(key) { ArrayList(GROUP_SAMPLES) }
        if (list.size < GROUP_SAMPLES) list.add(name)
    }
    return counts.entries
        .sortedByDescending { it.value }
        .map { PkgGroup(it.key, it.value, samples[it.key] ?: emptyList()) }
}

/** 取包名前两段作为「目录」条目 */
private fun packagePrefix(className: String): String {
    val idx = className.lastIndexOf('.')
    if (idx <= 0) return "(默认包)"
    val parts = className.substring(0, idx).split('.')
    return if (parts.size <= 2) parts.joinToString(".") else parts.take(2).joinToString(".") + "…"
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
