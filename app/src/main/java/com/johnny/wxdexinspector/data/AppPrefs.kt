package com.johnny.wxdexinspector.data

import android.content.Context
import android.content.SharedPreferences
import com.johnny.wxdexinspector.data.model.NavStyle
import com.johnny.wxdexinspector.data.model.ScanMode
import com.johnny.wxdexinspector.data.model.ThemeMode
import com.johnny.wxdexinspector.data.model.WorkMode

/**
 * 全局偏好设置。所有开关/选项的唯一真相源。
 */
object AppPrefs {

    private const val NAME = "wxdexinspector_prefs"

    private const val K_THEME = "theme_mode"
    private const val K_NAV = "nav_style"
    private const val K_GLASS_ALPHA = "glass_alpha"
    private const val K_WORK_MODE = "work_mode"
    private const val K_LAST_SCAN_MODE = "last_scan_mode"
    private const val K_STORAGE_ROOT = "storage_root"
    private const val K_AUTO_CLEAN_TEMP = "auto_clean_temp"
    private const val K_BACKUP_LOG = "backup_log"
    private const val K_VISUAL_REPORT = "visual_report"
    private const val K_EXT_MASTER = "ext_master"
    private const val K_EXT_HARDENING = "ext_hardening"
    private const val K_EXT_SDK = "ext_sdk"
    private const val K_EXT_COMPONENT = "ext_component"
    private const val K_EXT_PREVIEW = "ext_preview"
    private const val K_EXT_ARSC = "ext_arsc"
    private const val K_DISCLAIMER = "disclaimer_accepted"

    @Volatile
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        }
    }

    // ---- 主题 ----
    var themeMode: ThemeMode
        get() = enumOf(sp.getString(K_THEME, null), ThemeMode.SYSTEM)
        set(v) = sp.edit().putString(K_THEME, v.name).apply()

    // ---- 导航栏 ----
    var navStyle: NavStyle
        get() = enumOf(sp.getString(K_NAV, null), NavStyle.GLASS)
        set(v) = sp.edit().putString(K_NAV, v.name).apply()

    var glassAlpha: Float
        get() = sp.getFloat(K_GLASS_ALPHA, 0.55f).coerceIn(MIN_ALPHA, MAX_ALPHA)
        set(v) = sp.edit().putFloat(K_GLASS_ALPHA, v.coerceIn(MIN_ALPHA, MAX_ALPHA)).apply()

    // ---- 工作模式 ----
    var workMode: WorkMode
        get() = enumOf(sp.getString(K_WORK_MODE, null), WorkMode.AUTO)
        set(v) = sp.edit().putString(K_WORK_MODE, v.name).apply()

    var lastScanMode: ScanMode
        get() = enumOf(sp.getString(K_LAST_SCAN_MODE, null), ScanMode.SINGLE)
        set(v) = sp.edit().putString(K_LAST_SCAN_MODE, v.name).apply()

    // ---- 存储 ----
    var storageRoot: String
        get() = sp.getString(K_STORAGE_ROOT, "") ?: ""
        set(v) = sp.edit().putString(K_STORAGE_ROOT, v).apply()

    var autoCleanTemp: Boolean
        get() = sp.getBoolean(K_AUTO_CLEAN_TEMP, true)
        set(v) = sp.edit().putBoolean(K_AUTO_CLEAN_TEMP, v).apply()

    var backupLog: Boolean
        get() = sp.getBoolean(K_BACKUP_LOG, true)
        set(v) = sp.edit().putBoolean(K_BACKUP_LOG, v).apply()

    // ---- 扫描功能 ----
    var visualReport: Boolean
        get() = sp.getBoolean(K_VISUAL_REPORT, true)
        set(v) = sp.edit().putBoolean(K_VISUAL_REPORT, v).apply()

    // ---- 拓展功能 ----
    var extMaster: Boolean
        get() = sp.getBoolean(K_EXT_MASTER, true)
        set(v) = sp.edit().putBoolean(K_EXT_MASTER, v).apply()

    var extHardening: Boolean
        get() = sp.getBoolean(K_EXT_HARDENING, true)
        set(v) = sp.edit().putBoolean(K_EXT_HARDENING, v).apply()

    var extSdk: Boolean
        get() = sp.getBoolean(K_EXT_SDK, true)
        set(v) = sp.edit().putBoolean(K_EXT_SDK, v).apply()

    var extComponent: Boolean
        get() = sp.getBoolean(K_EXT_COMPONENT, true)
        set(v) = sp.edit().putBoolean(K_EXT_COMPONENT, v).apply()

    var extPreview: Boolean
        get() = sp.getBoolean(K_EXT_PREVIEW, true)
        set(v) = sp.edit().putBoolean(K_EXT_PREVIEW, v).apply()

    var extArsc: Boolean
        get() = sp.getBoolean(K_EXT_ARSC, true)
        set(v) = sp.edit().putBoolean(K_EXT_ARSC, v).apply()

    // ---- 免责声明 ----
    var disclaimerAccepted: Boolean
        get() = sp.getBoolean(K_DISCLAIMER, false)
        set(v) = sp.edit().putBoolean(K_DISCLAIMER, v).apply()

    /** 拓展开关是否在整体上生效：总开关开即生效，分项各自独立 */
    val extActive: Boolean
        get() = extMaster

    private inline fun <reified T : Enum<T>> enumOf(name: String?, def: T): T =
        runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(def)

    const val MIN_ALPHA = 0.2f
    const val MAX_ALPHA = 0.9f
}
