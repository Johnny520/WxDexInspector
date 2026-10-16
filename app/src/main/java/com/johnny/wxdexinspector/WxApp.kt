package com.johnny.wxdexinspector

import android.app.Application
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.core.crash.CrashHandler
import com.johnny.wxdexinspector.core.plugin.PluginRegistry
import com.johnny.wxdexinspector.core.scan.ScanTrace
import com.johnny.wxdexinspector.data.AppPrefs

/**
 * 应用入口。初始化偏好、存储目录、日志。
 */
class WxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashHandler.install(this)
        AppPrefs.init(this)
        // 上次扫描若被强杀（native 崩溃 / 内存回收），这里归档出可读记录
        ScanTrace.consumeLeftover(this)?.let { killed ->
            WxLogger.e(TAG, "检测到上次扫描被强制终止：\n$killed")
        }
        WxLogger.init(this)
        StorageManager.ensureDirs(this)
        PluginRegistry.ensureLoaded(this)
        WxLogger.i(TAG, "WxDexInspector 启动完成，版本 ${BuildConfig.VERSION_NAME}")
    }

    companion object {
        private const val TAG = "WxApp"

        @Volatile
        lateinit var instance: WxApp
            private set
    }
}
