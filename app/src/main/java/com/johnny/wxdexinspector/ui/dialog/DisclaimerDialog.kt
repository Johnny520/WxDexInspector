package com.johnny.wxdexinspector.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 首次打开仅弹出一次的免责声明 */
@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit
) {
    WxAlertDialog(
        onDismissRequest = { /* 强制阅读，必须点击同意 */ },
        title = "免责声明 & 使用须知",
        confirmText = "我已阅读并同意",
        onConfirm = onAccept,
        scrollable = true
    ) {
        Column {
            Item("1. 本软件主要用途：微信APK静态分析，用于微信LSP模块开发学习、版本适配。")
            Item("2. 附带可选拓展功能，可查看其他安卓应用的静态信息；拓展仅为顺手附加能力，并非本软件核心目标。")
            Item("3. 本软件仅对APK进行静态文件数据分析，不提供APK修改、重打包、脱壳、去广告、破解应用的功能，仅读取展示APK内静态信息。")
            Item("4. 使用者获取分析结果后，自行使用其他工具进行二次操作产生的所有后果，全部由使用者自行承担。")
            Item("5. 禁止用于侵犯软件著作权、商业破解、非法用途。")
            Spacer(Modifier.height(6.dp))
            Text(
                "使用本软件即代表同意全部条款。",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun Item(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 3.dp)
    )
}

/** 完整免责声明文本（供设置页 / 关于页复用） */
val DISCLAIMER_TEXT: String = """
免责声明 & 使用须知

1. 本软件主要用途：微信APK静态分析，用于微信LSP模块开发学习、版本适配。
2. 附带可选拓展功能，可查看其他安卓应用的静态信息；拓展仅为顺手附加能力，并非本软件核心目标。
3. 本软件仅对APK进行静态文件数据分析，不提供APK修改、重打包、脱壳、去广告、破解应用的功能，仅读取展示APK内静态信息。
4. 使用者获取分析结果后，自行使用其他工具进行二次操作产生的所有后果，全部由使用者自行承担。
5. 禁止用于侵犯软件著作权、商业破解、非法用途。

使用本软件即代表同意全部条款。
""".trimIndent()
