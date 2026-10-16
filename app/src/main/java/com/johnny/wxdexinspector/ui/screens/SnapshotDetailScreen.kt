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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.johnny.wxdexinspector.core.snapshot.SnapshotManager
import com.johnny.wxdexinspector.core.util.copyToClipboard
import com.johnny.wxdexinspector.core.util.toast
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.dialog.ConfirmDialog
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 首次展示条数 / 每次「加载更多」追加条数 */
private const val PAGE_INITIAL = 200
private const val PAGE_STEP = 500

/** 搜索过滤结果上限（避免超大结果集拖慢 UI） */
private const val FILTER_LIMIT = 30000

/** 剪贴板单次复制上限（Binder 有 1MB 级别限制，超大会抛异常） */
private const val CLIP_LIMIT = 10000

private val BROWSE_TABS = listOf("类", "方法", "字段", "字符串", "Hook 候选")

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

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var shown by remember { mutableIntStateOf(PAGE_INITIAL) }

    LaunchedEffect(filePath) {
        loading = true
        result = withContext(Dispatchers.IO) { SnapshotManager.load(File(filePath)) }
        loading = false
    }

    val r = result

    // 当前分类的完整列表（仅保存引用/必要时生成一次）
    val currentList: List<String> = remember(r, tab) {
        val res = r ?: return@remember emptyList()
        when (tab) {
            0 -> res.classNames
            1 -> res.methodSigns
            2 -> res.fieldSigns
            3 -> res.keyStrings
            else -> res.hookCandidates.map { "[${it.confidence}] ${it.description}  →  ${it.className}->${it.methodName}" }
        }
    }
    val filtered: List<String> = remember(currentList, query) {
        if (query.isBlank()) currentList
        else currentList.asSequence()
            .filter { it.contains(query, ignoreCase = true) }
            .take(FILTER_LIMIT)
            .toList()
    }
    // 切换分类/改搜索词时重置展示条数
    LaunchedEffect(tab, query) { shown = PAGE_INITIAL }

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
                        if (r.truncated) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "注：该快照为未完成分段扫描或明细已达容量上限，数字为统计值。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
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

            // ---------- 内容浏览（可看全部：分页加载 + 搜索 + 复制） ----------
            item {
                SectionCard(title = "内容浏览（共 ${currentList.size} 条，已显示 ${minOf(shown, filtered.size)} 条）") {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            BROWSE_TABS.forEachIndexed { i, label ->
                                TextButton(onClick = { tab = i }) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                                        color = if (tab == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索关键字（类名 / 方法签名 / 字符串）") }
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (query.isBlank()) "点击任意条目复制" else "匹配 ${filtered.size} 条" + if (filtered.size >= FILTER_LIMIT) "（已达上限）" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                val payload = filtered.take(CLIP_LIMIT).joinToString("\n")
                                context.copyToClipboard(payload, BROWSE_TABS[tab])
                                context.toast(
                                    if (filtered.size > CLIP_LIMIT) "已复制前 $CLIP_LIMIT 条（完整列表请用「导出ZIP」）"
                                    else "已复制 ${filtered.size} 条"
                                )
                            }) { Text("复制当前列表") }
                        }
                    }
                }
            }

            val shownCount = minOf(shown, filtered.size)
            items(shownCount) { index ->
                val text = filtered[index]
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.copyToClipboard(text, BROWSE_TABS[tab])
                            context.toast("已复制")
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            if (filtered.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (shownCount < filtered.size) {
                            OutlinedButton(
                                onClick = { shown += PAGE_STEP },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("加载更多（剩余 ${filtered.size - shownCount} 条）") }
                        } else {
                            Text(
                                "已显示全部 ${filtered.size} 条",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
