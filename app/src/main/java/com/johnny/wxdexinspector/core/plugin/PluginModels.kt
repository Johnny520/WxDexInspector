package com.johnny.wxdexinspector.core.plugin

/** 插件元信息（商店条目与本地安装共用） */
data class PluginInfo(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val type: String,
    val description: String,
    val url: String = "",
    val tags: List<String> = emptyList(),
    val size: Long = 0L
)

/** 已安装插件 */
data class InstalledPlugin(
    val info: PluginInfo,
    val dir: String,
    val enabled: Boolean,
    val itemCount: Int,
    val installedAt: Long
)

/**
 * 插件类型（均为「数据插件」：只提供静态特征/规则数据，不执行任何代码，
 * 因此不存在任意代码执行风险，也不涉及任何破解、篡改、脱壳能力）。
 */
object PluginType {
    /** 加固特征包：扩展加固厂商识别规则 */
    const val HARDENING = "hardening_pack"

    /** SDK 特征包：扩展第三方 SDK / 广告 SDK 识别规则 */
    const val SDK = "sdk_pack"

    /** 微信 Hook 特征包：扩展 Hook 候选特征串 */
    const val HOOK = "hook_pack"

    fun label(type: String): String = when (type) {
        HARDENING -> "加固特征包"
        SDK -> "SDK 特征包"
        HOOK -> "Hook 特征包"
        else -> "其他"
    }

    val ALL = listOf(HARDENING, SDK, HOOK)
}
