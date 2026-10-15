package com.johnny.wxdexinspector.core.dex

import android.os.Process
import com.johnny.wxdexinspector.core.log.WxLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass

/** 用户主动取消扫描 */
class ScanCancelledException : RuntimeException("扫描已取消")

/**
 * 基于 DexKit 的 DEX 深度解析器。
 * 仅做静态读取：类/方法/字段/字符串/调用关系，不做任何修改。
 *
 * v1.0.6 稳定性改造：
 *  ① 明细上限按 APK 体积自适应（大包主动降级，避免内存不足闪退）；
 *  ② 支持取消，长扫描可中断，不再「卡死」；
 *  ③ 特征匹配改用「字符串 → 特征」倒排索引，去掉「每个方法 × 全部特征」的全量比对；
 *  ④ 扫描线程降为后台优先级，解析期间不抢 UI 线程；
 *  ⑤ 内存不足时抛出可读建议，而不是直接崩溃。
 */
class DexScanner(
    private val apkPath: String,
    private val onLog: (String) -> Unit = {},
    private val onProgress: (Float, String) -> Unit = { _, _ -> }
) {

    data class Limits(
        val maxClasses: Int = 200000,
        val maxMethods: Int = 60000,
        val maxFields: Int = 20000,
        val maxKeyStrings: Int = 5000,
        val maxAllStrings: Int = 50000,
        val maxCallEdges: Int = 20000,
        val maxHookCandidates: Int = 500
    ) {
        companion object {
            /** 按 APK 体积自适应上限：体积越大越容易内存不足，主动降级以换取「能跑完」 */
            fun forApkSize(sizeBytes: Long): Limits = when {
                sizeBytes >= 150L * 1024 * 1024 -> Limits(
                    maxClasses = 60000,
                    maxMethods = 12000,
                    maxFields = 5000,
                    maxKeyStrings = 1200,
                    maxAllStrings = 12000,
                    maxCallEdges = 4000,
                    maxHookCandidates = 200
                )
                sizeBytes >= 60L * 1024 * 1024 -> Limits(
                    maxClasses = 120000,
                    maxMethods = 25000,
                    maxFields = 10000,
                    maxKeyStrings = 2500,
                    maxAllStrings = 25000,
                    maxCallEdges = 8000,
                    maxHookCandidates = 300
                )
                else -> Limits()
            }
        }
    }

    private val tag = "DexScanner"

    @Volatile
    private var cancelRequested = false

    val isCancelled: Boolean get() = cancelRequested

    /** 请求取消（可从任意线程调用） */
    fun cancel() {
        cancelRequested = true
    }

    private fun log(msg: String) {
        WxLogger.i(tag, msg)
        runCatching { onLog(msg) }
    }

    fun scan(limits: Limits = Limits()): DexScanOutput {
        val classes = ArrayList<DexClassInfo>(1024)
        val methods = ArrayList<DexMethodInfo>(1024)
        val fields = ArrayList<DexFieldInfo>(1024)
        val keyStrings = ArrayList<StringHit>(256)
        val hookCandidates = ArrayList<HookCandidate>(64)
        val callEdges = ArrayList<CallEdge>(1024)
        val allStrings = HashSet<String>(4096)

        var methodCount = 0L
        var fieldCount = 0L
        var truncated = false
        var dexNum = 0

        // 后台优先级：解析期间不与 UI 争抢 CPU
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }

        if (!DexKitNative.ensureLoaded()) {
            throw IllegalStateException(
                "DexKit 原生库加载失败：${DexKitNative.statusText}\n" +
                    "建议：① 卸载后重新安装本应用；② 确认设备架构为 arm64-v8a / armeabi-v7a；" +
                    "③ 若仍失败，请在「设置 → 稳定性 → 查看最近崩溃」附上记录反馈。"
            )
        }
        val bridge = try {
            DexKitBridge.create(apkPath)
        } catch (t: Throwable) {
            throw IllegalStateException("无法初始化 DexKit：${t.message}", t)
        }
        try {
            dexNum = runCatching { bridge.getDexNum() }.getOrDefault(0)
            log("DexKit 加载完成，DEX 数量：$dexNum")
            logMemory("DexKit 初始化后")

            val classList = try {
                bridge.findClass(FindClass.create())
            } catch (e: OutOfMemoryError) {
                throw oomError("读取类列表", e)
            }
            val classTotal = classList.size
            log("扫描到类总数：$classTotal")

            var idx = 0
            for (cd in classList) {
                if (cancelRequested) throw ScanCancelledException()
                idx++
                if (idx % 2000 == 0) {
                    log("已解析 $idx/$classTotal 个类…")
                    runCatching {
                        onProgress(idx.toFloat() / classTotal.coerceAtLeast(1), "解析类 $idx/$classTotal")
                    }
                    if (idx % 20000 == 0) logMemory("解析中")
                }

                val className = runCatching { cd.name }.getOrNull() ?: continue
                val superName = runCatching { cd.superClass?.name }.getOrNull()
                val ifaces = runCatching { cd.interfaces.mapNotNull { it?.name } }.getOrDefault(emptyList())
                val mCount = runCatching { cd.methodCount }.getOrDefault(0)
                val fCount = runCatching { cd.fieldCount }.getOrDefault(0)

                if (classes.size < limits.maxClasses) {
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
                            // 倒排索引先筛出「可能命中的特征」，只对候选特征做精确判定
                            val hitFeatures = collectHitFeatures(lowerSet)
                            if (hitFeatures.isNotEmpty() && hookCandidates.size < limits.maxHookCandidates) {
                                for (f in hitFeatures) {
                                    if (hookCandidates.size >= limits.maxHookCandidates) break
                                    if (matches(f, lowerSet)) {
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
            logMemory("解析完成后")
            runCatching { onProgress(1f, "解析完成") }
        } catch (e: ScanCancelledException) {
            log("扫描已取消（用户中断）")
            throw e
        } catch (e: OutOfMemoryError) {
            throw oomError("DEX 解析", e)
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

    /** 用倒排索引收集「本方法可能命中的特征」（避免逐个特征全量比对） */
    private fun collectHitFeatures(lowerSet: Set<String>): List<HookFeature> {
        val index = runCatching { HookFeatureLibrary.stringIndex }.getOrNull() ?: return emptyList()
        if (index.isEmpty()) return emptyList()
        var out: MutableList<HookFeature>? = null
        for (s in lowerSet) {
            val list = index[s] ?: continue
            val target = out ?: ArrayList<HookFeature>(4).also { out = it }
            for (f in list) if (!target.contains(f)) target.add(f)
        }
        return out ?: emptyList()
    }

    private fun oomError(where: String, e: OutOfMemoryError): IllegalStateException =
        IllegalStateException(
            "$where 时内存不足（${memText()}）。建议：①在「设置 → 拓展功能」关闭拓展检测后重试；" +
                "②改用体积更小的 APK；③关闭其他后台应用或重启本应用后再试。",
            e
        )

    private fun memText(): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
        return "已用 ${used} MB / 上限 ${rt.maxMemory() / 1024 / 1024} MB"
    }

    private fun logMemory(where: String) {
        log("内存[$where]：${memText()}")
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
        return interestingPatterns.any { low.contains(it) }
    }
}
