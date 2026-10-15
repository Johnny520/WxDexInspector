package com.johnny.wxdexinspector.core.apk

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.core.permission.ShellRunner
import com.johnny.wxdexinspector.data.model.RunMode
import rikka.shizuku.Shizuku
import java.io.File

/**
 * APK 提取器。仅做文件复制（静态读取），不改动任何 APK 内容。
 * - Shizuku：免 Root 提取本机用户应用（含 Split 分包）
 * - Root：可提取用户应用 + 系统预装应用
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

        val paths = listApkPaths(app.packageName, mode)
        if (paths.isEmpty()) {
            return ExtractResult.Failure("未获取到 APK 路径（包名：${app.packageName}）。请确认 ${mode.label} 已授权")
        }
        onLog("发现 ${paths.size} 个 APK 路径")

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
            ExtractResult.Failure("提取失败：无法读取 APK 文件，可能权限不足或文件不存在")
        } else {
            ExtractResult.Success(saved, app.packageName)
        }
    }

    private fun listApkPaths(pkg: String, mode: RunMode): List<String> {
        val result = when (mode) {
            RunMode.SHIZUKU -> ShellRunner.runShizuku(arrayOf("pm", "path", pkg))
            RunMode.ROOT -> ShellRunner.runRoot("pm path $pkg")
            else -> return emptyList()
        } ?: return emptyList()
        return result.stdout.lines()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
    }

    private fun copyFile(src: String, dest: File, mode: RunMode): Boolean {
        return try {
            when (mode) {
                RunMode.SHIZUKU -> copyViaShizuku(src, dest)
                RunMode.ROOT -> copyViaRoot(src, dest)
                else -> false
            }
        } catch (t: Throwable) {
            WxLogger.e(TAG, "复制失败 $src", t)
            false
        }
    }

    private fun copyViaShizuku(src: String, dest: File): Boolean {
        val p = Shizuku.newProcess(arrayOf("cat", src), null, null)
        p.inputStream.use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
        val code = p.waitFor()
        return code == 0 && dest.length() > 0
    }

    private fun copyViaRoot(src: String, dest: File): Boolean {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat '$src'"))
        p.inputStream.use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
        val code = p.waitFor()
        return code == 0 && dest.length() > 0
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
