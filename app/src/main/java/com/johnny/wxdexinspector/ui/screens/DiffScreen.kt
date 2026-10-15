package com.johnny.wxdexinspector.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.core.scan.DiffEngine
import com.johnny.wxdexinspector.core.scan.DiffResult
import com.johnny.wxdexinspector.core.scan.ScanResult
import com.johnny.wxdexinspector.core.snapshot.SnapshotManager
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.dialog.WxAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DiffScreen(
    initialFile: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var baseFile by remember { mutableStateOf<File?>(initialFile?.let { File(it) }) }
    var targetFile by remember { mutableStateOf<File?>(null) }
    var diff by remember { mutableStateOf<DiffResult?>(null) }
    var running by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(0) } // 1=base 2=target

    LaunchedEffect(Unit) {
        files = withContext(Dispatchers.IO) { SnapshotManager.list(context) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("快照差异对比", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerRow("基准快照（旧）", baseFile?.name) { picking = 1 }
            PickerRow("对比快照（新）", targetFile?.name) { picking = 2 }
            Button(
                onClick = {
                    val b = baseFile; val t = targetFile
                    if (b == null || t == null) return@Button
                    running = true
                    scope.launch {
                        diff = withContext(Dispatchers.IO) {
                            val rb = SnapshotManager.load(b); val rt = SnapshotManager.load(t)
                            if (rb == null || rt == null) null else DiffEngine.diff(rb, rt)
                        }
                        running = false
                    }
                },
                enabled = baseFile != null && targetFile != null && !running,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("开始对比") }
        }

        Spacer(Modifier.height(8.dp))

        if (running) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val d = diff
            if (d == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("选择两个快照后开始对比", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("${d.baseName}  →  ${d.targetName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    item { DiffSection("类", d.addedClasses, d.removedClasses) }
                    item { DiffSection("方法", d.addedMethods, d.removedMethods) }
                    item { DiffSection("字段", d.addedFields, d.removedFields) }
                    item {
                        SectionCard(title = "Hook 候选变化") {
                            Column(Modifier.padding(14.dp)) {
                                Text("新增 ${d.addedHooks.size} 个 / 消失 ${d.removedHooks.size} 个", style = MaterialTheme.typography.bodyMedium)
                                d.addedHooks.take(30).forEach { Text("+ ${it.className}->${it.methodName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                                d.removedHooks.take(30).forEach { Text("- ${it.className}->${it.methodName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }

    if (picking != 0) {
        WxAlertDialog(
            onDismissRequest = { picking = 0 },
            title = "选择快照",
            confirmText = "关闭",
            onConfirm = { picking = 0 },
            scrollable = true
        ) {
            Column {
                if (files.isEmpty()) Text("暂无快照")
                files.forEach { f ->
                    Text(
                        f.nameWithoutExtension,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (picking == 1) baseFile = f else targetFile = f
                            picking = 0
                        }.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, value: String?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Compare, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.padding(6.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value ?: "点击选择", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DiffSection(title: String, added: List<String>, removed: List<String>) {
    SectionCard(title = "$title 变化（+${added.size} / -${removed.size}）") {
        Column(Modifier.padding(14.dp)) {
            if (added.isEmpty() && removed.isEmpty()) {
                Text("无变化", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                added.take(60).forEach { Text("+ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                removed.take(60).forEach { Text("- $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
