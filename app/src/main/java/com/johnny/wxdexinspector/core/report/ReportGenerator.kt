package com.johnny.wxdexinspector.core.report

import android.content.Context
import com.google.gson.GsonBuilder
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.core.scan.ScanResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 导出报告生成器 */
object ReportGenerator {

    data class Output(val dir: File, val files: List<File>)

    private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun generate(context: Context, result: ScanResult, workDir: File, includeVisual: Boolean): Output {
        if (!workDir.exists()) workDir.mkdirs()
        val files = ArrayList<File>()

        files += write(workDir, "scan_env_info.txt", envInfo(result))
        files += writeLines(workDir, "all_classes.txt", result.classNames)
        files += write(workDir, "all_methods.csv", methodsCsv(result))
        files += write(workDir, "hook_candidate.json", gson.toJson(result.hookCandidates))
        files += write(workDir, "summary_report.md", summary(result))

        WxLogger.archiveTo(workDir, "scan_log.txt")?.let { files += it }

        result.ext?.let { ext ->
            files += write(workDir, "apk_manifest.txt", ext.manifestText)
            files += write(workDir, "apk_cert_info.txt", certInfo(result))
            files += write(workDir, "sdk_detect_report.md", sdkReport(ext))
            files += write(workDir, "component_summary.md", componentReport(ext))
            files += writeLines(workDir, "arsc_string.txt", ext.arscStrings)
        }

        if (includeVisual) {
            val statsPng = File(workDir, "chart_stats.png")
            if (VisualReport.generateStatsChart(statsPng, result.stats, result.hookCandidates.size, result.apk.packageName)) files += statsPng
            val pkgPng = File(workDir, "chart_packages.png")
            if (VisualReport.generatePackageChart(pkgPng, result.classNames)) files += pkgPng
        }

        return Output(workDir, files)
    }

    private fun write(dir: File, name: String, content: String): File {
        val f = File(dir, name)
        f.writeText(content, Charsets.UTF_8)
        return f
    }

    /** 逐行流式写入，避免 joinToString 先把超大列表拼成巨型字符串 */
    private fun writeLines(dir: File, name: String, lines: List<String>): File {
        val f = File(dir, name)
        java.io.BufferedWriter(
            java.io.OutputStreamWriter(java.io.FileOutputStream(f), Charsets.UTF_8),
            64 * 1024
        ).use { w ->
            for (line in lines) {
                w.append(line)
                w.append('\n')
            }
        }
        return f
    }

    private fun envInfo(r: ScanResult): String = buildString {
        appendLine("WxDexInspector 扫描环境信息")
        appendLine("=================================")
        appendLine("扫描时间: ${fmt.format(Date(r.createdAt))}")
        appendLine("工作模式: ${r.modeLabel}")
        appendLine("扫描模式: ${r.scanModeLabel}")
        appendLine("拓展功能: ${if (r.extEnabled) "已启用" else "未启用"}")
        appendLine("APK来源: ${r.apk.source}")
        appendLine("APK路径: ${r.apk.filePath}")
        appendLine("文件名: ${r.apk.fileName}")
        appendLine("包名: ${r.apk.packageName}")
        appendLine("应用名: ${r.apk.appLabel}")
        appendLine("版本: ${r.apk.versionName} (${r.apk.versionCode})")
        appendLine("minSdk / targetSdk: ${r.apk.minSdk} / ${r.apk.targetSdk}")
        appendLine("文件大小: ${r.apk.sizeBytes} bytes")
        appendLine("结果是否截断: ${if (r.truncated) "是（部分明细已截断）" else "否"}")
    }

    private fun methodsCsv(r: ScanResult): String {
        val sb = StringBuilder("class,name,descriptor,returnType,paramCount,isConstructor\n")
        r.methods.forEach { m ->
            sb.append(csv(m.className)).append(',')
                .append(csv(m.name)).append(',')
                .append(csv(m.descriptor)).append(',')
                .append(csv(m.returnType)).append(',')
                .append(m.paramTypes.size).append(',')
                .append(m.isConstructor).append('\n')
        }
        return sb.toString()
    }

    private fun csv(s: String): String =
        if (s.contains(',') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun summary(r: ScanResult): String = buildString {
        appendLine("# WxDexInspector 扫描报告")
        appendLine()
        appendLine("## 基本信息")
        appendLine("- 应用名: **${r.displayName}**")
        appendLine("- 包名: `${r.apk.packageName}`")
        appendLine("- 版本: ${r.apk.versionName} (${r.apk.versionCode})")
        appendLine("- 扫描时间: ${fmt.format(Date(r.createdAt))}")
        appendLine("- 工作模式: ${r.modeLabel}")
        appendLine("- 扫描模式: ${r.scanModeLabel}")
        appendLine()
        appendLine("## DEX 统计")
        appendLine("| 指标 | 数值 |")
        appendLine("| --- | --- |")
        appendLine("| DEX 数量 | ${r.stats.dexCount} |")
        appendLine("| 类数量 | ${r.stats.classCount} |")
        appendLine("| 方法数量 | ${r.stats.methodCount} |")
        appendLine("| 字段数量 | ${r.stats.fieldCount} |")
        appendLine("| 字符串数量 | ${r.stats.stringCount} |")
        appendLine("| Hook 候选 | ${r.hookCandidates.size} |")
        appendLine()
        appendLine("## Hook 候选方法（Top 50）")
        r.hookCandidates.take(50).forEach { h ->
            appendLine("- [${h.confidence}] **${h.description}**")
            appendLine("  - `${h.className}->${h.methodName}`")
            appendLine("  - 命中字符串: ${h.matchedStrings.joinToString(", ")}")
        }
        if (r.ext != null) {
            appendLine()
            appendLine("## 拓展检测")
            appendLine("- 加固识别: ${if (r.ext.hardening.isEmpty()) "未检测到加固" else r.ext.hardening.joinToString("、")}")
            appendLine("- SDK 识别: ${r.ext.sdks.joinToString("、").ifBlank { "无" }}")
            appendLine("- 权限数量: ${r.ext.permissions.size}")
            appendLine("- 组件数量: Activity ${r.ext.activities.size} / Service ${r.ext.services.size} / Receiver ${r.ext.receivers.size} / Provider ${r.ext.providers.size}")
            appendLine("- Intent-Filter: ${r.ext.intentFilters.size} 条")
            appendLine("- resources.arsc 字符串: ${r.ext.arscStrings.size} 条")
        }
        appendLine()
        appendLine("---")
        appendLine("> 本报告由 WxDexInspector 静态分析生成，仅用于微信模块开发与版本适配研究。")
    }

    private fun certInfo(r: ScanResult): String = buildString {
        appendLine("APK 签名证书信息")
        appendLine("=====================")
        appendLine("文件 MD5:    ${r.apk.md5}")
        appendLine("文件 SHA1:   ${r.apk.sha1}")
        appendLine("文件 SHA256: ${r.apk.sha256}")
        appendLine("证书 MD5:    ${r.apk.signMd5}")
        appendLine("证书 SHA1:   ${r.apk.signSha1}")
        appendLine("证书 SHA256: ${r.apk.signSha256}")
        appendLine("证书 Subject: ${r.apk.signSubject}")
        appendLine("证书 Issuer:  ${r.apk.signIssuer}")
    }

    private fun sdkReport(ext: com.johnny.wxdexinspector.core.ext.ExtensionResult): String = buildString {
        appendLine("# SDK / 加固静态识别报告")
        appendLine()
        appendLine("## 加固状态")
        appendLine(if (ext.hardening.isEmpty()) "- 未检测到已知加固特征" else ext.hardening.joinToString("\n") { "- $it" })
        appendLine()
        appendLine("> 仅静态特征识别，不进行任何脱壳操作。")
        appendLine()
        appendLine("## 第三方 / 广告 SDK")
        if (ext.sdks.isEmpty()) appendLine("- 未匹配到已知 SDK") else ext.sdks.forEach { appendLine("- $it") }
    }

    private fun componentReport(ext: com.johnny.wxdexinspector.core.ext.ExtensionResult): String = buildString {
        appendLine("# 组件与权限汇总")
        appendLine()
        appendLine("## 权限 (${ext.permissions.size})")
        ext.permissions.forEach { appendLine("- `$it`") }
        if (ext.declaredPermissions.isNotEmpty()) {
            appendLine()
            appendLine("## 自定义权限 (${ext.declaredPermissions.size})")
            ext.declaredPermissions.forEach { appendLine("- `$it`") }
        }
        appendLine()
        appendLine("## Activity (${ext.activities.size})")
        ext.activities.forEach { appendLine("- `$it`") }
        appendLine()
        appendLine("## Service (${ext.services.size})")
        ext.services.forEach { appendLine("- `$it`") }
        appendLine()
        appendLine("## Receiver (${ext.receivers.size})")
        ext.receivers.forEach { appendLine("- `$it`") }
        appendLine()
        appendLine("## Provider (${ext.providers.size})")
        ext.providers.forEach { appendLine("- `$it`") }
        if (ext.intentFilters.isNotEmpty()) {
            appendLine()
            appendLine("## Intent-Filter (${ext.intentFilters.size})")
            ext.intentFilters.take(200).forEach { f ->
                appendLine("- [${f.componentType}] `${f.componentName}`")
                if (f.actions.isNotEmpty()) appendLine("  - action: ${f.actions.joinToString(", ")}")
                if (f.schemes.isNotEmpty()) appendLine("  - data: ${f.schemes.joinToString(", ")}")
            }
        }
    }
}
