package com.johnny.wxdexinspector.core.scan

import android.content.Context
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.apk.ApkInfoReader
import com.johnny.wxdexinspector.core.dex.DexScanner
import com.johnny.wxdexinspector.core.ext.BinaryRes
import com.johnny.wxdexinspector.core.ext.ExtensionAnalyzer
import com.johnny.wxdexinspector.core.ext.ExtensionResult
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.core.permission.PermissionManager
import com.johnny.wxdexinspector.core.report.ReportGenerator
import com.johnny.wxdexinspector.core.report.ZipExporter
import com.johnny.wxdexinspector.core.snapshot.SnapshotManager
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.data.model.ScanMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 扫描编排：APK解析 → DEX深度解析 → 拓展分析 → 快照保存 → 报告导出 */
object ScanOrchestrator {

    private const val TAG = "Scan"

    fun scan(
        context: Context,
        apkPath: String,
        source: String,
        scanMode: ScanMode = ScanMode.SINGLE,
        onLog: (String) -> Unit = {}
    ): ScanResult {
        val t0 = System.currentTimeMillis()
        val mode = PermissionManager.resolveMode(context)
        val extEnabled = AppPrefs.extActive

        WxLogger.i(TAG, "========== 开始扫描 ==========")
        WxLogger.i(TAG, "当前生效模式：${mode.label}")
        WxLogger.i(TAG, if (extEnabled) "拓展功能：已启用" else "拓展功能：未启用")
        onLog("========== 开始扫描 ==========")
        onLog("当前生效模式：${mode.label}")
        onLog(if (extEnabled) "拓展功能：已启用" else "拓展功能：未启用")

        val meta = ApkInfoReader.read(context, apkPath, source)
        WxLogger.i(TAG, "包名 ${meta.packageName}｜版本 ${meta.versionName}｜大小 ${meta.sizeBytes / 1024 / 1024} MB")
        onLog("APK：${meta.appLabel.ifBlank { meta.fileName }}（${meta.packageName}）")

        onLog("【核心】开始 DEX 深度解析…")
        val dex = DexScanner(apkPath, onLog).scan()

        var ext: ExtensionResult? = null
        if (extEnabled) {
            onLog("【拓展功能】开始解析 AndroidManifest / 资源 / SDK…")
            val entries = BinaryRes.listZipEntries(apkPath)
            ext = ExtensionAnalyzer.analyze(
                apkPath = apkPath,
                dexClassNames = dex.classes.map { it.name },
                fileEntries = entries,
                parseArsc = AppPrefs.extArsc,
                previewFiles = AppPrefs.extPreview,
                onLog = onLog
            )
        }

        val result = ScanResult(
            id = "SCAN_" + System.currentTimeMillis(),
            createdAt = t0,
            modeLabel = mode.label,
            scanModeLabel = scanMode.label,
            extEnabled = extEnabled,
            apk = meta,
            stats = dex.stats,
            classNames = dex.classes.map { it.name }.take(200000),
            methodSigns = dex.methods.map { it.sign }.take(200000),
            fieldSigns = dex.fields.map { "${it.className}->${it.name}:${it.type}" }.take(200000),
            methods = dex.methods,
            keyStrings = dex.keyStrings.map { it.value }.distinct().take(5000),
            hookCandidates = dex.hookCandidates,
            ext = ext,
            truncated = dex.truncated
        )

        val snap = SnapshotManager.save(context, result)
        val elapsed = (System.currentTimeMillis() - t0) / 1000
        WxLogger.i(TAG, "扫描完成，耗时 ${elapsed}s；快照：${snap?.name}")
        onLog("扫描完成，耗时 $elapsed 秒")

        return result.copy(logFilePath = snap?.absolutePath)
    }

    fun export(context: Context, result: ScanResult, includeVisual: Boolean, onLog: (String) -> Unit = {}): File? {
        return try {
            val dir = StorageManager.exportsDir(context)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val base = "${result.apk.packageName.ifBlank { "apk" }}_${result.apk.versionName.ifBlank { "v0" }}_$stamp"
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            val workDir = File(dir, base)
            onLog("生成报告中：$base")
            val out = ReportGenerator.generate(context, result, workDir, includeVisual)
            val zip = File(dir, "$base.zip")
            val ok = ZipExporter.zip(out.dir, zip)
            runCatching { out.dir.deleteRecursively() }
            if (ok) {
                onLog("导出完成：${zip.absolutePath}（${zip.length() / 1024} KB）")
                zip
            } else {
                onLog("导出失败")
                null
            }
        } catch (t: Throwable) {
            WxLogger.e(TAG, "导出报告失败", t)
            onLog("导出失败：${t.message}")
            null
        }
    }
}
