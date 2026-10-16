package com.johnny.wxdexinspector.core.dex

import android.os.Process
import com.johnny.wxdexinspector.core.log.WxLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import java.io.File
import java.util.zip.ZipFile

/** 用户主动取消扫描 */
class ScanCancelledException : RuntimeException("扫描已取消")

/**
 * 基于 DexKit 的 DEX 深度解析器（仅静态读取，不做任何修改）。
 *
 * 内存策略（针对「扫描卡顿 / 无日志闪退 / OOM」）：
 *  ① **一个 dex 一个 dex 地来**：大 APK 不再把整包交给 DexKit（那是「一次吃十几碗」），
 *     而是逐个把单个 classes*.dex 的字节交给 DexKit，解析完立刻 close 桥并释放字节；
 *  ② 单个 dex 解析完即释放，堆水位巡检（超过 72% 立即收手），宁可结果截断也不让应用被强杀；
 *  ③ 单个 dex 内存不足时跳过该 dex 继续，尽量产出部分结果；
 *  ④ 逐 dex 加载完全不可用时，只有小体积 APK 才回退整包；大体积直接提示改用「仅基础信息解析」；
 *  ⑤ 明细上限按 APK 体积自适应、特征匹配走倒排索引、支持取消、后台线程优先级。
 */
class DexScanner(
    private val apkPath: String,
    private val onLog: (String) -> Unit = {},
    private val onProgress: (Float, String) -> Unit = { _, _ -> },
    /** 单 dex 兜底加载（临时文件）时使用的目录，为 null 则只用内存字节方式 */
    private val workDir: File? = null
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

    companion object {
        /** 超过该体积即启用「逐个 dex 解析」 */
        private const val PER_DEX_THRESHOLD = 20L * 1024 * 1024

        /** 堆占用超过该比例即停止继续解析：宁可截断，也不让应用被系统强杀 */
        private const val MEM_STOP_RATIO = 0.72f

        /** 单个 dex 可解码上限（超过说明体积异常，跳过以免一次吃掉过多内存） */
        private const val MAX_SINGLE_DEX_BYTES = 96L * 1024 * 1024
    }

    private val tag = "DexScanner"

    @Volatile
    private var cancelRequested = false

    val isCancelled: Boolean get() = cancelRequested

    /** 请求取消（可从任意线程调用） */
    fun cancel() {
        cancelRequested = true
    }

    /** 扫描过程中的累加容器（逐个 dex 解析时跨 bridge 复用） */
    private class Acc {
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
        var dexParsed = 0
        var dexFailed = 0
    }

    private fun log(msg: String) {
        WxLogger.i(tag, msg)
        runCatching { onLog(msg) }
    }

    fun scan(limits: Limits = Limits()): DexScanOutput {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }

        if (!DexKitNative.ensureLoaded()) {
            throw IllegalStateException(
                "DexKit 原生库加载失败：${DexKitNative.statusText}\n" +
                    "建议：① 卸载后重新安装本应用；② 确认设备架构为 arm64-v8a / armeabi-v7a；" +
                    "③ 若仍失败，请在「设置 → 稳定性 → 查看最近崩溃」附上记录反馈。"
            )
        }

        val size = runCatching { File(apkPath).length() }.getOrDefault(0L)
        val acc = Acc()

        if (size >= PER_DEX_THRESHOLD) {
            val entries = listDexEntries(apkPath)
            if (entries.size >= 2) {
                log("APK 体积 ${size / 1024 / 1024} MB，共 ${entries.size} 个 dex；逐个 dex 串行解析（解析一个、释放一个，避免一次性占满内存）")
                logMemory("解析前")
                val usable = scanPerDexEntries(entries, limits, acc)
                if (!usable) {
                    acc.dexParsed = 0
                    if (size < PER_DEX_THRESHOLD * 2) {
                        log("逐个 dex 加载不可用，回退整包加载（体积较小，风险可控）…")
                        scanWholeApk(limits, acc)
                    } else {
                        throw IllegalStateException(
                            "该 APK 的 dex 无法单独加载。为避免一次性加载整包导致内存不足，已停止 DEX 深度解析。\n" +
                                "请改用「仅基础信息解析」模式（不做 DEX 解析，最省内存）。"
                        )
                    }
                }
                logMemory("解析完成后")
                log("解析完成：类 ${acc.classes.size}，方法 ${acc.methodCount}，字段 ${acc.fieldCount}，字符串 ${acc.allStrings.size}${if (acc.truncated) "（结果已截断）" else ""}")
                log("Hook 候选：${acc.hookCandidates.size} 个；失败/跳过 dex：${acc.dexFailed} 个")
                runCatching { onProgress(1f, "解析完成") }
                return output(acc)
            }
        }

        log("单次加载整个 APK（体积 ${size / 1024 / 1024} MB，属小体积，风险可控）")
        logMemory("解析前")
        scanWholeApk(limits, acc)
        logMemory("解析完成后")
        log("解析完成：类 ${acc.classes.size}，方法 ${acc.methodCount}，字段 ${acc.fieldCount}，字符串 ${acc.allStrings.size}${if (acc.truncated) "（结果已截断）" else ""}")
        log("Hook 候选：${acc.hookCandidates.size} 个")
        runCatching { onProgress(1f, "解析完成") }
        return output(acc)
    }

    /** 整包加载（仅小体积 APK 或逐 dex 完全不可用且体积较小时使用） */
    private fun scanWholeApk(limits: Limits, acc: Acc) {
        val bridge = try {
            DexKitBridge.create(apkPath)
        } catch (t: Throwable) {
            throw IllegalStateException("无法初始化 DexKit：${t.message}", t)
        }
        try {
            if (!parseBridge(bridge, limits, acc, 0, 1, "APK")) {
                log("整包解析因内存压力提前结束（结果已截断）")
            }
        } catch (e: ScanCancelledException) {
            log("扫描已取消（用户中断）")
            throw e
        } catch (e: OutOfMemoryError) {
            // 已解析到部分数据就保留下来，避免整次扫描以「闪退」收场
            if (acc.classes.isEmpty() && acc.methods.isEmpty()) throw oomError("DEX 解析", e)
            acc.truncated = true
            log("内存不足（${memText()}），已保留部分解析结果并提前结束")
        } finally {
            runCatching { bridge.close() }
        }
    }

    /**
     * 逐个 dex 串行解析：读一个 → 解析一个 → 释放一个。
     * @return false 表示该 APK 完全无法按 dex 单独加载（调用方决定是否回退）
     */
    private fun scanPerDexEntries(entries: List<String>, limits: Limits, acc: Acc): Boolean {
        var firstUnusable = false
        ZipFile(apkPath).use { zf ->
            for ((index, name) in entries.withIndex()) {
                if (cancelRequested) throw ScanCancelledException()
                val ratio = heapUsedRatio()
                if (ratio > MEM_STOP_RATIO) {
                    acc.truncated = true
                    log("堆占用已达 ${(ratio * 100).toInt()}%，停止后续 dex 解析（已处理 $index/${entries.size} 个），结果已截断")
                    break
                }
                val label = "dex ${index + 1}/${entries.size}"
                val bytes = readEntryBytes(zf, name)
                if (bytes == null) {
                    acc.dexFailed++
                    acc.truncated = true
                    log("$label 读取失败或体积超限，已跳过（$name）")
                    if (index == 0) firstUnusable = true
                    continue
                }
                log("$label 开始解析（$name，${bytes.size / 1024 / 1024} MB）")

                val fromMemory = try {
                    DexKitBridge.create(arrayOf(bytes))
                } catch (t: Throwable) {
                    WxLogger.e(tag, "$label 内存内加载失败：${t.message}", t)
                    null
                }
                val bridge = fromMemory ?: tryTempFileBridge(bytes, label)
                if (bridge == null) {
                    acc.dexFailed++
                    acc.truncated = true
                    log("$label 无法单独加载，已跳过")
                    if (index == 0) firstUnusable = true
                    continue
                }
                try {
                    if (!parseBridge(bridge, limits, acc, index, entries.size, label)) {
                        log("内存压力较大，停止后续 dex 解析，结果已截断")
                        break
                    }
                } catch (e: ScanCancelledException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    acc.dexFailed++
                    acc.truncated = true
                    WxLogger.e(tag, "$label 内存不足，已跳过该 dex", e)
                } finally {
                    runCatching { bridge.close() }
                }
            }
        }
        if (firstUnusable && acc.dexParsed == 0) return false
        if (acc.dexParsed == 0) {
            throw IllegalStateException("全部 ${entries.size} 个 dex 均解析失败，建议改用「仅基础信息解析」。")
        }
        return true
    }

    /** 兜底：单个 dex 落盘后再按路径加载（仍然是一个一个来） */
    private fun tryTempFileBridge(bytes: ByteArray, label: String): DexKitBridge? {
        val dir = workDir ?: return null
        if (!dir.exists() && !dir.mkdirs()) return null
        val f = File(dir, "one_dex.dex")
        val bridge = runCatching {
            java.io.FileOutputStream(f).use { it.write(bytes) }
            DexKitBridge.create(f.absolutePath)
        }.getOrElse { t ->
            WxLogger.e(tag, "$label 临时文件加载失败：${t.message}", t)
            null
        }
        runCatching { f.delete() }
        return bridge
    }

    /**
     * 解析单个 bridge（一个 dex 或整包），结果累加到 acc。
     * @return true 表示完整解析完成；false 表示因内存压力提前结束（结果已截断）
     */
    private fun parseBridge(
        bridge: DexKitBridge,
        limits: Limits,
        acc: Acc,
        pathIndex: Int,
        pathCount: Int,
        label: String
    ): Boolean {
        val dexNum = runCatching { bridge.getDexNum() }.getOrDefault(1)
        val classList = try {
            bridge.findClass(FindClass.create())
        } catch (e: OutOfMemoryError) {
            throw oomError("读取类列表", e)
        }
        val classTotal = classList.size
        log("[$label] 含 ${dexNum} 个 dex，类 $classTotal，堆占用 ${(heapUsedRatio() * 100).toInt()}%")

        var idx = 0
        for (cd in classList) {
            if (cancelRequested) throw ScanCancelledException()
            idx++
            // 内存水位巡检：到阈值就收手，避免 OOM 被系统强杀
            if (idx % 500 == 0) {
                val ratio = heapUsedRatio()
                if (ratio > MEM_STOP_RATIO) {
                    acc.dexParsed += dexNum.coerceAtLeast(1)
                    acc.truncated = true
                    log("[$label] 堆占用已达 ${(ratio * 100).toInt()}%，提前结束解析（$idx/$classTotal 个类），结果已截断")
                    return false
                }
            }
            if (idx % 2000 == 0) {
                val frac = (pathIndex + idx.toFloat() / classTotal.coerceAtLeast(1)) / pathCount.coerceAtLeast(1)
                log("[$label] 已解析 $idx/$classTotal 个类…")
                runCatching { onProgress(frac.coerceIn(0f, 0.99f), "$label：类 $idx/$classTotal") }
                if (idx % 20000 == 0) logMemory("解析中")
            }

            val className = runCatching { cd.name }.getOrNull() ?: continue
            val superName = runCatching { cd.superClass?.name }.getOrNull()
            val ifaces = runCatching { cd.interfaces.mapNotNull { it?.name } }.getOrDefault(emptyList())
            val mCount = runCatching { cd.methodCount }.getOrDefault(0)
            val fCount = runCatching { cd.fieldCount }.getOrDefault(0)

            if (acc.classes.size < limits.maxClasses) {
                acc.classes.add(
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
                acc.truncated = true
            }

            val mdList = runCatching { cd.methods }.getOrNull()
            if (mdList != null) {
                for (md in mdList) {
                    acc.methodCount++
                    val mName = runCatching { md.methodName }.getOrNull() ?: continue
                    val mClass = runCatching { md.className }.getOrNull() ?: className
                    val mDesc = runCatching { md.descriptor }.getOrDefault("")
                    val isCtor = runCatching { md.isConstructor }.getOrDefault(false)

                    if (acc.methods.size < limits.maxMethods) {
                        acc.methods.add(
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
                        acc.truncated = true
                    }

                    // 字符串 & 特征匹配
                    val strs = runCatching { md.usingStrings }.getOrNull()
                    if (!strs.isNullOrEmpty()) {
                        val lowerSet = HashSet<String>(strs.size)
                        for (s in strs) {
                            if (acc.allStrings.size < limits.maxAllStrings) acc.allStrings.add(s)
                            lowerSet.add(s.lowercase())
                            if (acc.keyStrings.size < limits.maxKeyStrings && looksInteresting(s)) {
                                acc.keyStrings.add(StringHit(s, mClass, mName))
                            }
                        }
                        // 倒排索引先筛出「可能命中的特征」，只对候选特征做精确判定
                        val hitFeatures = collectHitFeatures(lowerSet)
                        if (hitFeatures.isNotEmpty() && acc.hookCandidates.size < limits.maxHookCandidates) {
                            for (f in hitFeatures) {
                                if (acc.hookCandidates.size >= limits.maxHookCandidates) break
                                if (matches(f, lowerSet)) {
                                    acc.hookCandidates.add(
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
                    if (acc.callEdges.size < limits.maxCallEdges) {
                        val invokes = runCatching { md.invokes }.getOrNull()
                        if (invokes != null) {
                            val caller = "$mClass->$mName"
                            for (inv in invokes) {
                                if (acc.callEdges.size >= limits.maxCallEdges) break
                                val callee = runCatching { inv.methodSign }.getOrNull() ?: continue
                                acc.callEdges.add(CallEdge(caller, callee))
                            }
                        }
                    }
                }
            }

            val fdList = runCatching { cd.fields }.getOrNull()
            if (fdList != null) {
                for (fd in fdList) {
                    acc.fieldCount++
                    if (acc.fields.size < limits.maxFields) {
                        acc.fields.add(
                            DexFieldInfo(
                                className = runCatching { fd.className }.getOrDefault(className),
                                name = runCatching { fd.name }.getOrDefault(""),
                                type = runCatching { fd.typeSign }.getOrDefault(""),
                                modifiers = runCatching { fd.modifiers }.getOrDefault(0)
                            )
                        )
                    } else {
                        acc.truncated = true
                    }
                }
            }
        }
        acc.dexParsed += dexNum.coerceAtLeast(1)
        log("[$label] 完成，累计类 ${acc.classes.size}，堆占用 ${(heapUsedRatio() * 100).toInt()}%")
        return true
    }

    private fun output(acc: Acc): DexScanOutput = DexScanOutput(
        stats = ScanStats(
            dexCount = acc.dexParsed,
            classCount = acc.classes.size,
            methodCount = acc.methodCount,
            fieldCount = acc.fieldCount,
            stringCount = acc.allStrings.size
        ),
        classes = acc.classes,
        methods = acc.methods,
        fields = acc.fields,
        keyStrings = acc.keyStrings,
        hookCandidates = acc.hookCandidates,
        callEdges = acc.callEdges,
        truncated = acc.truncated
    )

    /** APK 根目录下的 classes*.dex 条目 */
    private fun listDexEntries(apkPath: String): List<String> = runCatching {
        ZipFile(apkPath).use { zf ->
            zf.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { n -> !n.contains('/') && n.startsWith("classes") && n.endsWith(".dex") }
                .sorted()
                .toList()
        }
    }.getOrDefault(emptyList())

    /** 读取单个 zip 条目的完整字节（体积超限返回 null） */
    private fun readEntryBytes(zf: ZipFile, name: String): ByteArray? {
        val entry = zf.getEntry(name) ?: return null
        val size = entry.size
        if (size <= 0L || size > MAX_SINGLE_DEX_BYTES) return null
        return runCatching {
            val out = ByteArray(size.toInt())
            zf.getInputStream(entry).use { ins ->
                var off = 0
                while (off < out.size) {
                    val read = ins.read(out, off, out.size - off)
                    if (read <= 0) break
                    off += read
                }
            }
            out
        }.getOrNull()
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
            "$where 时内存不足（${memText()}）。建议：①改用「仅基础信息解析」；" +
                "②在「设置 → 拓展功能」关闭拓展检测后重试；③改用体积更小的 APK；④重启本应用后再试。",
            e
        )

    /** 当前堆占用比例（0~1） */
    private fun heapUsedRatio(): Float {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()).toFloat()
        return if (rt.maxMemory() <= 0L) 0f else used / rt.maxMemory()
    }

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
