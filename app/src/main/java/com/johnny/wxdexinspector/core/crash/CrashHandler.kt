package com.johnny.wxdexinspector.core.crash

import android.content.Context
import android.os.Build
import com.johnny.wxdexinspector.BuildConfig
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.log.WxLogger
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃捕获。
 *
 * 目的：①把未捕获异常（含 OutOfMemoryError）落盘到 crashes/ 目录，便于用户反馈与自诊断；
 *      ②在内存中保留最近一次崩溃文本，供「设置 → 稳定性」展示、复制。
 *
 * 说明：不吞异常——记录完成后仍交回原有 handler（默认行为），保证系统统计与进程退出流程不变。
 */
object CrashHandler {

    private const val TAG = "Crash"
    private const val PREFIX = "crash-"
    private const val MAX_KEEP = 10

    @Volatile
    private var installed = false

    @Volatile
    private var appCtx: Context? = null

    @Volatile
    private var lastText: String? = null

    @Volatile
    private var lastFile: File? = null

    /** 安装全局异常处理器（幂等） */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        appCtx = app
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(app, thread, throwable) }
            runCatching { WxLogger.e(TAG, "未捕获异常：${throwable.javaClass.name}: ${throwable.message}") }
            prev?.uncaughtException(thread, throwable)
        }
        runCatching { WxLogger.i(TAG, "崩溃捕获已安装") }
    }

    /** 最近一次崩溃文本：优先取本次进程内记录，否则读最新的崩溃文件 */
    fun lastCrashText(): String? {
        lastText?.let { return it }
        return runCatching { latestFile()?.takeIf { it.isFile }?.readText(Charsets.UTF_8) }.getOrNull()
    }

    /** 崩溃记录数量 */
    fun count(): Int = runCatching {
        dir()?.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) }?.size ?: 0
    }.getOrDefault(0)

    /** 清空崩溃记录，返回删除的数量 */
    fun clear(): Int = runCatching {
        val d = dir()
        var n = 0
        d?.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) }?.forEach {
            if (it.delete()) n++
        }
        lastText = null
        lastFile = null
        n
    }.getOrDefault(0)

    /** 崩溃记录目录（优先公共存储，失败回退应用私有目录） */
    fun crashDir(context: Context? = null): File? = runCatching {
        val app = context?.applicationContext ?: appCtx
        val pub = runCatching { StorageManager.crashesDir(app) }.getOrNull()
        if (pub != null && (pub.exists() || pub.mkdirs())) return pub
        val base = app?.filesDir ?: return pub
        File(base, "crashes").apply { if (!exists()) mkdirs() }
    }.getOrNull()

    private fun dir(): File? = crashDir(appCtx)

    private fun latestFile(): File? = runCatching {
        dir()?.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) }
            ?.maxByOrNull { it.lastModified() }
    }.getOrNull()

    private fun record(app: Context, thread: Thread, t: Throwable) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val sb = StringBuilder(2048)
        sb.appendLine("========= WxDexInspector 崩溃记录 =========")
        sb.appendLine("时间    : $ts")
        sb.appendLine("线程    : ${thread.name} (id=${thread.id})")
        sb.appendLine("异常    : ${t.javaClass.name}")
        sb.appendLine("消息    : ${t.message}")
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
        sb.appendLine("内存    : 已用 ${used} MB / 上限 ${rt.maxMemory() / 1024 / 1024} MB")
        sb.appendLine("版本    : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("设备    : ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("ABI     : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        if (t is OutOfMemoryError) {
            sb.appendLine("提示    : 内存不足。常见于解析超大 APK（如微信 >200MB）或明细数据量过大，")
            sb.appendLine("          可在「设置 → 拓展功能」关闭拓展检测后重试，或改用更小的 APK。")
        }
        sb.appendLine()
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        sb.append(sw.toString())
        val text = sb.toString()
        lastText = text

        val target = runCatching { crashDir(app) }.getOrNull()
            ?: File(app.filesDir, "crashes").apply { mkdirs() }
        val f = File(target, "$PREFIX${System.currentTimeMillis()}.txt")
        f.writeText(text, Charsets.UTF_8)
        lastFile = f

        // 仅保留最近 MAX_KEEP 条
        target.listFiles { x -> x.isFile && x.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_KEEP)
            ?.forEach { runCatching { it.delete() } }
    }
}
