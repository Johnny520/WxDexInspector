package com.johnny.wxdexinspector.core.plugin

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** 极简 HTTP GET（带体积上限与超时），供插件索引与插件数据下载使用 */
object PluginHttp {

    /** 单次下载上限 5MB，防止异常大文件 */
    private const val MAX_BYTES = 5 * 1024 * 1024

    fun get(url: String, timeoutMs: Int = 12_000): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "WxDexInspector-PluginStore")
            setRequestProperty("Accept", "*/*")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_BYTES) throw java.io.IOException("内容超出大小限制")
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** 为 GitHub raw 地址补充 jsDelivr 镜像（国内可直连） */
    fun mirrors(url: String): List<String> {
        val list = ArrayList<String>()
        list.add(url)
        val rawPrefix = "https://raw.githubusercontent.com/"
        if (url.startsWith(rawPrefix)) {
            // https://raw.githubusercontent.com/<owner>/<repo>/<branch>/<path>
            val rest = url.removePrefix(rawPrefix)
            val parts = rest.split('/')
            if (parts.size >= 4) {
                val owner = parts[0]
                val repo = parts[1]
                val branch = parts[2]
                val path = parts.drop(3).joinToString("/")
                list.add("https://cdn.jsdelivr.net/gh/$owner/$repo@$branch/$path")
                list.add("https://ghproxy.net/$url")
            }
        }
        return list
    }
}
