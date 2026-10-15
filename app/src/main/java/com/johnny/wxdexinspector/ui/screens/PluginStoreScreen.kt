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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.core.plugin.InstalledPlugin
import com.johnny.wxdexinspector.core.plugin.PluginInfo
import com.johnny.wxdexinspector.core.plugin.PluginManager
import com.johnny.wxdexinspector.core.plugin.PluginRepository
import com.johnny.wxdexinspector.core.plugin.PluginType
import com.johnny.wxdexinspector.core.util.openUrl
import com.johnny.wxdexinspector.core.util.toast
import com.johnny.wxdexinspector.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 在线插件商店：安装 / 卸载 / 启停「数据插件」（加固、SDK、Hook 特征包） */
@Composable
fun PluginStoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var remote by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    var installed by remember { mutableStateOf<List<InstalledPlugin>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        loading = true
        error = null
        installed = withContext(Dispatchers.IO) { PluginManager.installed(context) }
        val r = withContext(Dispatchers.IO) { runCatching { PluginRepository.fetchIndex() } }
        r.onSuccess { remote = it }.onFailure { error = it.message ?: "网络异常" }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text("插件商店", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "在线 ${remote.size} 个 · 已安装 ${installed.size} 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { tick++ }) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        val installedIds = installed.map { it.info.id }.toSet()

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (installed.isNotEmpty()) {
                item {
                    Text("已安装", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                items(installed, key = { "i_" + it.info.id }) { p ->
                    SectionCard {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.info.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${PluginType.label(p.info.type)} · v${p.info.version} · ${p.itemCount} 条规则",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = p.enabled,
                                    onCheckedChange = { on ->
                                        PluginManager.setEnabled(context, p.info.id, on)
                                        installed = PluginManager.installed(context)
                                        context.toast(if (on) "已启用" else "已停用")
                                    }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(onClick = {
                                if (PluginManager.uninstall(context, p.info.id)) {
                                    installed = PluginManager.installed(context)
                                    context.toast("已卸载")
                                } else context.toast("卸载失败")
                            }) { Text("卸载") }
                        }
                    }
                }
            }

            item {
                Text("在线插件", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }

            if (error != null) {
                item {
                    SectionCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("加载失败：$error", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "请检查网络后点击右上角刷新重试。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (remote.isEmpty() && error == null) {
                item {
                    SectionCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("暂无可安装插件", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            items(remote, key = { "r_" + it.id }) { info ->
                val isInstalled = installedIds.contains(info.id)
                val isBusy = busy == info.id
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(info.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "${info.author} · v${info.version} · ${PluginType.label(info.type)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (info.description.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(info.description, style = MaterialTheme.typography.bodySmall)
                        }
                        if (info.tags.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                info.tags.joinToString("  ") { "#$it" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = !isInstalled && !isBusy,
                            onClick = {
                                busy = info.id
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) {
                                        runCatching { PluginManager.install(context, info) }
                                    }
                                    busy = null
                                    installed = PluginManager.installed(context)
                                    r.onSuccess { context.toast("已安装：${it.info.name}") }
                                        .onFailure { context.toast("安装失败：${it.message}") }
                                }
                            }
                        ) {
                            Text(
                                when {
                                    isInstalled -> "已安装"
                                    isBusy -> "安装中…"
                                    else -> "安装"
                                }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "关于插件") {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "插件只提供静态特征数据（加固特征、SDK 特征、微信 Hook 特征），安装后可扩展识别能力。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "本商店仅收录合规的分析增强插件，不提供破解、脱壳、重打包、篡改类内容。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton(onClick = { context.openUrl(PluginRepository.STORE_HOME) }) { Text("商店仓库") }
                            TextButton(onClick = { context.openUrl(PluginRepository.SUBMIT_URL) }) { Text("提交插件") }
                        }
                    }
                }
            }
        }
    }
}
