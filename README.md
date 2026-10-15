# WxDexInspector

> 微信APK静态分析工具，面向微信LSP模块开发者，用于微信新版本适配（WCX模块开发调试）

## ✨ 项目介绍

**核心目标：** 解析微信APK，检索Dex类、方法、字符串，版本差异对比，生成适配报告，辅助微信模块开发适配。

附带可选拓展功能（顺手附加，非主业）：支持任意安卓APK静态信息查看，可查看签名、Manifest、resources.arsc、识别加固、广告SDK、应用组件等。

> ⚠️ 重要提示：本工具**仅读取APK静态信息**，不提供APK修改、重打包、脱壳、破解、一键去广告功能。用户拿到分析结果后的二次修改操作，属于用户个人行为。

## 📱 运行模式

1. 纯静态模式：手动导入APK，无需任何授权
2. Shizuku免Root模式：免Root提取本机用户安装应用(含Split分包)
3. Root增强模式：提取用户应用+系统预装APP，额外支持进程动态探测

## 🧩 功能清单

### 核心功能
- 5种扫描模式：单包扫描 / 双包差异对比 / 批量扫描 / 快照对比 / 快照浏览
- DexKit深度DEX解析，自动识别Hook候选方法，解决微信混淆多方法匹配冲突
- 彩色实时日志，日志归档备份
- .wxsnap快照持久化保存，历史快照管理
- 导出全套报告，支持ZIP打包，可生成可视化关系图表

### 拓展功能（设置内可开关）
- APK基础信息与证书签名查看，指纹一键复制
- AndroidManifest清单解析、四大组件、权限、Intent检索
- resources.arsc资源字符串解析
- 加固厂商静态识别（乐固、360加固、爱加密、梆梆等，仅识别不脱壳）
- 广告SDK、第三方SDK静态识别
- APK内部文件树浏览，图片/文本资源预览

## 🎨 UI特性
- Material3 + LSPosed卡片风格，4套主题：跟随系统 / 亮色 / 暗色 / MIUI主题
- 底部导航二选一：原生导航栏 / 液态玻璃半透明悬浮导航，支持自定义透明度(0.2~0.9)
- 全局圆角弹窗，淡入淡出动画，水波纹点击反馈

## 📂 文件存储
内部存储根目录 `/storage/emulated/0/WxDexInspector/`
- apks：提取的APK/APKS包
- snapshots：.wxsnap快照
- exports：导出的ZIP报告包
- logs：日志备份
- temp：临时缓存文件

## 🛠 技术栈
- Kotlin + 少量 Java，Jetpack Compose + Material3
- DexKit 作为 DEX 解析核心
- Shizuku 免 Root 提取
- 最低适配 Android 10（API 29），目标 SDK 36（Android 16）
- 开源协议 Apache 2.0

## 📜 开源协议
Apache 2.0

开发者：Johnny
