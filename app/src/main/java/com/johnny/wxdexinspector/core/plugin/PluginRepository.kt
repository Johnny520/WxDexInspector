package com.johnny.wxdexinspector.core.plugin

import com.johnny.wxdexinspector.core.log.WxLogger
import org.json.JSONObject
import java.io.IOException

/**
 * 在线插件商店：从托管仓库拉取插件索引。
 * 索引托管在 Johnny520/wxdexinspector-plugins，多镜像依次尝试。
 */
object PluginRepository {

    private const val TAG = "PluginRepository"
    private const val OWNER = "Johnny520"
    private const val REPO = "wxdexinspector-plugins"
    private const val BRANCH = "main"

    const val STORE_HOME = "https://github.com/$OWNER/$REPO"
    const val SUBMIT_URL = "https://github.com/$OWNER/$REPO/issues"

    private val INDEX_URLS = listOf(
        "https://cdn.jsdelivr.net/gh/$OWNER/$REPO@$BRANCH/plugins.json",
        "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/plugins.json",
        "https://ghproxy.net/https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/plugins.json"
    )

    /** 拉取在线插件列表（网络 IO，需在子线程调用） */
    fun fetchIndex(): List<PluginInfo> {
        var last: Throwable? = null
        for (u in INDEX_URLS) {
            try {
                val text = String(PluginHttp.get(u), Charsets.UTF_8)
                val list = parseIndex(text)
                if (list.isNotEmpty()) return list
                last = IOException("索引为空")
            } catch (t: Throwable) {
                last = t
                WxLogger.w(TAG, "索引源不可用：$u -> ${t.message}")
            }
        }
        throw IOException("无法获取插件索引（${last?.message ?: "网络不可用"}）")
    }

    private fun parseIndex(text: String): List<PluginInfo> {
        val arr = JSONObject(text).optJSONArray("plugins") ?: return emptyList()
        val list = ArrayList<PluginInfo>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            val tagsArr = o.optJSONArray("tags")
            val tags = if (tagsArr == null) emptyList()
            else (0 until tagsArr.length()).map { tagsArr.optString(it, "") }.filter { it.isNotBlank() }
            list.add(
                PluginInfo(
                    id = id,
                    name = o.optString("name", id),
                    author = o.optString("author", "未知"),
                    version = o.optString("version", "-"),
                    type = o.optString("type", PluginType.HARDENING),
                    description = o.optString("description", ""),
                    url = o.optString("url", ""),
                    tags = tags,
                    size = o.optLong("size", 0L)
                )
            )
        }
        return list
    }
}
