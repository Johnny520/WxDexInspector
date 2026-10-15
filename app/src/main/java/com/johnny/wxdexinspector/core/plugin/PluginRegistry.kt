package com.johnny.wxdexinspector.core.plugin

import android.content.Context
import com.johnny.wxdexinspector.core.dex.HookFeature
import com.johnny.wxdexinspector.core.dex.HookFeatureLibrary
import com.johnny.wxdexinspector.core.log.WxLogger
import org.json.JSONObject
import java.io.File

/**
 * 插件数据注册表：把「已启用插件」携带的静态特征合并进内存，供扫描/识别使用。
 * 插件只提供数据（JSON），不执行代码。
 */
object PluginRegistry {

    private const val TAG = "PluginRegistry"

    @Volatile
    private var hookFeatures: List<HookFeature> = emptyList()

    @Volatile
    private var hardening: List<Pair<String, List<String>>> = emptyList()

    @Volatile
    private var sdk: List<Pair<String, List<String>>> = emptyList()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (!loaded) reload(context)
    }

    @Synchronized
    fun reload(context: Context) {
        val hooks = ArrayList<HookFeature>()
        val hards = ArrayList<Pair<String, List<String>>>()
        val sdks = ArrayList<Pair<String, List<String>>>()

        PluginManager.installed(context).filter { it.enabled }.forEach { p ->
            val payload = runCatching { File(p.dir, "payload.json").readText(Charsets.UTF_8) }.getOrNull()
            if (payload.isNullOrBlank()) return@forEach
            runCatching {
                when (p.info.type) {
                    PluginType.HOOK -> parseHook(payload, hooks)
                    PluginType.HARDENING -> parseHardening(payload, hards)
                    PluginType.SDK -> parseSdk(payload, sdks)
                }
            }.onFailure { WxLogger.e(TAG, "插件解析失败: ${p.info.id}", it) }
        }

        hookFeatures = hooks
        hardening = hards
        sdk = sdks
        loaded = true
        HookFeatureLibrary.invalidate()
        WxLogger.i(TAG, "插件特征已加载：Hook ${hooks.size} 条，加固 ${hards.size} 组，SDK ${sdks.size} 组")
    }

    fun extraHookFeatures(): List<HookFeature> = hookFeatures
    fun extraHardening(): List<Pair<String, List<String>>> = hardening
    fun extraSdk(): List<Pair<String, List<String>>> = sdk

    // -------- 解析 --------
    private fun parseHook(text: String, out: MutableList<HookFeature>) {
        val arr = JSONObject(text).optJSONArray("features") ?: return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank { "plugin_hook_$i" }
            out.add(
                HookFeature(
                    id = id,
                    description = o.optString("description", id),
                    allStrings = o.optJSONArray("allStrings").toStringList(),
                    anyStrings = o.optJSONArray("anyStrings").toStringList(),
                    classHints = o.optJSONArray("classHints").toStringList()
                )
            )
        }
    }

    private fun parseHardening(text: String, out: MutableList<Pair<String, List<String>>>) {
        val arr = JSONObject(text).optJSONArray("rules") ?: return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val vendor = o.optString("vendor").ifBlank { continue }
            val sigs = o.optJSONArray("so").toStringList() + o.optJSONArray("classes").toStringList()
            if (sigs.isNotEmpty()) out.add(vendor to sigs)
        }
    }

    private fun parseSdk(text: String, out: MutableList<Pair<String, List<String>>>) {
        val arr = JSONObject(text).optJSONArray("sdks") ?: return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").ifBlank { continue }
            val prefixes = o.optJSONArray("prefixes").toStringList()
            if (prefixes.isNotEmpty()) out.add(name to prefixes)
        }
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = ArrayList<String>(length())
        for (i in 0 until length()) {
            val s = optString(i, "")
            if (s.isNotBlank()) list.add(s)
        }
        return list
    }
}
