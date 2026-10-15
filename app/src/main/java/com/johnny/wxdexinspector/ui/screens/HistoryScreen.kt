package com.johnny.wxdexinspector.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.ui.components.EmptyState
import com.johnny.wxdexinspector.ui.components.SettingRow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    var snapshots by remember { mutableStateOf<List<File>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        snapshots = loadSnapshots(context)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("历史快照", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "共 ${snapshots.size} 个 .wxsnap 快照",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { refreshKey++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        }

        Spacer(Modifier.height(6.dp))

        if (snapshots.isEmpty()) {
            EmptyState("暂无快照，完成一次完整扫描后将自动生成 .wxsnap", Icons.Filled.History)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(snapshots, key = { it.absolutePath }) { f ->
                    SnapshotRow(f)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SnapshotRow(f: File) {
    com.johnny.wxdexinspector.ui.components.SectionCard {
        SettingRow(
            title = f.nameWithoutExtension,
            subtitle = "${fmt(f.lastModified())} · ${humanSize(f.length())}",
            icon = Icons.Filled.History,
            onClick = { }
        )
    }
}

private fun loadSnapshots(context: android.content.Context): List<File> {
    val dir = StorageManager.snapshotsDir(context)
    return dir.listFiles { f -> f.isFile && f.name.endsWith(".wxsnap") }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

private fun fmt(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

private fun humanSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
