package com.johnny.wxdexinspector.core.update

import com.johnny.wxdexinspector.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内「检查更新」。
 * 通过 GitHub Releases API 获取最新版本，与本机 BuildConfig.VERSION_NAME 比较。
 * 纯只读网络请求，不做任何自动下载/安装。
 */
object UpdateChecker {

    const val GITHUB_REPO = "https://github.com/Johnny520/WxDexInspector"
    const val RELEASE_PAGE = "https://github.com/Johnny520/WxDexInspector/releases/latest"
    const val ISSUES_PAGE = "https://github.com/Johnny520/WxDexInspector/issues"

    private const val LATEST_API =
        "https://api.github.com/repos/Johnny520/WxDexInspector/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,
        val currentVersion: String,
        val releaseUrl: String,
        val downloadUrl: String,
        val notes: String,
        val publishedAt: String,
        val hasUpdate: Boolean
    )

    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    /** 网络请求，必须在子线程调用（否则会阻塞 UI） */
    fun check(): UpdateInfo {
        val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "WxDexInspector/" + BuildConfig.VERSION_NAME)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("服务器返回 HTTP $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(text)

            val tag = obj.optString("tag_name", "")
                .removePrefix("v").removePrefix("V").trim()
            val releaseUrl = obj.optString("html_url", RELEASE_PAGE)
            val notes = obj.optString("body", "")
            val publishedAt = obj.optString("published_at", "")

            var download = releaseUrl
            obj.optJSONArray("assets")?.let { assets ->
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val n = a.optString("name", "")
                    if (n.endsWith(".apk", true) && n.contains("release", true)) {
                        download = a.optString("browser_download_url", releaseUrl)
                        break
                    }
                }
            }

            val latest = tag.ifBlank { currentVersion }
            return UpdateInfo(
                latestVersion = latest,
                currentVersion = currentVersion,
                releaseUrl = releaseUrl,
                downloadUrl = download,
                notes = notes,
                publishedAt = publishedAt,
                hasUpdate = isNewer(latest, currentVersion)
            )
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** 语义化版本比较：latest > current 时返回 true */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parse(latest)
        val b = parse(current)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parse(v: String): List<Int> =
        v.split('.', '-', '_', ' ')
            .mapNotNull { seg -> seg.trim().takeWhile { it.isDigit() }.toIntOrNull() }
}
