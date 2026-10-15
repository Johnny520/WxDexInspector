package com.johnny.wxdexinspector.core.dex

import com.johnny.wxdexinspector.core.plugin.PluginRegistry

/**
 * 内置微信模块特征库。
 * 每条特征包含若干「字符串常量」判据，命中即视为 Hook 候选方法。
 * 说明：仅使用公开字符串常量做静态匹配，不含任何破解/篡改能力。
 */
data class HookFeature(
    val id: String,
    val description: String,
    /** 必须全部命中的字符串（AND） */
    val allStrings: List<String> = emptyList(),
    /** 命中任意一个即可（OR） */
    val anyStrings: List<String> = emptyList(),
    /** 类名包含的提示（加权） */
    val classHints: List<String> = emptyList()
)

object HookFeatureLibrary {

    val FEATURES: List<HookFeature> = listOf(
        HookFeature(
            id = "send_text_msg",
            description = "发送消息（文本）NetSceneSendMsg",
            allStrings = listOf("newsendmsg"),
            anyStrings = listOf("MicroMsg.NetSceneSendMsg", "/cgi-bin/micromsg-bin/newsendmsg", "NetSceneSendMsg"),
            classHints = listOf("NetSceneSendMsg")
        ),
        HookFeature(
            id = "recv_msg",
            description = "接收消息分发",
            anyStrings = listOf("MicroMsg.MMReceivingServiceUtil", "onNewSyncEnd", "startSync"),
            classHints = listOf("Receiving", "Sync")
        ),
        HookFeature(
            id = "chatting_ui",
            description = "聊天界面 ChattingUI",
            anyStrings = listOf("ChattingUI", "MicroMsg.ChattingUI"),
            classHints = listOf("ChattingUI", "Chatting")
        ),
        HookFeature(
            id = "db_rcontact",
            description = "联系人数据库 rcontact 表",
            allStrings = listOf("rcontact", "chatroom")
        ),
        HookFeature(
            id = "revoke_msg",
            description = "消息撤回",
            anyStrings = listOf("revoke", "SysCmdMsgRevoke", "MicroMsg.Revoke"),
            classHints = listOf("Revoke")
        ),
        HookFeature(
            id = "moments",
            description = "朋友圈 Sns",
            anyStrings = listOf("snsObject", "MicroMsg.Sns", "SnsCommentFooter"),
            classHints = listOf("Sns", "Moments")
        ),
        HookFeature(
            id = "account_info",
            description = "账号信息",
            anyStrings = listOf("MicroMsg.AccountInfo", "wxdbf9c0b2f2b019a1"),
            classHints = listOf("AccountInfo")
        ),
        HookFeature(
            id = "emoji",
            description = "表情",
            anyStrings = listOf("MicroMsg.Emoji", "emojiInfo"),
            classHints = listOf("Emoji")
        ),
        HookFeature(
            id = "finder",
            description = "视频号 Finder",
            anyStrings = listOf("MicroMsg.Finder", "finderFeed", "objectNonceId"),
            classHints = listOf("Finder")
        ),
        HookFeature(
            id = "pay_lucky",
            description = "红包 LuckyMoney",
            anyStrings = listOf("LuckyMoney", "luckyMoney"),
            classHints = listOf("LuckyMoney", "RedEnvelope")
        ),
        HookFeature(
            id = "tinker_hotfix",
            description = "热修复/加固入口",
            anyStrings = listOf("Tinker", "StubApp", "libjiagu"),
            classHints = listOf("StubApp", "Tinker")
        )
    )

    /** 内置特征 + 插件扩展特征 */
    val allFeatures: List<HookFeature>
        get() = FEATURES + PluginRegistry.extraHookFeatures()

    @Volatile
    private var indexCache: Map<String, List<HookFeature>>? = null

    /** string -> 关联特征（插件变更后可通过 invalidate() 重建） */
    val stringIndex: Map<String, List<HookFeature>>
        get() = indexCache ?: synchronized(this) {
            indexCache ?: buildIndex().also { indexCache = it }
        }

    /** 插件安装/启停后调用，使特征索引失效重建 */
    fun invalidate() {
        synchronized(this) { indexCache = null }
    }

    private fun buildIndex(): Map<String, List<HookFeature>> {
        val map = HashMap<String, MutableList<HookFeature>>()
        allFeatures.forEach { f ->
            (f.allStrings + f.anyStrings).forEach { s ->
                map.getOrPut(s.lowercase()) { mutableListOf() }.add(f)
            }
        }
        return map
    }
}
