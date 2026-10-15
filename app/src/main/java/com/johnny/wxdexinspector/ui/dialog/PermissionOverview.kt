package com.johnny.wxdexinspector.ui.dialog

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.core.permission.PermissionManager
import com.johnny.wxdexinspector.core.util.toast
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.data.model.PermState
import com.johnny.wxdexinspector.data.model.WorkMode
import com.johnny.wxdexinspector.ui.components.RadioRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 运行模式 + 权限总览。
 * - showModeSelector = true 时顶部展示工作模式单选（用于首页状态胶囊点击）
 * - 权限列表实时反映当前状态，可「一键申请全部」
 */
@Composable
fun PermissionOverviewDialog(
    onDismiss: () -> Unit,
    showModeSelector: Boolean = false,
    onModeChange: ((WorkMode) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<PermissionManager.PermItem>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(AppPrefs.workMode) }

    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    LaunchedEffect(tick) {
        items = withContext(Dispatchers.IO) { PermissionManager.checklist(context, doRootProbe = false) }
    }

    WxAlertDialog(
        onDismissRequest = onDismiss,
        title = if (showModeSelector) "运行模式与权限" else "权限总览",
        confirmText = if (busy) "申请中…" else "一键申请全部",
        onConfirm = {
            if (busy) return@WxAlertDialog
            scope.launch {
                busy = true
                val snap = withContext(Dispatchers.IO) { PermissionManager.snapshot(context, doRootProbe = true) }
                when {
                    !PermissionManager.isStorageGranted(context) -> {
                        context.toast("请授予「所有文件访问」权限")
                        PermissionManager.openAllFilesSettings(context)
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !PermissionManager.isNotificationGranted(context) -> {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    snap.root != PermState.GRANTED && PermissionManager.isRootAvailable() -> {
                        val ok = withContext(Dispatchers.IO) { PermissionManager.requestRoot() }
                        context.toast(if (ok) "✅ 已获得 Root 权限" else "未获得 Root 权限（可能被拒绝）")
                    }
                    snap.shizuku == PermState.DENIED ->
                        PermissionManager.requestShizukuPermission { ok ->
                            context.toast(if (ok) "Shizuku 已授权" else "Shizuku 授权失败或被拒绝")
                            tick++
                        }
                    else -> context.toast("权限已全部就绪")
                }
                busy = false
                tick++
            }
        },
        dismissText = "关闭",
        onDismiss = onDismiss,
        scrollable = true
    ) {
        Column {
            if (showModeSelector) {
                Text(
                    "工作模式",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                WorkMode.entries.forEach { m ->
                    RadioRow(
                        title = m.label,
                        selected = m == selectedMode,
                        onClick = {
                            if (m != selectedMode) {
                                selectedMode = m
                                AppPrefs.workMode = m
                                onModeChange?.invoke(m)
                                tick++
                            }
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
            }

            Text(
                "权限状态（必需项已标注）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            items.forEach { item -> PermRow(item) }
            Spacer(Modifier.height(4.dp))
            Text(
                "提示：修改系统权限后回到本页即会自动刷新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermRow(item: PermissionManager.PermItem) {
    val color = when (item.state) {
        PermState.GRANTED -> Color(0xFF2FBF71)
        PermState.DENIED -> MaterialTheme.colorScheme.error
        PermState.UNAVAILABLE -> MaterialTheme.colorScheme.outline
        PermState.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (item.required) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "必需",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (item.hint.isNotBlank()) {
                Text(
                    item.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            when (item.state) {
                PermState.GRANTED -> "已就绪"
                PermState.DENIED -> "未授权"
                PermState.UNAVAILABLE -> "不可用"
                PermState.UNKNOWN -> "检测中"
            },
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}
