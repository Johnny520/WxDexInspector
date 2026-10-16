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
import com.johnny.wxdexinspector.core.util.MemInfo
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.data.model.ScanMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一次「扫描段」的结果：携带当前结果与会话进度 */
data class ScanRun(val result: ScanResult, val session: ScanSession)

/**
 * 扫描编排（分段可续扫）：
 *   APK解析 → 逐 dex 串行 DEX 解析（一段一段来）→ 全部 dex 完成后做拓展分析 → 落盘快照。
 *
 * 关键点：一次只解析一个 dex；只有内存吃紧时才在 dex 边界暂停并把已完成部分落盘，
 * 用户点「继续扫描」从下一个 dex 接着扫，最终得到完整结果，而不是被截断的结果。
 */
object ScanOrchestrator {

    private const val TAG = "Scan"

    /** 逐 dex 完全不可用时，只有小于该体积的 APK 才允许回退整包 */
    private const val WHOLE_APK_FALLBACK_MAX = 40L * 1024 * 1024

    /** 堆占用超过该比例则本次不做拓展功能分析 */
    private const val EXT_SKIP_HEAP_RATIO = 0.6f

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
        truncated = false,
        allStrings = emptyList()
    )

    private fun log(onLog: (String) -> Unit, msg: String) {
        WxLogger.i(TAG, msg)
        runCatching { onLog(msg) }
    }

    private fun dexWorkDir(context: Context): File = File(StorageManager.tempDir(context), "dexwork")

    /** 当前堆占用比例（0~1） */
    private fun heapRatio(): Float {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()).toFloat()
        return if (rt.maxMemory() <= 0L) 0f else used / rt.maxMemory()
    }

    private fun memoryText(where: String): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
        return "内存[$where]：已用 ${used} MB / 上限 ${rt.maxMemory() / 1024 / 1024} MB"
    }

    /** 新建会话并完成第一段 */
    fun startChunk(
        context: Context,
        apkPath: String,
        source: String,
        scanMode: ScanMode = ScanMode.SINGLE,
        deepDex: Boolean = true,
        onLog: (String) -> Unit = {},
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ScanRun {
        val t0 = System.currentTimeMillis()
        val apkSize = runCatching { File(apkPath).length() }.getOrDefault(0L)
        val modeLabel = PermissionManager.resolveMode(context).label
        val extEnabled = AppPrefs.extActive

        log(onLog, "========== 开始扫描 ==========")
        log(onLog, "当前生效模式：$modeLabel；APK 体积 ${apkSize / 1024 / 1024} MB")

        val entries = if (deepDex) DexScanner.dexEntries(apkPath) else emptyList()
        val segmented = deepDex && entries.size >= 2

        val session = ScanSession(
            id = ScanSessionStore.idOf(apkPath, apkSize),
            apkPath = apkPath,
            apkSize = apkSize,
            source = source,
            scanModeLabel = scanMode.label,
            modeLabel = modeLabel,
            extEnabled = extEnabled,
            deepDex = deepDex,
            dexEntries = entries,
            totalDex = if (segmented) entries.size else 1,
            nextDexIndex = 0
        )
        log(
            onLog,
            when {
                segmented -> "共 ${entries.size} 个 dex：逐个 dex 串行解析（读一个、解析一个、释放一个），扫完一个接着下一个"
                deepDex -> "整包解析（dex 数量较少，体积可控）"
                else -> "仅基础信息模式（不做 DEX 解析，最省内存）"
            }
        )
        return runSegment(context, session, carry = null, t0 = t0, onLog = onLog, onProgress = onProgress)
    }

    /** 继续上一段之后的内容（从已完成部分接着扫） */
    fun continueChunk(
        context: Context,
        session: ScanSession,
        onLog: (String) -> Unit = {},
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ScanRun {
        val carry = ScanSessionStore.loadCarry(context, session.id)
        log(
            onLog,
            "继续扫描：已完成 ${session.nextDexIndex}/${session.totalDex} 个 dex，剩余 ${session.remaining} 个" +
                if (carry == null) "（未找到已完成部分，将从当前进度重新累计）" else ""
        )
        return runSegment(context, session, carry, t0 = System.currentTimeMillis(), onLog = onLog, onProgress = onProgress)
    }

    /**
     * 一次扫到底（内部自动续段，供批量扫描等使用）。
     * 若内存持续吃紧导致某段没有任何推进，则停在当前进度并返回已有结果。
     */
    fun scan(
        context: Context,
        apkPath: String,
        source: String,
        scanMode: ScanMode = ScanMode.SINGLE,
        onLog: (String) -> Unit = {},
        deepDex: Boolean = true,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ScanResult {
        var run = startChunk(context, apkPath, source, scanMode, deepDex, onLog, onProgress)
        var guard = 0
        while (!run.session.finished && guard++ < 200) {
            val before = run.session.nextDexIndex
            run = continueChunk(context, run.session, onLog, onProgress)
            if (run.session.nextDexIndex <= before) {
                log(onLog, "内存持续吃紧，已暂停在 ${run.session.nextDexIndex}/${run.session.totalDex} 个 dex；可在扫描页点「继续扫描」接着来")
                break
            }
        }
        return run.result
    }

    /** 执行一个扫描段 */
    private fun runSegment(
        context: Context,
        session: ScanSession,
        carry: DexScanOutput?,
        t0: Long,
        onLog: (String) -> Unit,
        onProgress: (Float, String) -> Unit
    ): ScanRun {
        ScanTrace.begin(context, session.apkPath, session.apkSize, session.deepDex)
        try {
            val meta = ApkInfoReader.read(context, session.apkPath, session.source)
            log(onLog, "APK：${meta.appLabel.ifBlank { meta.fileName }}（${meta.packageName}）")

            val limits = DexScanner.Limits()
            val dexOutput: DexScanOutput
            var nextIndex = session.nextDexIndex

            if (!session.deepDex) {
                log(onLog, "已跳过 DEX 深度解析（仅基础信息模式）")
                dexOutput = carry ?: emptyDex
                nextIndex = session.totalDex
            } else {
                log(onLog, memoryText("解析前"))
                log(onLog, "系统可用内存：${MemInfo.availableText()}")
                val scanner = DexScanner(session.apkPath, onLog, onProgress, dexWorkDir(context))
                activeScanner = scanner
                val chunk = try {
                    scanner.scanChunk(
                        limits = limits,
                        dexEntries = session.dexEntries,
                        startIndex = session.nextDexIndex,
                        carry = carry
                    )
                } catch (e: OutOfMemoryError) {
                    WxLogger.e(TAG, "DEX 解析内存不足", e)
                    throw IllegalStateException(
                        memoryText("内存不足时") +
                            "\n内存不足，DEX 解析中断。建议：①改用「仅基础信息解析」；" +
                            "②在「设置 → 拓展功能」关闭拓展检测后重试；③重启本应用后再试。",
                        e
                    )
                } finally {
                    activeScanner = null
                }

                if (chunk.perDexUnusable) {
                    if (session.apkSize < WHOLE_APK_FALLBACK_MAX) {
                        log(onLog, "逐个 dex 加载不可用，回退整包解析（体积较小，风险可控）…")
                        dexOutput = scanner.scanWhole(limits, carry)
                        nextIndex = 1
                    } else {
                        throw IllegalStateException(
                            "该 APK 的 dex 无法单独加载。为避免一次性加载整包导致内存不足，已停止 DEX 深度解析。\n" +
                                "请改用「仅基础信息解析」模式（不做 DEX 解析，最省内存）。"
                        )
                    }
                } else {
                    dexOutput = chunk.output
                    nextIndex = chunk.nextDexIndex
                }
                log(onLog, memoryText("解析后"))
            }

            val newSession = session.copy(nextDexIndex = nextIndex, updatedAt = System.currentTimeMillis())
            val finished = newSession.finished

            // 拓展功能：只在全部 dex 扫描完成后做一次
            var ext: ExtensionResult? = null
            if (finished && session.extEnabled) {
                val ratio = heapRatio()
                if (ratio > EXT_SKIP_HEAP_RATIO) {
                    log(onLog, "内存占用已达 ${(ratio * 100).toInt()}%，本次跳过拓展功能分析（可在下次内存充裕时重扫）")
                } else {
                    log(onLog, "【拓展功能】开始解析 AndroidManifest / 资源 / SDK…")
                    val entries = BinaryRes.listZipEntries(session.apkPath)
                    ext = ExtensionAnalyzer.analyze(
                        apkPath = session.apkPath,
                        dexClassNames = dexOutput.classes.map { it.name },
                        fileEntries = entries,
                        parseArsc = AppPrefs.extArsc,
                        previewFiles = AppPrefs.extPreview,
                        onLog = onLog
                    )
                }
            } else if (session.extEnabled) {
                log(onLog, "拓展功能分析将在全部 dex 扫描完成后进行（当前 ${newSession.nextDexIndex}/${newSession.totalDex}）")
            }

            val result = ScanResult(
                id = newSession.id,
                createdAt = t0,
                modeLabel = newSession.modeLabel,
                scanModeLabel = if (newSession.deepDex) newSession.scanModeLabel else "${newSession.scanModeLabel}（仅基础信息）",
                extEnabled = session.extEnabled,
                apk = meta,
                stats = dexOutput.stats,
                classNames = dexOutput.classes.map { it.name },
                methodSigns = dexOutput.methods.map { it.sign },
                fieldSigns = dexOutput.fields.map { "${it.className}->${it.name}:${it.type}" },
                methods = dexOutput.methods,
                keyStrings = dexOutput.keyStrings.map { it.value }.distinct(),
                hookCandidates = dexOutput.hookCandidates,
                ext = ext,
                truncated = dexOutput.truncated || !finished
            )

            // 落盘：展示快照 + 会话进度 + 已完成部分（未扫完时）
            val snap = ScanSessionStore.saveResult(context, newSession.id, result)
            ScanSessionStore.save(context, newSession)
            if (finished) {
                ScanSessionStore.saveCarry(context, newSession.id, null)
            } else {
                ScanSessionStore.saveCarry(context, newSession.id, dexOutput)
            }

            val elapsed = (System.currentTimeMillis() - t0) / 1000
            if (finished) {
                log(onLog, "扫描完成：全部 ${newSession.totalDex} 个 dex，本段耗时 ${elapsed} 秒")
            } else {
                log(onLog, "本段完成：${newSession.nextDexIndex}/${newSession.totalDex} 个 dex（耗时 ${elapsed} 秒），点「继续扫描」接着来")
            }
            return ScanRun(result.copy(logFilePath = snap?.absolutePath), newSession)
        } finally {
            ScanTrace.end(context)
        }
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
