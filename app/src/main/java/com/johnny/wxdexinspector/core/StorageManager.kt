package com.johnny.wxdexinspector.core

import android.content.Context
import android.os.Environment
import com.johnny.wxdexinspector.WxApp
import com.johnny.wxdexinspector.data.AppPrefs
import java.io.File

/**
 * 统一存储目录管理。
 *
 * /storage/emulated/0/WxDexInspector/
 *  ├─ apks/        提取的APK/APKS分包
 *  ├─ snapshots/   .wxsnap 快照
 *  ├─ exports/     导出ZIP报告包
 *  ├─ logs/        独立日志备份
 *  └─ temp/        扫描临时缓存
 */
object StorageManager {

    const val DIR_NAME = "WxDexInspector"

    @Volatile
    private var cachedRoot: File? = null

    /** 解析并返回存储根目录（优先公共存储，不可写时回退到应用专属外部目录）。 */
    fun rootDir(context: Context? = null): File {
        cachedRoot?.let { if (it.exists() || it.mkdirs()) return it }

        val pref = AppPrefs.storageRoot
        if (pref.isNotBlank()) {
            val f = File(pref)
            if (f.exists() || f.mkdirs()) {
                cachedRoot = f
                return f
            }
        }

        val primary = File(Environment.getExternalStorageDirectory(), DIR_NAME)
        if (canWrite(primary)) {
            cachedRoot = primary
            return primary
        }

        val ctx = context ?: runCatching { WxApp.instance }.getOrNull()
        val base = ctx?.getExternalFilesDir(null) ?: ctx?.filesDir
            ?: Environment.getExternalStorageDirectory()
        val fallback = File(base, DIR_NAME)
        fallback.mkdirs()
        cachedRoot = fallback
        return fallback
    }

    fun invalidateCache() {
        cachedRoot = null
    }

    /** 当前根目录的展示路径 */
    fun rootPath(context: Context? = null): String = rootDir(context).absolutePath

    fun apksDir(context: Context? = null): File = sub("apks", context)
    fun snapshotsDir(context: Context? = null): File = sub("snapshots", context)
    fun exportsDir(context: Context? = null): File = sub("exports", context)
    fun logsDir(context: Context? = null): File = sub("logs", context)
    fun tempDir(context: Context? = null): File = sub("temp", context)

    fun pluginsDir(context: Context? = null): File = sub("plugins", context)

    fun crashesDir(context: Context? = null): File = sub("crashes", context)

    private fun sub(name: String, context: Context?): File {
        val f = File(rootDir(context), name)
        if (!f.exists()) f.mkdirs()
        return f
    }

    /** 确保所有子目录存在 */
    fun ensureDirs(context: Context? = null): Boolean = runCatching {
        apksDir(context); snapshotsDir(context); exportsDir(context); logsDir(context); tempDir(context)
        pluginsDir(context); crashesDir(context)
        true
    }.getOrDefault(false)

    /** 清理 temp 目录，返回释放的字节数 */
    fun cleanTemp(context: Context? = null): Long {
        val dir = tempDir(context)
        var freed = 0L
        dir.listFiles()?.forEach { f ->
            freed += f.length()
            runCatching { f.deleteRecursively() }
        }
        return freed
    }

    private fun canWrite(dir: File): Boolean {
        return try {
            if (!dir.exists() && !dir.mkdirs()) return false
            val probe = File(dir, ".probe_${System.currentTimeMillis()}")
            val ok = probe.createNewFile()
            probe.delete()
            ok
        } catch (t: Throwable) {
            false
        }
    }
}
