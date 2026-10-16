package com.johnny.wxdexinspector.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.core.snapshot.SnapshotManager
import com.johnny.wxdexinspector.core.util.toast
import com.johnny.wxdexinspector.ui.components.EmptyState
import com.johnny.wxdexinspector.ui.components.OnResume
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.components.SettingRow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onOpenSnapshot: (String) -> Unit,
    onOpenDiff: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val context = LocalContext.current
    var snapshots by remember { mutableStateOf<List<File>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var userRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        snapshots = SnapshotManager.list(context)
        if (userRefresh) {
            userRefresh = false
            context.toast("已刷新：共 ${snapshots.size} 个快照")
        }
    }

    // 切回前台时自动刷新（例如在文件管理器里手动放入快照）
    OnResume { refreshKey++ }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("历史快照", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("共 ${snapshots.size} 个 .wxsnap 快照", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { userRefresh = true; refreshKey++ }) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") }
        }

        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenDiff) {
                Icon(Icons.Filled.Compare, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("快照对比")
            }
            TextButton(onClick = onOpenLogs) {
                Icon(Icons.Filled.Terminal, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("运行日志")
            }
        }

        Spacer(Modifier.height(4.dp))

        if (snapshots.isEmpty()) {
            EmptyState("暂无快照，完成一次完整扫描后将自动生成 .wxsnap", Icons.Filled.History)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp)
            ) {
                items(snapshots, key = { it.absolutePath }) { f ->
                    SectionCard {
                        SettingRow(
                            title = f.nameWithoutExtension,
                            subtitle = "${fmt(f.lastModified())} · ${humanSize(f.length())}",
                            icon = Icons.Filled.History,
                            onClick = { onOpenSnapshot(f.absolutePath) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun fmt(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

private fun humanSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
