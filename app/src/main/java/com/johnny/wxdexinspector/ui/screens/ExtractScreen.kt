package com.johnny.wxdexinspector.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.apk.ApkExtractor
import com.johnny.wxdexinspector.core.apk.AppRepository
import com.johnny.wxdexinspector.core.apk.ExtractResult
import com.johnny.wxdexinspector.core.apk.InstalledApp
import com.johnny.wxdexinspector.core.permission.PermissionManager
import com.johnny.wxdexinspector.data.model.RunMode
import com.johnny.wxdexinspector.ui.components.EmptyState
import com.johnny.wxdexinspector.ui.dialog.WxAlertDialog
import com.johnny.wxdexinspector.ui.theme.WxRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExtractScreen(
    onBack: () -> Unit,
    onOpenDetail: (String, String) -> Unit,
    onScan: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(RunMode.STATIC) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var extractingPkg by remember { mutableStateOf<String?>(null) }
    var resultDialog by remember { mutableStateOf<ExtractResult?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val r = withContext(Dispatchers.IO) {
                    ApkExtractor.importFromUri(context, uri, StorageManager.apksDir(context))
                }
                resultDialog = r
            }
        }
    }

    LaunchedEffect(tab, mode) {
        loading = true
        val actual = withContext(Dispatchers.IO) { PermissionManager.snapshot(context).effective }
        mode = actual
        val all = withContext(Dispatchers.IO) { AppRepository.listInstalled(context, includeSystem = true) }
        apps = when (tab) {
            1 -> all.filter { !it.isSystem }
            2 -> all.filter { it.isSystem }
            else -> all
        }
        loading = false
    }

    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text("APK 提取", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("当前模式：${mode.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                importLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
            }) { Icon(Icons.Filled.FolderOpen, contentDescription = "导入本地APK") }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("搜索应用名称或包名") },
            shape = RoundedCornerShape(WxRadius.button)
        )

        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("全部应用", "用户安装", "系统预装").forEachIndexed { i, label ->
                val enabled = !(i == 2 && mode != RunMode.ROOT)
                FilterChip(selected = tab == i, onClick = { if (enabled) tab = i }, enabled = enabled, label = { Text(label) })
            }
        }
        if (mode != RunMode.ROOT) {
            Text(
                "系统预装应用需 Root 模式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 18.dp, top = 4.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (filtered.isEmpty()) {
            EmptyState("未找到应用", Icons.Filled.Android)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppCard(
                        app = app,
                        extracting = extractingPkg == app.packageName,
                        onClick = { app.apkPath?.let { onOpenDetail(it, "installed") } },
                        onExtract = {
                            if (extractingPkg == null) {
                                extractingPkg = app.packageName
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) {
                                        ApkExtractor.extract(context, app, mode, StorageManager.apksDir(context), {})
                                    }
                                    extractingPkg = null
                                    resultDialog = r
                                }
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    when (val dlg = resultDialog) {
        is ExtractResult.Success -> WxAlertDialog(
            onDismissRequest = { resultDialog = null },
            title = "提取完成",
            confirmText = "直接扫描",
            onConfirm = {
                resultDialog = null
                dlg.files.firstOrNull()?.let { onScan(it, "extracted") }
            },
            dismissText = "仅保存返回列表",
            onDismiss = { resultDialog = null }
        ) {
            Column {
                Text("包名：${dlg.packageName}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text("保存路径：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                dlg.files.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (dlg.files.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text("共 ${dlg.files.size} 个分包文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        is ExtractResult.Failure -> WxAlertDialog(
            onDismissRequest = { resultDialog = null },
            title = "提取失败",
            confirmText = "知道了",
            onConfirm = { resultDialog = null }
        ) {
            Text(dlg.reason, style = MaterialTheme.typography.bodyMedium)
        }
        null -> Unit
    }
}

@Composable
private fun AppCard(
    app: InstalledApp,
    extracting: Boolean,
    onClick: () -> Unit,
    onExtract: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(WxRadius.card),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bmp = remember(app.packageName) {
                    app.icon?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                }
                if (bmp != null) {
                    Image(bmp, contentDescription = null, modifier = Modifier.size(44.dp))
                } else {
                    Box(
                        Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("版本 ${app.versionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (extracting) {
                    CircularProgressIndicator(Modifier.size(26.dp))
                } else {
                    Button(onClick = onExtract, shape = RoundedCornerShape(14.dp)) { Text("提取") }
                }
            }
            if (extracting) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
