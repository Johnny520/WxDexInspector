package com.johnny.wxdexinspector.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.data.model.ScanMode
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.components.SettingRow
import com.johnny.wxdexinspector.ui.components.StatusPill

@Composable
fun HomeScreen(
    onOpenStatus: () -> Unit,
    onOpenMode: (ScanMode) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "WxDexInspector",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "微信APK静态分析工具箱",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(
                text = AppPrefs.workMode.label.take(6),
                ok = true,
                onClick = onOpenStatus
            )
        }

        Spacer(Modifier.height(8.dp))

        SectionCard(title = "扫描模式") {
            ScanMode.entries.forEach { mode ->
                SettingRow(
                    title = mode.label,
                    subtitle = mode.desc,
                    icon = scanModeIcon(mode),
                    onClick = { onOpenMode(mode) }
                )
            }
        }

        SectionCard(title = "快捷操作") {
            SettingRow(
                title = "导入外部APK扫描",
                subtitle = "纯静态模式即可使用，无需授权",
                icon = Icons.Filled.FolderOpen,
                onClick = { onOpenMode(ScanMode.SINGLE) }
            )
            SettingRow(
                title = "提取本机应用",
                subtitle = "Shizuku / Root 模式支持",
                icon = Icons.Filled.Download,
                onClick = { onOpenMode(ScanMode.SINGLE) }
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun scanModeIcon(mode: ScanMode): ImageVector = when (mode) {
    ScanMode.SINGLE -> Icons.Filled.Search
    ScanMode.DIFF -> Icons.Filled.Compare
    ScanMode.BATCH -> Icons.Filled.Layers
    ScanMode.SNAPSHOT_DIFF -> Icons.Filled.History
    ScanMode.SNAPSHOT_BROWSE -> Icons.Filled.List
}
