package com.johnny.wxdexinspector.ui.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.ui.theme.WxRadius

/** 统一圆角弹窗基座（淡入淡出由 AlertDialog 默认动画提供） */
@Composable
fun WxAlertDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    confirmText: String = "确定",
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    scrollable: Boolean = false,
    content: @Composable () -> Unit
) {
    val titleBlock: (@Composable () -> Unit)? = if (title != null) {
        val t = title
        { Text(t, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
    } else {
        null
    }

    val dismissBlock: (@Composable () -> Unit)? = if (dismissText != null && onDismiss != null) {
        val dt = dismissText
        val cb = onDismiss
        { TextButton(onClick = cb) { Text(dt) } }
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(WxRadius.dialog),
        title = titleBlock,
        text = {
            if (scrollable) {
                Box(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    content()
                }
            } else {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = dismissBlock
    )
}

/** 通用信息弹窗 */
@Composable
fun InfoDialog(
    title: String,
    message: String,
    onClose: () -> Unit = {}
) {
    WxAlertDialog(
        onDismissRequest = onClose,
        title = title,
        confirmText = "知道了",
        onConfirm = onClose,
        scrollable = true
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 确认弹窗 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    dismissText: String = "取消"
) {
    WxAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = dismissText,
        onDismiss = onDismiss
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
