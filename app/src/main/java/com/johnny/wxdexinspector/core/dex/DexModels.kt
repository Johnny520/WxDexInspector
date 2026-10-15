package com.johnny.wxdexinspector.core.dex

/** 类信息 */
data class DexClassInfo(
    val name: String,
    val descriptor: String,
    val superClass: String?,
    val interfaces: List<String>,
    val methodCount: Int,
    val fieldCount: Int,
    val sourceFile: String?
)

/** 方法信息 */
data class DexMethodInfo(
    val className: String,
    val name: String,
    val descriptor: String,
    val sign: String,
    val returnType: String,
    val paramTypes: List<String>,
    val isConstructor: Boolean,
    val modifiers: Int
)

/** 字段信息 */
data class DexFieldInfo(
    val className: String,
    val name: String,
    val type: String,
    val modifiers: Int
)

/** 硬编码字符串命中 */
data class StringHit(
    val value: String,
    val className: String,
    val methodName: String
)

/** Hook 候选方法 */
data class HookCandidate(
    val featureId: String,
    val description: String,
    val className: String,
    val methodName: String,
    val descriptor: String,
    val matchedStrings: List<String>,
    val confidence: Int
)

/** 调用关系边 */
data class CallEdge(val caller: String, val callee: String)

/** 统计汇总 */
data class ScanStats(
    val dexCount: Int = 0,
    val classCount: Int = 0,
    val methodCount: Long = 0,
    val fieldCount: Long = 0,
    val stringCount: Int = 0
)

/** DEX 扫描原始输出 */
data class DexScanOutput(
    val stats: ScanStats,
    val classes: List<DexClassInfo>,
    val methods: List<DexMethodInfo>,
    val fields: List<DexFieldInfo>,
    val keyStrings: List<StringHit>,
    val hookCandidates: List<HookCandidate>,
    val callEdges: List<CallEdge>,
    val truncated: Boolean
)
