package com.johnny.wxdexinspector.core.util

import java.io.File

/**
 * 系统内存信息（读取 /proc/meminfo）。
 *
 * 用于扫描前判断「是否值得冒险做深度解析」：可用内存明显不足时提前提示，
 * 而不是等进程被系统内存回收（表现为无日志的闪退）。
 */
object MemInfo {

    /** 系统可用内存（KB），读取失败返回 -1 */
    fun availableKb(): Long = runCatching {
        File("/proc/meminfo").useLines { lines ->
            lines.firstOrNull { it.startsWith("MemAvailable:") }
                ?.filter { it.isDigit() }
                ?.toLongOrNull()
                ?: -1L
        }
    }.getOrDefault(-1L)

    /** 系统可用内存（MB），读取失败返回 -1 */
    fun availableMb(): Long {
        val kb = availableKb()
        return if (kb < 0) -1L else kb / 1024
    }

    /** 可读文本 */
    fun availableText(): String {
        val mb = availableMb()
        return if (mb < 0) "未知" else "$mb MB"
    }
}
