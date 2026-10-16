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
 * 内存策略 —— 「一段一段地扫，而不是一次吃十几碗」：
 *  ① 大 APK 逐个 dex 串行解析：读一个 dex → 解析 → 立刻 close 释放 → 下一个 dex；
 *  ② 段与段之间做堆水位巡检，只有吃紧时才在 dex 边界停下（本段到此为止），
 *     已完成部分由调用方落盘，之后可从下一个 dex **继续扫描**，直到扫完；
 *  ③ 单个 dex 解析失败/内存不足只跳过该 dex，不影响整体；
 *  ④ 明细列表达到容量上限时只停止「存明细」，统计仍然完整（detailCapped）；
 *  ⑤ 特征匹配走倒排索引、支持取消、后台线程优先级。
 */
class DexScanner(
    private val apkPath: String,
    private val onLog: (String) -> Unit = {},
    private val onProgress: (Float, String) -> Unit = { _, _ -> },
    /** 单 dex 兜底加载（临时文件）时使用的目录，为 null 则只用内存字节方式 */
    private val workDir: File? = null
) {

    data class Limits(
        val maxClasses: Int = 250000,
        val maxMethods: Int = 150000,
        val maxFields: Int = 80000,
        val maxKeyStrings: Int = 10000,
        val maxAllStrings: Int = 100000,
        val maxCallEdges: Int = 30000,
        val maxHookCandidates: Int = 1000
    )

    /** 一段扫描的结果 */
    data class ChunkResult(
        /** 本段落结束时的累计数据（含传入的 carry） */
        val output: DexScanOutput,
        /** 下一个待扫描的 dex 下标（等于 totalDex 表示已扫完） */
        val nextDexIndex: Int,
        val totalDex: Int,
        /** 本段实际完成的 dex 数 */
        val scannedDex: Int,
        /** 明细容量是否已满（统计仍完整） */
        val detailCapped: Boolean,
        /** 是否出现「dex 完全无法单独加载」 */
        val perDexUnusable: Boolean
    )

    companion object {
        /** 单段之间堆占用达到该比例就休息（可在 dex 边界续扫） */
        private const val HEAP_REST_RATIO = 0.70f

        /** 单个 dex 可解码上限（超过说明体积异常，跳过以免一次吃掉过多内存） */
        private const val MAX_SINGLE_DEX_BYTES = 96L * 1024 * 1024

        /** APK 根目录下的 classes*.dex 条目（按名称排序，即扫描顺序） */
        fun dexEntries(apkPath: String): List<String> = runCatching {
            ZipFile(apkPath).use { zf ->
                zf.entries().asSequence()
                    .filter { !it.isDirectory }
                    .map { it.name }
                    .filter { n -> !n.contains('/') && n.startsWith("classes") && n.endsWith(".dex") }
                    .sorted()
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private val tag = "DexScanner"

    @Volatile
    private var cancelRequested = false

    val isCancelled: Boolean get() = cancelRequested

    /** 请求取消（可从任意线程调用） */
    fun cancel() {
        cancelRequested = true
    }

    /** 累加容器：可直接接管传入 carry 的列表，避免「复制一份」带来的双份内存 */
    private class Acc(carry: DexScanOutput?) {
        val classes: MutableList<DexClassInfo> = pickMutable(carry?.classes)
        val methods: MutableList<DexMethodInfo> = pickMutable(carry?.methods)
        val fields: MutableList<DexFieldInfo> = pickMutable(carry?.fields)
        val keyStrings: MutableList<StringHit> = pickMutable(carry?.keyStrings)
        val hookCandidates: MutableList<HookCandidate> = pickMutable(carry?.hookCandidates)
        val callEdges: MutableList<CallEdge> = pickMutable(carry?.callEdges)
        val allStrings: HashSet<String> = HashSet<String>(4096).apply {
            carry?.allStrings?.let { addAll(it) }
        }
        var methodCount: Long = carry?.stats?.methodCount ?: 0L
        var fieldCount: Long = carry?.stats?.fieldCount ?: 0L
        var dexParsed: Int = carry?.stats?.dexCount ?: 0
        var dexFailed: Int = 0
        var truncated: Boolean = carry?.truncated ?: false
        var detailCapped: Boolean = false
    }

    private fun log(msg: String) {
        WxLogger.i(tag, msg)
        runCatching { onLog(msg) }
    }

    /** 整包解析（小体积 APK 或逐个 dex 完全不可用且体积较小时使用） */
    fun scanWhole(limits: Limits = Limits(), carry: DexScanOutput? = null): DexScanOutput {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
        if (!DexKitNative.ensureLoaded()) throw IllegalStateException(dexKitLoadError())
        val acc = Acc(carry)
        scanWholeApk(limits, acc)
        return output(acc)
    }

    /**
     * 扫描一段：从 [startIndex] 开始，最多扫 [maxDexPerRun] 个 dex，
     * 扫到「堆水位吃紧」或「该段额度用尽」为止。返回累计结果与下一个待扫下标。
     */
    fun scanChunk(
        limits: Limits = Limits(),
        dexEntries: List<String> = emptyList(),
        startIndex: Int = 0,
        carry: DexScanOutput? = null,
        maxDexPerRun: Int = Int.MAX_VALUE
    ): ChunkResult {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
        if (!DexKitNative.ensureLoaded()) throw IllegalStateException(dexKitLoadError())
        val acc = Acc(carry)

        // 整包模式：小体积 APK / 未提供 dex 列表
        if (dexEntries.size < 2) {
            log("整包解析（dex 数量较少，体积可控）…")
            scanWholeApk(limits, acc)
            return ChunkResult(output(acc), 1, 1, 1, acc.detailCapped, false)
        }

        var index = startIndex.coerceIn(0, dexEntries.size)
        val end = minOf(dexEntries.size.toLong(), index.toLong() + maxDexPerRun).toInt()
        var scanned = 0
        var perDexUnusable = false

        ZipFile(apkPath).use { zf ->
            while (index < end) {
                if (cancelRequested) throw ScanCancelledException()
                if (scanned > 0 && heapUsedRatio() >= HEAP_REST_RATIO) {
                    log(
                        "堆占用 ${(heapUsedRatio() * 100).toInt()}% 已达水位，本段先到这里（已完成 $index/${dexEntries.size} 个 dex）；" +
                            "点「继续扫描」可从下一个 dex 接着来"
                    )
                    break
                }
                val name = dexEntries[index]
                val label = "dex ${index + 1}/${dexEntries.size}"
                val bytes = readEntryBytes(zf, name)
                if (bytes == null) {
                    acc.dexFailed++
                    acc.truncated = true
                    log("$label 读取失败或体积超限，已跳过（$name）")
                    perDexUnusable = true
                    index++
                    scanned++
                    continue
                }
                log("$label 开始解析（$name，${bytes.size / 1024 / 1024} MB，堆占用 ${(heapUsedRatio() * 100).toInt()}%）")

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
                    perDexUnusable = true
                    index++
                    scanned++
                    continue
                }
                try {
                    parseBridge(bridge, limits, acc, index, dexEntries.size, label)
                } catch (e: ScanCancelledException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    acc.dexFailed++
                    acc.truncated = true
                    log("$label 内存不足，已跳过该 dex（其余 dex 不受影响）")
                } finally {
                    runCatching { bridge.close() }
                }
                index++
                scanned++
            }
        }

        log("本段完成：新增 $scanned 个 dex；累计 dex ${acc.dexParsed}、类 ${acc.classes.size}、方法 ${acc.methodCount}")
        return ChunkResult(
            output = output(acc),
            nextDexIndex = index,
            totalDex = dexEntries.size,
            scannedDex = scanned,
            detailCapped = acc.detailCapped,
            perDexUnusable = perDexUnusable && acc.dexParsed == 0
        )
    }

    private fun dexKitLoadError(): String =
        "DexKit 原生库加载失败：${DexKitNative.statusText}\n" +
            "建议：① 卸载后重新安装本应用；② 确认设备架构为 arm64-v8a / armeabi-v7a；" +
            "③ 若仍失败，请在「设置 → 稳定性 → 查看最近崩溃」附上记录反馈。"

    /** 整包加载 */
    private fun scanWholeApk(limits: Limits, acc: Acc) {
        val bridge = try {
            DexKitBridge.create(apkPath)
        } catch (t: Throwable) {
            throw IllegalStateException("无法初始化 DexKit：${t.message}", t)
        }
        try {
            parseBridge(bridge, limits, acc, 0, 1, "APK")
        } catch (e: ScanCancelledException) {
            log("扫描已取消（用户中断）")
            throw e
        } catch (e: OutOfMemoryError) {
            if (acc.classes.isEmpty() && acc.methods.isEmpty()) throw oomError("DEX 解析", e)
            acc.truncated = true
            log("内存不足（${memText()}），已保留部分解析结果")
        } finally {
            runCatching { bridge.close() }
        }
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

    /** 解析单个 bridge（一个 dex 或整包），结果累加到 acc */
    private fun parseBridge(
        bridge: DexKitBridge,
        limits: Limits,
        acc: Acc,
        pathIndex: Int,
        pathCount: Int,
        label: String
    ) {
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
                acc.detailCapped = true
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
                        acc.detailCapped = true
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
                                if (acc.hookCandidates.size >= limits.maxHookCandidates) {
                                    acc.detailCapped = true
                                    break
                                }
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

                    // 调用关系（容量保护）
                    if (acc.callEdges.size < limits.maxCallEdges) {
                        val invokes = runCatching { md.invokes }.getOrNull()
                        if (invokes != null) {
                            val caller = "$mClass->$mName"
                            for (inv in invokes) {
                                if (acc.callEdges.size >= limits.maxCallEdges) {
                                    acc.detailCapped = true
                                    break
                                }
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
                        acc.detailCapped = true
                    }
                }
            }
        }
        acc.dexParsed += dexNum.coerceAtLeast(1)
        log("[$label] 完成，累计类 ${acc.classes.size}，堆占用 ${(heapUsedRatio() * 100).toInt()}%")
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
        truncated = acc.truncated || acc.detailCapped,
        allStrings = acc.allStrings.toList()
    )

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
                "②在「设置 → 拓展功能」关闭拓展检测后重试；③重启本应用后再试。",
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

/** 直接接管 carry 的列表（避免复制导致的「双份内存」）；不可接管时才复制 */
@Suppress("UNCHECKED_CAST")
private fun <T> pickMutable(src: List<T>?): MutableList<T> = when {
    src == null -> ArrayList()
    src is MutableList<*> -> src as MutableList<T>
    else -> ArrayList(src)
}
