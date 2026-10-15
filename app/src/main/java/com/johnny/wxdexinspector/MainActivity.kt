package com.johnny.wxdexinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.core.permission.PermissionManager
import com.johnny.wxdexinspector.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.filterValues { granted -> !granted }.keys
            if (denied.isEmpty()) {
                WxLogger.i("MainActivity", "运行时权限已全部授予")
            } else {
                WxLogger.w("MainActivity", "以下权限被拒绝：$denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
        requestRuntimePermissions()
    }

    /** 启动即申请必要运行时权限（通知 / 存储读取） */
    private fun requestRuntimePermissions() {
        val perms = PermissionManager.runtimePermissions()
        if (perms.isEmpty()) return
        runCatching { permissionLauncher.launch(perms) }
            .onFailure { WxLogger.e("MainActivity", "申请运行时权限失败", it) }
    }
}
