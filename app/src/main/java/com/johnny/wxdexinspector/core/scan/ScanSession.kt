package com.johnny.wxdexinspector.core.scan

import android.content.Context
import com.google.gson.Gson
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.dex.DexScanOutput
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.core.snapshot.SnapshotManager
import java.io.File

/**
 * 分段扫描会话。
 *
 * 场景：大 APK（微信 272MB / 18 个 dex）不可能一次性解析完 —— 那会「一次吃十几碗」直接 OOM。
 * 这里把扫描做成**可续段的会话**：一次只解析一个 dex，解析完立刻释放并接着下一个；
 * 只有当内存水位吃紧时才在本段边界停下来，把「已完成部分」落盘，
 * 之后点「继续扫描」从下一个 dex 接着扫，最终得到**完整**结果（而不是被截断）。
 */
data class ScanSession(
    val id: String = "",
    val apkPath: String = "",
    val apkSize: Long = 0L,
    val source: String = "",
    val scanModeLabel: String = "",
    val modeLabel: String = "",
    val extEnabled: Boolean = false,
    val deepDex: Boolean = true,
    /** 待扫描的 dex 条目（顺序即扫描顺序）；整包模式为空 */
    val dexEntries: List<String> = emptyList(),
    val totalDex: Int = 1,
    val nextDexIndex: Int = 0,
    /** 展示快照文件名（历史列表可见；同一会话反复覆盖写同一文件，避免历史里堆一堆半成品） */
    val resultFileName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val finished: Boolean get() = !deepDex || nextDexIndex >= totalDex
    val remaining: Int get() = (totalDex - nextDexIndex).coerceAtLeast(0)
    val progressPercent: Int
        get() = if (totalDex <= 0) 0 else ((nextDexIndex.toFloat() / totalDex) * 100).toInt().coerceIn(0, 100)
}

/** 会话与「已完成部分」的落盘管理 */
object ScanSessionStore {

    private const val TAG = "ScanSession"
    private val gson = Gson()

    fun idOf(apkPath: String, apkSize: Long): String {
        val h = "$apkPath|$apkSize".hashCode().toUInt().toString(16)
        return "session_$h"
    }

    private fun dir(context: Context): File = StorageManager.snapshotsDir(context)

    private fun sessionFile(context: Context, id: String) = File(dir(context), "$id.json")
    private fun carryFile(context: Context, id: String) = File(dir(context), "$id.carry.json")
    private fun resultFile(context: Context, id: String) = File(dir(context), "$id.session.wxsnap")

    fun save(context: Context, session: ScanSession): Boolean = runCatching {
        sessionFile(context, session.id).writeText(gson.toJson(session.copy(updatedAt = System.currentTimeMillis())), Charsets.UTF_8)
        true
    }.getOrDefault(false)

    fun load(context: Context, id: String): ScanSession? = runCatching {
        val f = sessionFile(context, id)
        if (!f.isFile) null else gson.fromJson(f.readText(Charsets.UTF_8), ScanSession::class.java)
    }.getOrNull()

    /** 保存已完成部分（供中断后续扫）；carry 为 null 表示删除 */
    fun saveCarry(context: Context, id: String, carry: DexScanOutput?): Boolean = runCatching {
        val f = carryFile(context, id)
        if (carry == null) {
            f.delete()
            true
        } else {
            java.io.BufferedWriter(
                java.io.OutputStreamWriter(java.io.FileOutputStream(f), Charsets.UTF_8),
                64 * 1024
            ).use { w ->
                gson.toJson(carry, DexScanOutput::class.java, w)
                w.flush()
            }
            true
        }
    }.getOrDefault(false)

    fun loadCarry(context: Context, id: String): DexScanOutput? = runCatching {
        val f = carryFile(context, id)
        if (!f.isFile) null
        else java.io.InputStreamReader(java.io.FileInputStream(f), Charsets.UTF_8).use { r ->
            gson.fromJson(r, DexScanOutput::class.java)
        }
    }.onFailure { WxLogger.e(TAG, "读取扫描进度失败：${it.message}") }.getOrNull()

    /** 当前段的展示快照（同一会话覆盖写同一文件） */
    fun saveResult(context: Context, session: ScanSession, result: ScanResult): File? {
        val name = session.resultFileName.ifBlank { "${session.id}.session.wxsnap" }
        return SnapshotManager.saveNamed(context, name, result)
    }

    /** 扫描全部完成：清掉会话痕迹（快照保留在历史中） */
    fun finishSession(context: Context, id: String) {
        runCatching { sessionFile(context, id).delete() }
        runCatching { carryFile(context, id).delete() }
    }

    fun loadResult(context: Context, id: String): ScanResult? = runCatching {
        val f = resultFile(context, id)
        if (!f.isFile) null else SnapshotManager.load(f)
    }.getOrNull()

    fun clear(context: Context, id: String) {
        runCatching { sessionFile(context, id).delete() }
        runCatching { carryFile(context, id).delete() }
        runCatching { resultFile(context, id).delete() }
    }

    /** 清理所有会话残留（含历史遗留的 .session.wxsnap），返回清理数量 */
    fun clearAll(context: Context): Int = runCatching {
        var n = 0
        dir(context).listFiles()?.forEach { f ->
            val name = f.name
            if (name.endsWith(".session.wxsnap") || name.endsWith(".carry.json") ||
                (name.startsWith("session_") && name.endsWith(".json"))
            ) {
                if (f.delete()) n++
            }
        }
        n
    }.getOrDefault(0)
}
