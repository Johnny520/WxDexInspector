package com.johnny.wxdexinspector.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.apk.ApkExtractor
import com.johnny.wxdexinspector.core.scan.ScanOrchestrator
import com.johnny.wxdexinspector.core.scan.ScanResult
import com.johnny.wxdexinspector.core.snapshot.SnapshotManager
import com.johnny.wxdexinspector.data.model.ScanMode
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BatchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var results by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                val list = ArrayList<File>()
                uris.forEach { uri ->
                    val r = ApkExtractor.importFromUri(context, uri, StorageManager.tempDir(context))
                    if (r is com.johnny.wxdexinspector.core.apk.ExtractResult.Success) {
                        r.files.firstOrNull()?.let { list.add(File(it)) }
                    }
                }
                list
            }
            files = files + imported
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text("多版本批量扫描", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("已选择 ${files.size} 个 APK", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "*/*")) }) {
                Icon(Icons.Filled.FolderOpen, contentDescription = "添加APK")
            }
        }

        if (running) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (files.isEmpty()) return@Button
                    running = true
                    results = emptyList()
                    scope.launch {
                        val acc = ArrayList<ScanResult>()
                        files.forEachIndexed { i, f ->
                            status = "正在扫描 (${i + 1}/${files.size})：${f.name}"
                            progress = i.toFloat() / files.size
                            val r = runCatching {
                                withContext(Dispatchers.IO) {
                                    ScanOrchestrator.scan(context, f.absolutePath, "batch", ScanMode.BATCH, {})
                                }
                            }.getOrNull()
                            if (r != null) acc.add(r.slimForBatch())
                            results = acc.toList()
                        }
                        progress = 1f
                        status = "批量扫描完成"
                        running = false
                    }
                },
                enabled = files.isNotEmpty() && !running,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("开始批量扫描") }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (files.isEmpty()) {
                item { Text("点击右上角添加多个 APK 文件", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                if (results.isNotEmpty()) {
                    Text("扫描结果（${results.size}）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "明细已保存为 .wxsnap 快照，列表仅展示概要以避免批量扫描占用过多内存；导出时会自动读回完整数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(results) { r ->
                SectionCard(title = r.displayName) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${r.apk.packageName} · ${r.apk.versionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("类 ${r.stats.classCount}｜方法 ${r.stats.methodCount}｜Hook 候选 ${r.hookCandidates.size}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val zip = withContext(Dispatchers.IO) {
                                        // 内存中只保留概要，导出时按需从快照读回完整数据
                                        val full = r.logFilePath
                                            ?.let { java.io.File(it) }
                                            ?.takeIf { it.isFile }
                                            ?.let { SnapshotManager.load(it) }
                                            ?: r
                                        ScanOrchestrator.export(context, full, com.johnny.wxdexinspector.data.AppPrefs.visualReport)
                                    }
                                    info = zip?.absolutePath ?: "导出失败"
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("导出该报告") }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    info?.let { InfoDialog("导出结果", it) { info = null } }
}

/** 批量模式只保留概要：多个完整结果同时驻留极易内存不足（完整明细已落盘为快照） */
private fun ScanResult.slimForBatch(): ScanResult = copy(
    classNames = emptyList(),
    methodSigns = emptyList(),
    fieldSigns = emptyList(),
    methods = emptyList(),
    keyStrings = keyStrings.take(200)
)
