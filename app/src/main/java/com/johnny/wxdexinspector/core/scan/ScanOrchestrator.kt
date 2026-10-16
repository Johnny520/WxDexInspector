package com.johnny.wxdexinspector.core.scan

import android.content.Context
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.apk.ApkInfoReader
import com.johnny.wxdexinspector.core.dex.DexScanOutput
import com.johnny.wxdexinspector.core.dex.DexScanner
import com.johnny.wxdexinspector.core.dex.ScanStats
import com.johnny.wxdexinspector.core.ext.BinaryRes
import com.johnny.wxdexinspector.core.ext.ExtensionAnalyzer
import com.johnny.wxdexinspector.core.ext.ExtensionResult
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.core.permission.PermissionManager
import com.johnny.wxdexinspector.core.report.ReportGenerator
import com.johnny.wxdexinspector.core.report.ZipExporter
import com.johnny.wxdexinspector.core.snapshot.SnapshotManager
import com.johnny.wxdexinspector.core.util.MemInfo
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.data.model.ScanMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 扫描编排：APK解析 → DEX深度解析 → 拓展分析 → 快照保存 → 报告导出。
 *
 * v1.0.7：① 扫描开始/结束落盘「痕迹标记」，即使进程被强杀（native 崩溃 / 内存回收）
 * 也能在下次启动时给出可读结论；② 支持「仅基础信息解析」（跳过 DEX，用于超大 APK 保底）；
 * ③ 扫描前做可用内存预警。
 */
object ScanOrchestrator {

    private const val TAG = "Scan"

    /** 当前正在执行的扫描器（用于「取消扫描」） */
    @Volatile
    private var activeScanner: DexScanner? = null

    /** 请求取消当前扫描（可从任意线程调用） */
    fun cancelCurrent() {
        runCatching { activeScanner?.cancel() }
    }

    private val emptyDex = DexScanOutput(
        stats = ScanStats(),
        classes = emptyList(),
        methods = emptyList(),
        fields = emptyList(),
        keyStrings = emptyList(),
        hookCandidates = emptyList(),
        callEdges = emptyList(),
        truncated = false
    )

    private fun log(onLog: (String) -> Unit, msg: String) {
        WxLogger.i(TAG, msg)
        runCatching { onLog(msg) }
    }

    fun scan(
        context: Context,
        apkPath: String,
        source: String,
        scanMode: ScanMode = ScanMode.SINGLE,
        onLog: (String) -> Unit = {},
        deepDex: Boolean = true,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ScanResult {
        val t0 = System.currentTimeMillis()
        val mode = PermissionManager.resolveMode(context)
        val extEnabled = AppPrefs.extActive

        log(onLog, "========== 开始扫描 ==========")
        log(onLog, "当前生效模式：${mode.label}")
        log(onLog, if (extEnabled) "拓展功能：已启用" else "拓展功能：未启用")

        val apkSize = runCatching { File(apkPath).length() }.getOrDefault(0L)
        // 痕迹标记：正常结束（含可捕获异常）会删除；被强杀时保留，供下次启动诊断
        ScanTrace.begin(context, apkPath, apkSize, deepDex)
        try {
            val meta = ApkInfoReader.read(context, apkPath, source)
            log(onLog, "APK：${meta.appLabel.ifBlank { meta.fileName }}（${meta.packageName}）· ${apkSize / 1024 / 1024} MB")

            val dex: DexScanOutput = if (deepDex) {
                val limits = DexScanner.Limits.forApkSize(apkSize)
                log(onLog, "【核心】开始 DEX 深度解析…")
                log(onLog, "明细上限自适应：类 ${limits.maxClasses} / 方法 ${limits.maxMethods} / Hook 候选 ${limits.maxHookCandidates}")
                log(onLog, memoryText("解析前"))
                val availMb = MemInfo.availableMb()
                log(onLog, "系统可用内存：${MemInfo.availableText()}")
                val needMb = apkSize / 1024 / 1024 * 3
                if (availMb in 1..needMb) {
                    log(onLog, "⚠ 内存预警：可用 ${availMb} MB，深度解析预计需要约 ${needMb} MB，存在被系统强杀的风险；如遇闪退可改用「仅基础信息解析」。")
                }
                val scanner = DexScanner(
                    apkPath = apkPath,
                    onLog = onLog,
                    onProgress = onProgress,
                    workDir = File(StorageManager.tempDir(context), "dexwork")
                )
                activeScanner = scanner
                try {
                    scanner.scan(limits)
                } catch (e: OutOfMemoryError) {
                    WxLogger.e(TAG, "DEX 解析内存不足", e)
                    throw IllegalStateException(
                        memoryText("内存不足时") +
                            "\n内存不足，DEX 解析中断。建议：①改用「仅基础信息解析」；" +
                            "②在「设置 → 拓展功能」关闭拓展检测后重试；③改用体积更小的 APK；④重启本应用后再试。",
                        e
                    )
                } finally {
                    activeScanner = null
                }
            } else {
                log(onLog, "已跳过 DEX 深度解析（仅基础信息模式），本模式不做 dexkit 原生解析，最省内存")
                emptyDex
            }
            log(onLog, memoryText("解析后"))

            var ext: ExtensionResult? = null
            if (extEnabled) {
                log(onLog, "【拓展功能】开始解析 AndroidManifest / 资源 / SDK…")
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
                scanModeLabel = if (deepDex) scanMode.label else "${scanMode.label}（仅基础信息）",
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
            log(onLog, "扫描完成，耗时 $elapsed 秒")

            return result.copy(logFilePath = snap?.absolutePath)
        } finally {
            ScanTrace.end(context)
        }
    }

    private fun memoryText(where: String): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
        return "内存[$where]：已用 ${used} MB / 上限 ${rt.maxMemory() / 1024 / 1024} MB"
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
