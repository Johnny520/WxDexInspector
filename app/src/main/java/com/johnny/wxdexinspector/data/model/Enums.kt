package com.johnny.wxdexinspector.data.model

/** 全局主题模式 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("亮色主题"),
    DARK("暗色主题"),
    MIUI("MIUI风格主题")
}

/** 底部导航栏风格 */
enum class NavStyle(val label: String) {
    NATIVE("原生标准导航栏"),
    GLASS("液态玻璃悬浮导航")
}

/** 权限运行模式设置 */
enum class WorkMode(val label: String) {
    AUTO("自动检测模式（推荐）"),
    STATIC("纯静态模式"),
    SHIZUKU("Shizuku 增强模式"),
    ROOT("Root 模式")
}

/** 权限三层：静态 / Shizuku / Root */
enum class RunMode(val label: String) {
    STATIC("纯静态模式"),
    SHIZUKU("Shizuku免Root增强模式"),
    ROOT("Root最高权限模式")
}

/** 五种扫描模式 */
enum class ScanMode(
    val label: String,
    val desc: String
) {
    SINGLE("单包全量扫描", "单个APK完整DEX解析"),
    DIFF("双包差异对比", "新旧微信APK对比类/方法/字段变更"),
    BATCH("多版本批量扫描", "一次性导入多个微信APK并行分析"),
    SNAPSHOT_DIFF("快照对比", "基于保存的 .wxsnap 快照做差异比对"),
    SNAPSHOT_BROWSE("快照浏览", "查看历史快照的分析报告")
}

/** 权限状态 */
enum class PermState(val label: String) {
    GRANTED("已授权"),
    DENIED("未授权"),
    UNAVAILABLE("不可用"),
    UNKNOWN("未知");

    val ok: Boolean get() = this == GRANTED
}

/** 日志级别 */
enum class LogLevel { INFO, WARN, ERROR, DEBUG }
