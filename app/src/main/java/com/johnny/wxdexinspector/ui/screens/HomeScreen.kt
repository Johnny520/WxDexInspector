package com.johnny.wxdexinspector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.data.model.ScanMode
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.components.SettingRow
import com.johnny.wxdexinspector.ui.components.StatusPill

@Composable
fun HomeScreen(
    modeLabel: String,
    onOpenStatus: () -> Unit,
    onOpenMode: (ScanMode) -> Unit,
    onOpenExtract: () -> Unit,
    onOpenLogs: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        // 顶部品牌卡
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Wx",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "WxDexInspector",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "微信 APK 静态分析工具箱",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
                StatusPill(text = modeLabel.take(7), ok = true, onClick = onOpenStatus)
            }
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
            SettingRow("导入 / 提取 APK", "纯静态导入或 Shizuku/Root 提取", Icons.Filled.Download, onClick = onOpenExtract)
            SettingRow("打开存储文件夹", "查看导出报告与快照", Icons.Filled.FolderOpen, onClick = onOpenExtract)
            SettingRow("运行日志", "彩色分级实时日志", Icons.Filled.Terminal, onClick = onOpenLogs)
        }

        Spacer(Modifier.height(104.dp))
    }
}

private fun scanModeIcon(mode: ScanMode): ImageVector = when (mode) {
    ScanMode.SINGLE -> Icons.Filled.Search
    ScanMode.DIFF -> Icons.Filled.Compare
    ScanMode.BATCH -> Icons.Filled.Layers
    ScanMode.SNAPSHOT_DIFF -> Icons.Filled.History
    ScanMode.SNAPSHOT_BROWSE -> Icons.Filled.List
}
