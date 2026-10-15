package com.johnny.wxdexinspector.core.dex

import com.johnny.wxdexinspector.core.log.WxLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass

/**
 * 基于 DexKit 的 DEX 深度解析器。
 * 仅做静态读取：类/方法/字段/字符串/调用关系，不做任何修改。
 */
class DexScanner(
    private val apkPath: String,
    private val onLog: (String) -> Unit = {}
) {

    data class Limits(
        val maxMethods: Int = 60000,
        val maxFields: Int = 20000,
        val maxKeyStrings: Int = 5000,
        val maxAllStrings: Int = 50000,
        val maxCallEdges: Int = 20000,
        val maxHookCandidates: Int = 500
    )

    private val tag = "DexScanner"

    private fun log(msg: String) {
        WxLogger.i(tag, msg)
        onLog(msg)
    }

    fun scan(limits: Limits = Limits()): DexScanOutput {
        val classes = ArrayList<DexClassInfo>()
        val methods = ArrayList<DexMethodInfo>()
        val fields = ArrayList<DexFieldInfo>()
        val keyStrings = ArrayList<StringHit>()
        val hookCandidates = ArrayList<HookCandidate>()
        val callEdges = ArrayList<CallEdge>()
        val allStrings = HashSet<String>()

        var methodCount = 0L
        var fieldCount = 0L
        var truncated = false
        var dexNum = 0

        val bridge = DexKitBridge.create(apkPath)
        try {
            dexNum = runCatching { bridge.dexNum }.getOrDefault(0)
            log("DexKit 加载完成，DEX 数量：$dexNum")

            val classList = bridge.findClass(FindClass.create())
            log("扫描到类总数：${classList.size}")

            var idx = 0
            for (cd in classList) {
                idx++
                if (idx % 5000 == 0) log("已解析 $idx/${classList.size} 个类…")

                val className = runCatching { cd.name }.getOrNull() ?: continue
                val superName = runCatching { cd.superClass?.name }.getOrNull()
                val ifaces = runCatching { cd.interfaces.mapNotNull { it?.name } }.getOrDefault(emptyList())
                val mCount = runCatching { cd.methodCount }.getOrDefault(0)
                val fCount = runCatching { cd.fieldCount }.getOrDefault(0)

                if (classes.size < 200000) {
                    classes.add(
                        DexClassInfo(
                            name = className,
                            descriptor = runCatching { cd.descriptor }.getOrDefault(""),
                            superClass = superName,
                            interfaces = ifaces,
                            methodCount = mCount,
                            fieldCount = fCount,
                            sourceFile = runCatching { cd.sourceFile }.getOrNull()
                        )
                    )
                } else {
                    truncated = true
                }

                val mdList = runCatching { cd.methods }.getOrNull()
                if (mdList != null) {
                    for (md in mdList) {
                        methodCount++
                        val mName = runCatching { md.methodName }.getOrNull() ?: continue
                        val mClass = runCatching { md.className }.getOrNull() ?: className
                        val mDesc = runCatching { md.descriptor }.getOrDefault("")
                        val isCtor = runCatching { md.isConstructor }.getOrDefault(false)

                        if (methods.size < limits.maxMethods) {
                            methods.add(
                                DexMethodInfo(
                                    className = mClass,
                                    name = mName,
                                    descriptor = mDesc,
                                    sign = runCatching { md.methodSign }.getOrDefault("$mClass->$mName$mDesc"),
                                    returnType = runCatching { md.returnTypeName }.getOrDefault(""),
                                    paramTypes = runCatching { md.paramTypeNames }.getOrDefault(emptyList()),
                                    isConstructor = isCtor,
                                    modifiers = runCatching { md.modifiers }.getOrDefault(0)
                                )
                            )
                        } else {
                            truncated = true
                        }

                        // 字符串 & 特征匹配
                        val strs = runCatching { md.usingStrings }.getOrNull()
                        if (!strs.isNullOrEmpty()) {
                            val lowerSet = HashSet<String>(strs.size)
                            for (s in strs) {
                                if (allStrings.size < limits.maxAllStrings) allStrings.add(s)
                                lowerSet.add(s.lowercase())
                                if (keyStrings.size < limits.maxKeyStrings && looksInteresting(s)) {
                                    keyStrings.add(StringHit(s, mClass, mName))
                                }
                            }
                            if (hookCandidates.size < limits.maxHookCandidates) {
                                HookFeatureLibrary.FEATURES.forEach { f ->
                                    if (matches(f, lowerSet) && hookCandidates.size < limits.maxHookCandidates) {
                                        hookCandidates.add(
                                            HookCandidate(
                                                featureId = f.id,
                                                description = f.description,
                                                className = mClass,
                                                methodName = mName,
                                                descriptor = mDesc,
                                                matchedStrings = strs.filter { s ->
                                                    (f.allStrings + f.anyStrings).any { it.equals(s, ignoreCase = true) }
                                                }.distinct().take(6),
                                                confidence = confidence(f, lowerSet, mClass)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 调用关系（截断保护）
                        if (callEdges.size < limits.maxCallEdges) {
                            val invokes = runCatching { md.invokes }.getOrNull()
                            if (invokes != null) {
                                val caller = "$mClass->$mName"
                                for (inv in invokes) {
                                    if (callEdges.size >= limits.maxCallEdges) break
                                    val callee = runCatching { inv.methodSign }.getOrNull() ?: continue
                                    callEdges.add(CallEdge(caller, callee))
                                }
                            }
                        }
                    }
                }

                val fdList = runCatching { cd.fields }.getOrNull()
                if (fdList != null) {
                    for (fd in fdList) {
                        fieldCount++
                        if (fields.size < limits.maxFields) {
                            fields.add(
                                DexFieldInfo(
                                    className = runCatching { fd.className }.getOrDefault(className),
                                    name = runCatching { fd.name }.getOrDefault(""),
                                    type = runCatching { fd.typeSign }.getOrDefault(""),
                                    modifiers = runCatching { fd.modifiers }.getOrDefault(0)
                                )
                            )
                        } else {
                            truncated = true
                        }
                    }
                }
            }

            log("解析完成：类 ${classes.size}，方法 $methodCount，字段 $fieldCount，字符串 ${allStrings.size}")
            log("Hook 候选：${hookCandidates.size} 个")
        } finally {
            runCatching { bridge.close() }
        }

        val stats = ScanStats(
            dexCount = dexNum,
            classCount = classes.size,
            methodCount = methodCount,
            fieldCount = fieldCount,
            stringCount = allStrings.size
        )
        return DexScanOutput(
            stats = stats,
            classes = classes,
            methods = methods,
            fields = fields,
            keyStrings = keyStrings,
            hookCandidates = hookCandidates,
            callEdges = callEdges,
            truncated = truncated
        )
    }

    private fun matches(f: HookFeature, lowerSet: Set<String>): Boolean {
        if (f.allStrings.isNotEmpty() && !f.allStrings.all { lowerSet.contains(it.lowercase()) }) return false
        if (f.anyStrings.isNotEmpty() && f.anyStrings.none { lowerSet.contains(it.lowercase()) }) return false
        return f.allStrings.isNotEmpty() || f.anyStrings.isNotEmpty()
    }

    private fun confidence(f: HookFeature, lowerSet: Set<String>, className: String): Int {
        var c = 55
        if (f.allStrings.isNotEmpty()) c += 15
        if (f.classHints.isNotEmpty() && f.classHints.any { className.contains(it, ignoreCase = true) }) c += 20
        val hitCount = (f.allStrings + f.anyStrings).count { lowerSet.contains(it.lowercase()) }
        c += minOf(hitCount * 3, 9)
        return c.coerceIn(1, 99)
    }

    private val interestingPatterns = listOf(
        "cgi-bin", "http://", "https://", ".com/", "password", "secret", "apikey", "api_key",
        "token", "private_key", "-----begin", "sqlite", "rcontact", "MicroMsg."
    )

    private fun looksInteresting(s: String): Boolean {
        if (s.length < 6) return false
        val low = s.lowercase()
        return interestingPatterns.any { low.contains(it.lowercase()) }
    }
}
