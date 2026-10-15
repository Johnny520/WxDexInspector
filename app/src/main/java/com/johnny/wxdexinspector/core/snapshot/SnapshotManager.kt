package com.johnny.wxdexinspector.core.snapshot

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.core.scan.ScanResult
import java.io.File

/** .wxsnap 快照持久化管理 */
object SnapshotManager {

    private const val TAG = "Snapshot"
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun save(context: Context, result: ScanResult): File? = try {
        val dir = StorageManager.snapshotsDir(context)
        val name = "${result.apk.packageName.ifBlank { "apk" }}_${result.apk.versionName.ifBlank { "v0" }}_${result.createdAt}.wxsnap"
        val f = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        f.writeText(gson.toJson(result), Charsets.UTF_8)
        WxLogger.i(TAG, "快照已保存：${f.name}（${f.length() / 1024} KB）")
        f
    } catch (t: Throwable) {
        WxLogger.e(TAG, "保存快照失败", t)
        null
    }

    fun load(file: File): ScanResult? = try {
        gson.fromJson(file.readText(Charsets.UTF_8), ScanResult::class.java)
    } catch (t: Throwable) {
        WxLogger.e(TAG, "读取快照失败：${file.name}", t)
        null
    }

    fun list(context: Context): List<File> {
        val dir = StorageManager.snapshotsDir(context)
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".wxsnap") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun delete(file: File): Boolean = try {
        file.delete()
    } catch (t: Throwable) {
        false
    }
}
