package com.johnny.wxdexinspector.core.scan

import android.content.Context
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.crash.CrashHandler
import com.johnny.wxdexinspector.core.util.MemInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 扫描痕迹标记。
 *
 * 问题：native 崩溃 / 被系统内存回收（SIGKILL）时，进程在毫秒级消失，来不及执行任何
 *      清理与日志写入，用户侧只看到「闪退」，连崩溃记录都留不下。
 *
 * 方案：扫描开始时就落盘一条「扫描进行中」标记，正常结束（含可捕获异常）时删除；
 *      若下次启动时该标记仍存在，说明上次扫描是被强杀的 —— 据此归档一条可读记录，
 *      并在应用启动时即可告知用户可能原因。
 */
object ScanTrace {

    private const val NAME = "scan_inprogress.txt"

    fun file(context: Context): File = File(StorageManager.tempDir(context), NAME)

    /** 扫描开始时写入标记 */
    fun begin(context: Context, apkPath: String, apkSize: Long, deepDex: Boolean) {
        runCatching {
            val rt = Runtime.getRuntime()
            val used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
            val text = buildString {
                appendLine("WxDexInspector · 上次扫描未正常结束")
                appendLine("开始时间    : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("APK 路径    : $apkPath")
                appendLine("APK 大小    : ${apkSize / 1024 / 1024} MB")
                appendLine("深度解析    : ${if (deepDex) "是" else "否（仅基础信息）"}")
                appendLine("应用堆内存  : 已用 $used MB / 上限 ${rt.maxMemory() / 1024 / 1024} MB")
                appendLine("系统可用内存: ${MemInfo.availableText()}")
            }
            file(context).writeText(text, Charsets.UTF_8)
        }
    }

    /** 扫描正常/可捕获失败时删除标记 */
    fun end(context: Context) {
        runCatching { file(context).delete() }
    }

    /**
     * 启动时消费残留标记：存在则说明上次扫描进程被强杀。
     * 会归档为一条崩溃记录（crashes/scan-killed-*.txt），返回该标记文本。
     */
    fun consumeLeftover(context: Context): String? = runCatching {
        val f = file(context)
        if (!f.isFile) return@runCatching null
        val raw = runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("")
        f.delete()
        val dir = CrashHandler.crashDir(context)
        if (dir != null) {
            runCatching {
                File(dir, "scan-killed-${System.currentTimeMillis()}.txt").writeText(
                    raw + "\n" +
                        "结论        : 扫描过程中进程被强制终止，未产生 Java 异常记录。\n" +
                        "常见原因    : ① Native 内存不足被系统回收（超大 APK，如微信 >200MB）；\n" +
                        "              ② dexkit 原生库解析异常（SIGSEGV / SIGABRT）。\n" +
                        "下一步      : 「设置 → 稳定性 → 进程退出记录」可查看系统记录的原因与 trace。\n",
                    Charsets.UTF_8
                )
            }
        }
        raw.trim()
    }.getOrNull()
}
