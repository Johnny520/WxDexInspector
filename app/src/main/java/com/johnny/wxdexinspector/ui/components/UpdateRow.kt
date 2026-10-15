package com.johnny.wxdexinspector.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.core.update.UpdateChecker
import com.johnny.wxdexinspector.core.util.openUrl
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import com.johnny.wxdexinspector.ui.dialog.WxAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「检查更新」入口行：点击后请求 GitHub Releases，自动给出「已是最新」或「发现新版本」弹窗。
 * 可复用在设置页 / 关于页。
 */
@Composable
fun CheckUpdateActionRow(
    icon: ImageVector = Icons.Filled.SystemUpdate,
    title: String = "检查更新"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var latestMsg by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    ActionRow(if (checking) "正在检查更新…" else title, icon) {
        if (checking) return@ActionRow
        checking = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { UpdateChecker.check() } }
            checking = false
            r.onSuccess { info ->
                if (info.hasUpdate) update = info
                else latestMsg = "当前已是最新版本 v${info.currentVersion}"
            }.onFailure { e ->
                errorMsg = e.message ?: "网络异常"
            }
        }
    }

    update?.let { info ->
        WxAlertDialog(
            onDismissRequest = { update = null },
            title = "发现新版本 v${info.latestVersion}",
            confirmText = "前往下载",
            onConfirm = {
                context.openUrl(info.downloadUrl)
                update = null
            },
            dismissText = "稍后",
            onDismiss = { update = null },
            copyText = info.downloadUrl
        ) {
            Column {
                Text(
                    "当前 v${info.currentVersion} → 最新 v${info.latestVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("更新内容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(info.notes.take(1500), style = MaterialTheme.typography.bodySmall)
                }
                if (info.publishedAt.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "发布时间：${info.publishedAt.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    latestMsg?.let { InfoDialog("检查更新", it, onClose = { latestMsg = null }) }
    errorMsg?.let {
        InfoDialog(
            "检查更新失败",
            "无法连接更新服务器：$it\n\n可前往「项目主页」手动查看最新版本。",
            onClose = { errorMsg = null }
        )
    }
}
