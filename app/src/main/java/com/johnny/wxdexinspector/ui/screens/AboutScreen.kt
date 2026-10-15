package com.johnny.wxdexinspector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxdexinspector.BuildConfig
import com.johnny.wxdexinspector.core.update.UpdateChecker
import com.johnny.wxdexinspector.core.util.openUrl
import com.johnny.wxdexinspector.ui.components.ActionRow
import com.johnny.wxdexinspector.ui.components.CheckUpdateActionRow
import com.johnny.wxdexinspector.ui.components.SectionCard
import com.johnny.wxdexinspector.ui.components.SettingRow
import com.johnny.wxdexinspector.ui.dialog.DISCLAIMER_TEXT
import com.johnny.wxdexinspector.ui.dialog.InfoDialog
import com.johnny.wxdexinspector.ui.theme.WxRadius

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    var showDisclaimer by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // 顶部品牌卡
        Surface(
            shape = RoundedCornerShape(WxRadius.card),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Wx",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "WxDexInspector",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "微信 APK 静态分析工具箱",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        SectionCard(title = "关于本项目") {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "面向微信 LSP / Xposed 模块开发者：解析微信 APK，检索 Dex 中的类、方法、字段、字符串与调用关系，" +
                        "内置微信特征库匹配 Hook 候选，对比版本差异并生成适配报告。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                FeatureBullet("五种扫描模式：单包全量 / 双包差异 / 多版本批量 / 快照对比 / 快照浏览")
                FeatureBullet("组件与权限检索：四大组件、权限、Intent-Filter、Manifest 原文，点击即复制")
                FeatureBullet("报告导出：all_classes.txt、all_methods.csv、hook_candidate.json、summary_report.md")
                FeatureBullet("三层运行模式：纯静态 / Shizuku / Root，权限不足自动降级")
                FeatureBullet("拓展能力：加固识别、广告 SDK 检测、resources.arsc 解析、APK 资源预览")
            }
        }

        SectionCard(title = "版本与更新") {
            SettingRow("当前版本", "v${BuildConfig.VERSION_NAME} · Apache-2.0", icon = Icons.Filled.Description)
            CheckUpdateActionRow()
        }

        SectionCard(title = "开源与支持") {
            ActionRow("项目主页 (GitHub)", Icons.Filled.Link) { context.openUrl(UpdateChecker.GITHUB_REPO) }
            ActionRow("问题反馈 / 建议", Icons.Filled.BugReport) { context.openUrl(UpdateChecker.ISSUES_PAGE) }
            ActionRow("查看开源协议", Icons.Filled.Gavel) { showLicense = true }
            ActionRow("查看免责声明", Icons.Filled.Description) { showDisclaimer = true }
        }

        SectionCard(title = "技术栈") {
            Column(Modifier.padding(16.dp)) {
                TechLine("语言", "Kotlin / Java")
                TechLine("UI", "Jetpack Compose + Material 3")
                TechLine("DEX 解析", "DexKit")
                TechLine("液态玻璃", "Kyant0/AndroidLiquidGlass (backdrop)")
                TechLine("权限增强", "Shizuku 13")
            }
        }

        Spacer(Modifier.height(108.dp))
    }

    if (showDisclaimer) {
        InfoDialog("免责声明 & 使用须知", DISCLAIMER_TEXT) { showDisclaimer = false }
    }
    if (showLicense) {
        InfoDialog(
            "开源协议",
            "Apache License 2.0\n\nCopyright (c) 2026 Johnny520\n\n完整协议文本见仓库根目录 LICENSE 文件。"
        ) { showLicense = false }
    }
}

@Composable
private fun FeatureBullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TechLine(key: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
