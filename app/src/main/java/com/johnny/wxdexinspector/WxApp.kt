package com.johnny.wxdexinspector

import android.app.Application
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.data.AppPrefs

/**
 * 应用入口。初始化偏好、存储目录、日志。
 */
class WxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppPrefs.init(this)
        WxLogger.init(this)
        StorageManager.ensureDirs(this)
        WxLogger.i(TAG, "WxDexInspector 启动完成，版本 ${BuildConfig.VERSION_NAME}")
    }

    companion object {
        private const val TAG = "WxApp"

        @Volatile
        lateinit var instance: WxApp
            private set
    }
}
