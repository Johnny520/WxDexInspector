package com.johnny.wxdexinspector.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast

/** 从任意 Context 向上找到宿主 Activity */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/** 复制文本到剪贴板 */
fun Context.copyToClipboard(text: String, label: String = "WxDexInspector") {
    runCatching {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }
}
