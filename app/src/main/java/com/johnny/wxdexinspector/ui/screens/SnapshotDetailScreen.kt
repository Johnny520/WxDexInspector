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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.core.scan.ScanOrchestrator
import com.johnny.wxdexinspector.core.scan.ScanResult
import com.johnny.wxdexinspector.core.snapshot.SnapshotManager
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.ui.components.ConfirmDialog
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SnapshotDetailScreen(
    filePath: String,
    onBack: () -> Unit,
    onDiff: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        loading = true
        result = withContext(Dispatchers.IO) { SnapshotManager.load(File(filePath)) }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("快照详情", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val r = result
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("快照读取失败") }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard(title = r.displayName) {
                    Column(Modifier.padding(16.dp)) {
                        Line("包名", r.apk.packageName)
                        Line("版本", "${r.apk.versionName} (${r.apk.versionCode})")
                        Line("扫描时间", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(r.createdAt)))
                        Line("工作模式", r.modeLabel)
                        Line("扫描模式", r.scanModeLabel)
                        Line("拓展功能", if (r.extEnabled) "已启用" else "未启用")
                        Spacer(Modifier.height(8.dp))
                        Line("类 / 方法", "${r.stats.classCount} / ${r.stats.methodCount}")
                        Line("字段 / 字符串", "${r.stats.fieldCount} / ${r.stats.stringCount}")
                        Line("Hook 候选", r.hookCandidates.size.toString())
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val zip = withContext(Dispatchers.IO) { ScanOrchestrator.export(context, r, AppPrefs.visualReport) }
                            info = zip?.absolutePath ?: "导出失败"
                        }
                    }, shape = RoundedCornerShape(16.dp)) { Text("二次导出ZIP") }
                    OutlinedButton(onClick = { onDiff(filePath) }, shape = RoundedCornerShape(16.dp)) { Text("与其他快照对比") }
                }
            }
            if (r.hookCandidates.isNotEmpty()) {
                item {
                    SectionCard(title = "Hook 候选 (${r.hookCandidates.size})") {
                        Column(Modifier.padding(14.dp)) {
                            r.hookCandidates.take(100).forEach { h ->
                                Text("[${h.confidence}] ${h.description}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("${h.className}->${h.methodName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    info?.let { InfoDialog("导出结果", it) { info = null } }
    if (confirmDelete) {
        ConfirmDialog(
            title = "删除快照",
            message = "确定删除该 .wxsnap 快照？此操作不可恢复。",
            onConfirm = {
                SnapshotManager.delete(File(filePath))
                confirmDelete = false
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
