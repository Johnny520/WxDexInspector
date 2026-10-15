package com.johnny.wxdexinspector.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.data.model.LogLevel
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import com.johnny.wxdexinspector.ui.theme.LogDebugColor
import com.johnny.wxdexinspector.ui.theme.LogErrorColor
import com.johnny.wxdexinspector.ui.theme.LogInfoColor
import com.johnny.wxdexinspector.ui.theme.LogWarnColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue

@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var logs by remember { mutableStateOf(WxLogger.allEntries()) }
    var info by remember { mutableStateOf<String?>(null) }
    val queue = remember { ConcurrentLinkedQueue<WxLogger.LogEntry>() }

    LaunchedEffect(Unit) {
        val listener: (WxLogger.LogEntry) -> Unit = { queue.add(it) }
        WxLogger.addListener(listener)
        try {
            while (isActive) {
                delay(200)
                if (queue.isNotEmpty()) {
                    val add = ArrayList<WxLogger.LogEntry>()
                    while (true) {
                        val e = queue.poll() ?: break
                        add.add(e)
                    }
                    if (add.isNotEmpty()) logs = (logs + add).takeLast(2000)
                }
            }
        } finally {
            WxLogger.removeListener(listener)
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text("运行日志", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("共 ${logs.size} 条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                scope.launch {
                    val f = withContext(Dispatchers.IO) {
                        WxLogger.archiveTo(StorageManager.logsDir(context), "manual_${System.currentTimeMillis()}.txt")
                    }
                    info = f?.absolutePath ?: "保存失败"
                }
            }) { Icon(Icons.Filled.Save, contentDescription = "保存日志") }
            IconButton(onClick = { WxLogger.clear(); logs = emptyList() }) {
                Icon(Icons.Filled.Delete, contentDescription = "清空")
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(logs) { entry ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        entry.time,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        entry.level.name.take(1),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = levelColor(entry.level),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        entry.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = levelColor(entry.level)
                    )
                }
            }
        }
    }

    info?.let { InfoDialog("日志已保存", it) { info = null } }
}

private fun levelColor(level: LogLevel) = when (level) {
    LogLevel.INFO -> LogInfoColor
    LogLevel.WARN -> LogWarnColor
    LogLevel.ERROR -> LogErrorColor
    LogLevel.DEBUG -> LogDebugColor
}
