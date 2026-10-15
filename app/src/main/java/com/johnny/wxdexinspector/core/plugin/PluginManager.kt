package com.johnny.wxdexinspector.core.plugin

import android.content.Context
import com.johnny.wxdexinspector.core.StorageManager
import com.johnny.wxdexinspector.core.log.WxLogger
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * 插件安装 / 卸载 / 启停。
 * 插件 = 一份 JSON 特征数据（不执行任何代码），安装后合并进扫描特征库。
 */
object PluginManager {

    private const val TAG = "PluginManager"
    private const val PREFS = "wxdi_plugins"

    fun root(context: Context): File = StorageManager.pluginsDir(context)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, id: String): Boolean = prefs(context).getBoolean("en_$id", true)

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("en_$id", enabled).apply()
        PluginRegistry.reload(context)
    }

    fun isInstalled(context: Context, id: String): Boolean = File(root(context), safeId(id)).isDirectory

    fun installed(context: Context): List<InstalledPlugin> {
        val base = root(context)
        if (!base.isDirectory) return emptyList()
        return base.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val info = readInfo(dir) ?: return@mapNotNull null
                InstalledPlugin(
                    info = info,
                    dir = dir.absolutePath,
                    enabled = isEnabled(context, info.id),
                    itemCount = countItems(File(dir, "payload.json")),
                    installedAt = dir.lastModified()
                )
            }
            ?.sortedByDescending { it.installedAt }
            ?: emptyList()
    }

    /** 下载并安装插件。含网络 IO，必须在子线程调用。 */
    fun install(context: Context, info: PluginInfo): InstalledPlugin {
        if (info.id.isBlank()) throw IOException("插件 ID 为空")
        if (info.url.isBlank()) throw IOException("插件缺少下载地址")

        var last: Throwable? = null
        var bytes: ByteArray? = null
        for (u in PluginHttp.mirrors(info.url)) {
            val r = runCatching { PluginHttp.get(u) }
            if (r.isSuccess) {
                bytes = r.getOrNull()
                break
            }
            last = r.exceptionOrNull()
            WxLogger.w(TAG, "下载失败 $u -> ${last?.message}")
        }
        val data = bytes ?: throw IOException("下载失败：${last?.message ?: "网络不可用"}")

        val text = String(data, Charsets.UTF_8)
        JSONObject(text) // 校验必须是合法 JSON

        val dir = File(root(context), safeId(info.id))
        if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建插件目录")

        File(dir, "plugin.json").writeText(infoToJson(info), Charsets.UTF_8)
        File(dir, "payload.json").writeText(text, Charsets.UTF_8)
        prefs(context).edit().putBoolean("en_${info.id}", true).apply()

        WxLogger.i(TAG, "插件已安装：${info.id}（${data.size} 字节）")
        PluginRegistry.reload(context)
        return installed(context).firstOrNull { it.info.id == info.id }
            ?: InstalledPlugin(info, dir.absolutePath, true, 0, System.currentTimeMillis())
    }

    fun uninstall(context: Context, id: String): Boolean = runCatching {
        val dir = File(root(context), safeId(id))
        val ok = !dir.exists() || dir.deleteRecursively()
        prefs(context).edit().remove("en_$id").apply()
        PluginRegistry.reload(context)
        ok
    }.getOrDefault(false)

    // ---------- helpers ----------
    /** 防止路径穿越：只保留安全字符 */
    private fun safeId(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifBlank { "plugin" }

    private fun readInfo(dir: File): PluginInfo? = runCatching {
        val o = JSONObject(File(dir, "plugin.json").readText(Charsets.UTF_8))
        PluginInfo(
            id = o.optString("id", dir.name),
            name = o.optString("name", dir.name),
            author = o.optString("author", "未知"),
            version = o.optString("version", "-"),
            type = o.optString("type", PluginType.HARDENING),
            description = o.optString("description", ""),
            url = o.optString("url", "")
        )
    }.getOrNull()

    private fun infoToJson(i: PluginInfo): String = JSONObject().apply {
        put("id", i.id)
        put("name", i.name)
        put("author", i.author)
        put("version", i.version)
        put("type", i.type)
        put("description", i.description)
        put("url", i.url)
    }.toString(2)

    private fun countItems(payload: File): Int = runCatching {
        val o = JSONObject(payload.readText(Charsets.UTF_8))
        o.optJSONArray("rules")?.length()
            ?: o.optJSONArray("sdks")?.length()
            ?: o.optJSONArray("features")?.length()
            ?: 0
    }.getOrDefault(0)
}
