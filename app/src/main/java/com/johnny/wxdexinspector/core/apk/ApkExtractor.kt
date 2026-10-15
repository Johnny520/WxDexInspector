package com.johnny.wxdexinspector.core.apk

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.data.model.RunMode
import java.io.File

/**
 * APK 提取器。仅做文件复制（静态读取），不改动任何 APK 内容。
 * - Shizuku / Root：提取本机已安装应用（含 Split 分包）
 * - 提取方式：优先直接读取文件（免 Root 可行场景），失败时 Root 走 su 通道
 */
object ApkExtractor {

    private const val TAG = "ApkExtractor"

    fun extract(
        context: Context,
        app: InstalledApp,
        mode: RunMode,
        destDir: File,
        onLog: (String) -> Unit = {}
    ): ExtractResult {
        if (mode == RunMode.STATIC) {
            return ExtractResult.Failure("纯静态模式无法提取已安装应用，请切换 Shizuku / Root 模式，或手动导入 APK 文件")
        }
        if (!destDir.exists()) destDir.mkdirs()

        val paths = apkPathsFor(context, app)
        if (paths.isEmpty()) {
            return ExtractResult.Failure("未获取到 APK 路径（包名：${app.packageName}）")
        }
        onLog("发现 ${paths.size} 个 APK 文件")

        val saved = ArrayList<String>()
        for (src in paths) {
            val name = File(src).name
            val dest = File(destDir, "${app.packageName}__$name")
            onLog("正在提取：$src")
            val ok = copyFile(src, dest, mode)
            if (ok) {
                saved.add(dest.absolutePath)
                onLog("已保存：${dest.name}（${dest.length() / 1024 / 1024} MB）")
            } else {
                onLog("提取失败：$src")
            }
        }
        return if (saved.isEmpty()) {
            ExtractResult.Failure("提取失败：无法读取 APK 文件，可能权限不足。请尝试 Root 模式或手动导入")
        } else {
            ExtractResult.Success(saved, app.packageName)
        }
    }

    /** 收集 base.apk 与所有 split 分包路径 */
    fun apkPathsFor(context: Context, app: InstalledApp): List<String> {
        val paths = LinkedHashSet<String>()
        app.apkPath?.let { paths.add(it) }
        try {
            val pi = context.packageManager.getPackageInfo(app.packageName, 0)
            val ai = pi.applicationInfo
            ai?.sourceDir?.let { paths.add(it) }
            ai?.splitSourceDirs?.forEach { paths.add(it) }
        } catch (t: Throwable) {
            WxLogger.w(TAG, "获取分包列表失败：${app.packageName}")
        }
        return paths.filter { it.isNotBlank() }
    }

    private fun copyFile(src: String, dest: File, mode: RunMode): Boolean {
        // 1) 直读
        if (copyDirect(src, dest)) return true
        // 2) Root 通道
        if (mode == RunMode.ROOT) return copyViaRoot(src, dest)
        return false
    }

    private fun copyDirect(src: String, dest: File): Boolean = try {
        val f = File(src)
        if (!f.canRead()) return false
        f.inputStream().use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
        dest.length() > 0
    } catch (t: Throwable) {
        false
    }

    private fun copyViaRoot(src: String, dest: File): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat '$src'"))
        p.inputStream.use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
        val code = p.waitFor()
        code == 0 && dest.length() > 0
    } catch (t: Throwable) {
        WxLogger.e(TAG, "Root 复制失败 $src", t)
        false
    }

    /** 从 SAF Uri 导入外部 APK（纯静态模式即可用） */
    fun importFromUri(context: Context, uri: Uri, destDir: File): ExtractResult {
        if (!destDir.exists()) destDir.mkdirs()
        val name = queryName(context, uri) ?: "imported_${System.currentTimeMillis()}.apk"
        val dest = File(destDir, name)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            } ?: return ExtractResult.Failure("无法打开所选文件")
            if (dest.length() <= 0) ExtractResult.Failure("文件内容为空") else ExtractResult.Success(listOf(dest.absolutePath), name)
        } catch (t: Throwable) {
            WxLogger.e(TAG, "导入 APK 失败", t)
            ExtractResult.Failure("导入失败：${t.message}")
        }
    }

    private fun queryName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (t: Throwable) {
        null
    }
}
