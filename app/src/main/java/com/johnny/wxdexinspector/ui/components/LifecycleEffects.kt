package com.johnny.wxdexinspector.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

/**
 * 页面回到前台（ON_RESUME）时回调。
 * 用于「权限状态实时更新」：用户去系统设置授权后切回来即可立即刷新。
 */
@Composable
fun OnResume(onResume: () -> Unit) {
    val view = LocalView.current
    val owner = remember(view) { view.findViewTreeLifecycleOwner() }
    val callback by rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) callback()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
}
