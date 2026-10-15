package com.johnny.wxdexinspector.core.scan

import com.johnny.wxdexinspector.core.apk.ApkMeta
import com.johnny.wxdexinspector.core.dex.DexMethodInfo
import com.johnny.wxdexinspector.core.dex.HookCandidate
import com.johnny.wxdexinspector.core.dex.ScanStats
import com.johnny.wxdexinspector.core.ext.ExtensionResult

/** 一次完整扫描的结果（可直接序列化为 .wxsnap） */
data class ScanResult(
    val id: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val modeLabel: String = "",
    val scanModeLabel: String = "",
    val extEnabled: Boolean = false,
    val apk: ApkMeta = ApkMeta(),
    val stats: ScanStats = ScanStats(),
    val classNames: List<String> = emptyList(),
    val methodSigns: List<String> = emptyList(),
    val fieldSigns: List<String> = emptyList(),
    val methods: List<DexMethodInfo> = emptyList(),
    val keyStrings: List<String> = emptyList(),
    val hookCandidates: List<HookCandidate> = emptyList(),
    val ext: ExtensionResult? = null,
    val logFilePath: String? = null,
    val truncated: Boolean = false
) {
    val displayName: String get() = apk.appLabel.ifBlank { apk.fileName.ifBlank { apk.packageName } }
}

/** 两份结果的差异 */
data class DiffResult(
    val baseName: String = "",
    val targetName: String = "",
    val addedClasses: List<String> = emptyList(),
    val removedClasses: List<String> = emptyList(),
    val addedMethods: List<String> = emptyList(),
    val removedMethods: List<String> = emptyList(),
    val addedFields: List<String> = emptyList(),
    val removedFields: List<String> = emptyList(),
    val addedHooks: List<HookCandidate> = emptyList(),
    val removedHooks: List<HookCandidate> = emptyList()
)

object DiffEngine {
    fun diff(base: ScanResult, target: ScanResult): DiffResult {
        val baseClass = base.classNames.toHashSet()
        val tgtClass = target.classNames.toHashSet()
        val baseMethod = base.methodSigns.toHashSet()
        val tgtMethod = target.methodSigns.toHashSet()
        val baseField = base.fieldSigns.toHashSet()
        val tgtField = target.fieldSigns.toHashSet()
        val baseHook = base.hookCandidates.associateBy { it.featureId + "|" + it.className + "|" + it.methodName }
        val tgtHook = target.hookCandidates.associateBy { it.featureId + "|" + it.className + "|" + it.methodName }

        return DiffResult(
            baseName = base.displayName,
            targetName = target.displayName,
            addedClasses = (tgtClass - baseClass).take(5000).sorted(),
            removedClasses = (baseClass - tgtClass).take(5000).sorted(),
            addedMethods = (tgtMethod - baseMethod).take(5000).sorted(),
            removedMethods = (baseMethod - tgtMethod).take(5000).sorted(),
            addedFields = (tgtField - baseField).take(5000).sorted(),
            removedFields = (baseField - tgtField).take(5000).sorted(),
            addedHooks = tgtHook.filterKeys { it !in baseHook }.values.toList(),
            removedHooks = baseHook.filterKeys { it !in tgtHook }.values.toList()
        )
    }
}
