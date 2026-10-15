package com.johnny.wxdexinspector.core.ext

/** Intent-Filter 信息 */
data class IntentFilterInfo(
    val componentType: String,
    val componentName: String,
    val actions: List<String>,
    val categories: List<String>,
    val schemes: List<String>
)

/** 拓展功能分析结果（仅在拓展开启时生成） */
data class ExtensionResult(
    val hardening: List<String> = emptyList(),
    val sdks: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val declaredPermissions: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val receivers: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val intentFilters: List<IntentFilterInfo> = emptyList(),
    val manifestText: String = "",
    val arscStrings: List<String> = emptyList(),
    val fileEntries: List<String> = emptyList()
)

/** 加固厂商签名库 */
private val HARDENING_SIGNATURES: List<Pair<String, List<String>>> = listOf(
    "腾讯乐固" to listOf("libshell.so", "libshella.so", "com/tencent/StubShell", "libtosprotection"),
    "360加固" to listOf("libjiagu.so", "libjiagu_art.so", "libjiagu_x86.so", "com/stub/StubApp", "com/qihoo/util"),
    "爱加密" to listOf("libexec.so", "libexecmain.so", "com/shell/SuperApplication", "s/h/e/l/l/S"),
    "梆梆安全" to listOf("libsecexe.so", "libsecmain.so", "libDexHelper.so", "com/secneo/apkwrapper"),
    "阿里聚安全" to listOf("libmobisec.so", "com/ali/mobisecenhance"),
    "娜迦加固" to listOf("libchaosvmp.so", "libddog.so", "libfdog.so"),
    "通付盾" to listOf("libTFEnt.so", "com/dx/mobile"),
    "顶象" to listOf("libDXFvmp.so", "libDingRTC"),
    "腾讯御安全" to listOf("libtup.so", "libhamster.so"),
    "百度加固" to listOf("libbaiduprotect.so"),
    "几维安全" to listOf("libkwscmm.so", "libkwscr.so"),
    "网易易盾" to listOf("libnesec.so", "libyidun.so")
)

/** 第三方/广告 SDK 特征库（类名前缀 / 关键字） */
private val SDK_SIGNATURES: List<Pair<String, List<String>>> = listOf(
    "广点通/优量汇广告SDK" to listOf("com.qq.e."),
    "穿山甲/Pangle广告SDK" to listOf("com.bytedance.sdk.openadsdk", "com.pangle."),
    "百度联盟广告SDK" to listOf("com.baidu.mobads"),
    "字节跳动SDK" to listOf("com.bytedance."),
    "友盟统计SDK" to listOf("com.umeng."),
    "腾讯Bugly" to listOf("com.tencent.bugly"),
    "极光推送" to listOf("cn.jpush.", "cn.jiguang."),
    "个推推送" to listOf("com.igexin."),
    "微信开放SDK" to listOf("com.tencent.mm.opensdk"),
    "QQ互联SDK" to listOf("com.tencent.connect", "com.tencent.tauth"),
    "支付宝SDK" to listOf("com.alipay.sdk"),
    "高德地图SDK" to listOf("com.amap.api"),
    "百度地图SDK" to listOf("com.baidu.location", "com.baidu.mapapi"),
    "腾讯地图SDK" to listOf("com.tencent.map"),
    "OkHttp网络库" to listOf("okhttp3."),
    "Retrofit网络库" to listOf("retrofit2."),
    "Glide图片库" to listOf("com.bumptech.glide"),
    "阿里云SDK" to listOf("com.alibaba."),
    "Firebase" to listOf("com.google.firebase"),
    "Facebook SDK" to listOf("com.facebook."),
    "新浪微博SDK" to listOf("com.sina.weibo")
)

object ExtensionAnalyzer {

    fun analyze(
        apkPath: String,
        dexClassNames: List<String>,
        fileEntries: List<String>,
        parseArsc: Boolean,
        previewFiles: Boolean,
        onLog: (String) -> Unit = {}
    ): ExtensionResult {
        val manifestBytes = BinaryRes.readZipEntry(apkPath, "AndroidManifest.xml")
        val root = manifestBytes?.let { BinaryRes.parseManifest(it) }

        val permissions = ArrayList<String>()
        val declaredPermissions = ArrayList<String>()
        val activities = ArrayList<String>()
        val services = ArrayList<String>()
        val receivers = ArrayList<String>()
        val providers = ArrayList<String>()
        val filters = ArrayList<IntentFilterInfo>()
        val sb = StringBuilder()

        if (root != null) {
            root.children.forEach { child ->
                when (child.name) {
                    "uses-permission", "uses-permission-sdk-23" ->
                        child.attrs["name"]?.let { permissions.add(it) }
                    "permission" ->
                        child.attrs["name"]?.let { declaredPermissions.add(it) }
                    "application" -> {
                        child.children.forEach { comp ->
                            val type = comp.name
                            val name = comp.attrs["name"] ?: ""
                            when (type) {
                                "activity", "activity-alias" -> { activities.add(name); collectFilters(comp, "activity", name, filters) }
                                "service" -> { services.add(name); collectFilters(comp, "service", name, filters) }
                                "receiver" -> { receivers.add(name); collectFilters(comp, "receiver", name, filters) }
                                "provider" -> { providers.add(name) }
                            }
                        }
                    }
                }
            }
            toXml(root, sb, 0)
            onLog("Manifest 解析完成：组件 ${activities.size + services.size + receivers.size + providers.size} 个，权限 ${permissions.size} 个")
        } else {
            onLog("Manifest 解析失败（可能非标准二进制XML）")
        }

        val hardening = detectHardening(fileEntries, dexClassNames)
        val sdks = detectSdks(dexClassNames)
        onLog("加固识别：${if (hardening.isEmpty()) "未加固" else hardening.joinToString("、")}")
        onLog("匹配到 SDK ${sdks.size} 项")

        val arsc = if (parseArsc) {
            val s = BinaryRes.readArscStrings(apkPath)
            onLog("resources.arsc 字符串：${s.size} 条")
            s
        } else emptyList()

        return ExtensionResult(
            hardening = hardening,
            sdks = sdks,
            permissions = permissions.distinct(),
            declaredPermissions = declaredPermissions.distinct(),
            activities = activities.distinct(),
            services = services.distinct(),
            receivers = receivers.distinct(),
            providers = providers.distinct(),
            intentFilters = filters,
            manifestText = sb.toString(),
            arscStrings = arsc,
            fileEntries = if (previewFiles) fileEntries else emptyList()
        )
    }

    private fun collectFilters(node: com.johnny.wxdexinspector.core.ext.BinaryRes.XmlNode, type: String, name: String, out: MutableList<IntentFilterInfo>) {
        node.children.filter { it.name == "intent-filter" }.forEach { f ->
            val actions = ArrayList<String>()
            val categories = ArrayList<String>()
            val schemes = ArrayList<String>()
            f.children.forEach { c ->
                when (c.name) {
                    "action" -> c.attrs["name"]?.let { actions.add(it) }
                    "category" -> c.attrs["name"]?.let { categories.add(it) }
                    "data" -> {
                        c.attrs["scheme"]?.let { schemes.add(it) }
                        c.attrs["host"]?.let { schemes.add("host:$it") }
                    }
                }
            }
            if (actions.isNotEmpty() || schemes.isNotEmpty()) {
                out.add(IntentFilterInfo(type, name, actions, categories, schemes))
            }
        }
    }

    fun detectHardening(fileEntries: List<String>, classNames: List<String>): List<String> {
        val entriesLower = fileEntries.map { it.lowercase() }
        val classesJoined = classNames.take(200000).joinToString("\u0000")
        val found = ArrayList<String>()
        HARDENING_SIGNATURES.forEach { (vendor, sigs) ->
            val hit = sigs.any { s ->
                val sl = s.lowercase()
                entriesLower.any { it.contains(sl) } || classesJoined.contains(s, ignoreCase = true)
            }
            if (hit) found.add(vendor)
        }
        return found
    }

    fun detectSdks(classNames: List<String>): List<String> {
        val found = ArrayList<String>()
        SDK_SIGNATURES.forEach { (name, prefixes) ->
            val hit = classNames.any { cn -> prefixes.any { cn.startsWith(it) } }
            if (hit) found.add(name)
        }
        return found
    }

    private fun toXml(node: com.johnny.wxdexinspector.core.ext.BinaryRes.XmlNode, sb: StringBuilder, indent: Int) {
        val pad = "  ".repeat(indent)
        sb.append(pad).append("<").append(node.name)
        node.attrs.forEach { (k, v) ->
            sb.append(" ").append(k).append("=\"").append(v.replace("\"", "&quot;")).append("\"")
        }
        if (node.children.isEmpty()) {
            sb.append("/>\n")
        } else {
            sb.append(">\n")
            node.children.forEach { toXml(it, sb, indent + 1) }
            sb.append(pad).append("</").append(node.name).append(">\n")
        }
    }
}
