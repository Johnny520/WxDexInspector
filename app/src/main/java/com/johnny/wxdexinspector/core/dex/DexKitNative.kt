package com.johnny.wxdexinspector.core.dex

import com.johnny.wxdexinspector.core.log.WxLogger

/**
 * DexKit 原生库加载与诊断。
 *
 * 正常情况 DexKit 会自行加载 libdexkit.so，但个别设备/ROM/安装器下自动加载会失效，
 * 表现为 `No implementation found for ... nativeInitDexKit`。
 * 这里在调用前显式加载一次（System.loadLibrary 幂等），并记录结果以便给出准确的中文提示。
 */
object DexKitNative {

    private const val TAG = "DexKitNative"
    private const val LIB = "dexkit"

    @Volatile
    private var loaded: Boolean? = null

    @Volatile
    private var detail: String = "未检测"

    val statusText: String
        get() = detail

    @Synchronized
    fun ensureLoaded(): Boolean {
        loaded?.let { return it }
        val ok = try {
            System.loadLibrary(LIB)
            detail = "已成功加载 libdexkit.so"
            true
        } catch (t: Throwable) {
            detail = t.message ?: t.javaClass.simpleName
            false
        }
        loaded = ok
        if (ok) WxLogger.i(TAG, detail) else WxLogger.e(TAG, "libdexkit.so 加载失败：$detail")
        return ok
    }
}
