package com.johnny.wxdexinspector.core.log

import android.content.Context
import android.util.Log
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.data.model.LogLevel
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 彩色分级日志系统。
 * - INFO / WARN / ERROR 分级，UI 按级别着色
 * - 内存环形缓冲供实时日志面板使用
 * - 持久化到 logs/wxdi-YYYY-MM-DD.log（仅 I 及以上），受「独立备份日志」开关控制
 */
object WxLogger {

    data class LogEntry(
        val millis: Long,
        val time: String,
        val level: LogLevel,
        val tag: String,
        val message: String
    )

    private const val MAX_BUFFER = 3000
    private const val TAG_PREFIX = "WxDI:"

    private val buffer = ArrayDeque<LogEntry>(MAX_BUFFER)
    private val listeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()
    private val lock = Any()

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val fileFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    @Volatile
    private var context: Context? = null

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var currentLogFile: File? = null

    @Volatile
    private var currentLogDate: String = ""

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun d(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(LogLevel.WARN, tag, msg)
    fun e(tag: String, msg: String, tr: Throwable? = null) =
        log(LogLevel.ERROR, tag, if (tr != null) "$msg\n${Log.getStackTraceString(tr)}" else msg)

    private fun log(level: LogLevel, tag: String, msg: String) {
        val now = System.currentTimeMillis()
        val entry = LogEntry(now, timeFmt.format(Date(now)), level, tag, msg)

        when (level) {
            LogLevel.DEBUG -> Log.d(TAG_PREFIX + tag, msg)
            LogLevel.INFO -> Log.i(TAG_PREFIX + tag, msg)
            LogLevel.WARN -> Log.w(TAG_PREFIX + tag, msg)
            LogLevel.ERROR -> Log.e(TAG_PREFIX + tag, msg)
        }

        synchronized(lock) {
            if (buffer.size >= MAX_BUFFER) buffer.removeFirst()
            buffer.addLast(entry)
            if (level != LogLevel.DEBUG) writeToFile(entry)
        }

        listeners.forEach { l -> runCatching { l(entry) } }
    }

    private fun writeToFile(entry: LogEntry) {
        if (!AppPrefs.backupLog) return
        val ctx = context ?: return
        try {
            val today = dateFmt.format(Date())
            if (today != currentLogDate || writer == null) {
                closeWriter()
                val dir = StorageManager.logsDir(ctx)
                val f = File(dir, "wxdi-$today.log")
                if (!f.exists()) f.createNewFile()
                currentLogFile = f
                currentLogDate = today
                writer = FileOutputStream(f, true).bufferedWriter()
            }
            writer?.apply {
                write("[${fileFmt.format(Date(entry.millis))}] ${entry.level.name}/${entry.tag}: ${entry.message}")
                newLine()
                flush()
            }
        } catch (t: Throwable) {
            // 落盘失败不能影响主流程
        }
    }

    private fun closeWriter() {
        runCatching {
            writer?.flush()
            writer?.close()
        }
        writer = null
    }

    fun allEntries(): List<LogEntry> = synchronized(lock) { buffer.toList() }

    fun clear() = synchronized(lock) { buffer.clear() }

    fun currentLogFilePath(): String? = currentLogFile?.absolutePath

    /** 归档当前内存日志为独立 txt，返回文件 */
    fun archiveTo(dir: File, fileName: String): File? = try {
        val out = File(dir, fileName)
        out.parentFile?.mkdirs()
        val content = allEntries().joinToString("\n") {
            "[${fileFmt.format(Date(it.millis))}] ${it.level.name}/${it.tag}: ${it.message}"
        }
        out.writeText(content, Charsets.UTF_8)
        out
    } catch (t: Throwable) {
        null
    }

    fun addListener(l: (LogEntry) -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: (LogEntry) -> Unit) {
        listeners.remove(l)
    }
}
