package com.johnny.wxdexinspector.core.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 进程退出记录读取（Android 11+）。
 *
 * 价值：native 崩溃（SIGSEGV/SIGABRT）、被系统内存回收（LMK，SIGKILL）、ANR 这几种情况
 *      进程来不及写任何自己的日志，用户只看到「闪退」。系统侧其实保留了退出原因与 trace，
 *      这里把它读出来，用于精确定位「扫描闪退」到底是哪一种。
 */
object ExitInfoReader {

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** 读取最近的进程退出记录，返回可读文本 */
    fun describeRecent(context: Context, limit: Int = 5): String {
        if (!isSupported()) {
            return "当前系统为 Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}），\n" +
                "读取进程退出记录需要 Android 11（API 30）及以上。"
        }
        return runCatching {
            val am = context.getSystemService(ActivityManager::class.java)
                ?: return@runCatching "无法获取 ActivityManager"
            // 注意：getHistoricalProcessExitInfos() 未在公开 SDK 中导出，需反射调用；
            // 部分定制 ROM / 低版本系统未提供该接口，此时给出明确提示而不是崩溃。
            val list = queryExitInfos(am)
            if (list.isNullOrEmpty()) return@runCatching "未取到进程退出记录（系统未提供该接口，或确实没有异常退出）"
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val sb = StringBuilder()
            list.take(limit).forEachIndexed { i, info ->
                sb.appendLine("#${i + 1} · ${fmt.format(Date(info.timestamp))}")
                sb.appendLine("  原因  : ${reasonName(info.reason)}")
                info.description?.takeIf { it.isNotBlank() }?.let { sb.appendLine("  描述  : $it") }
                sb.appendLine("  内存  : PSS ${info.pss / 1024} MB / RSS ${info.rss / 1024} MB")
                traceSnippet(info)?.let { sb.appendLine("  Trace :\n$it") }
                sb.appendLine()
            }
            sb.toString().trim()
        }.getOrElse { "读取进程退出记录失败：${it.message}" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun queryExitInfos(am: ActivityManager): List<ApplicationExitInfo> = runCatching {
        val method = ActivityManager::class.java.getMethod("getHistoricalProcessExitInfos")
        val result = method.invoke(am)
        (result as? List<ApplicationExitInfo>) ?: emptyList()
    }.getOrElse { emptyList() }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "Java 崩溃（未捕获异常）"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native 崩溃（SIGSEGV/SIGABRT，常见于原生库解析）"
        ApplicationExitInfo.REASON_ANR -> "无响应（ANR）"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "被系统回收（内存不足，LMK）"
        ApplicationExitInfo.REASON_SIGNALED -> "被信号终止"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "资源占用过高被系统终止"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "用户主动结束"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "初始化失败"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "权限变更导致退出"
        ApplicationExitInfo.REASON_FREEZER -> "被冻结器终止"
        ApplicationExitInfo.REASON_OTHER -> "其他"
        else -> "未知（$reason）"
    }

    private fun traceSnippet(info: ApplicationExitInfo, maxLines: Int = 25): String? = runCatching {
        val stream = info.traceInputStream ?: return null
        val text = stream.bufferedReader().use { it.readText() }
        if (text.isBlank()) return null
        text.lineSequence().take(maxLines).joinToString("\n")
    }.getOrNull()
}
