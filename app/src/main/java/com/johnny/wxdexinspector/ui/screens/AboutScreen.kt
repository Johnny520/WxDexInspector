package com.johnny.wxdexinspector.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("WxDexInspector", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "微信APK分析工具箱，专为微信LSP模块开发者打造",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))
        Para(
            "核心用途：解析微信APK，自动检索Dex方法、类、字符串，对比版本差异，生成适配报告，辅助微信模块开发与版本适配。"
        )

        Spacer(Modifier.height(16.dp))
        Head("附带拓展能力（可选开启，顺手附加功能，非本软件主要目标）")
        Para("开启拓展功能后，可对任意安卓APK做通用静态信息查看：")
        Bullet("APK基础信息、签名证书、AndroidManifest清单")
        Bullet("resources.arsc 资源表解析，查看字符串与资源ID")
        Bullet("静态识别加固厂商、广告/第三方SDK")
        Bullet("查看四大组件、权限、静态Intent调用、预览APK内资源")

        Spacer(Modifier.height(16.dp))
        Warning(
            "⚠️ 重要说明：拓展功能仅为附带增值能力。软件主业始终是微信模块版本适配。\n\n" +
                "本工具仅读取APK静态信息，不提供APK修改、重打包、脱壳、一键去广告等编辑功能；" +
                "用户使用第三方工具（MT管理器等）对APK进行二次修改，属于用户个人行为。"
        )

        Spacer(Modifier.height(18.dp))
        Head("项目信息")
        Para("开发者：Johnny")
        Para("开源协议：Apache 2.0")
        Para("当前版本：1.0.0")

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Head(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp, bottom = 3.dp))
}

@Composable
private fun Warning(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
