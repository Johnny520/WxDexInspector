package com.johnny.wxdexinspector.core.apk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.johnny.wxdexinspector.core.log.WxLogger

/** 已安装应用仓库（仅读取，不含任何修改操作） */
object AppRepository {

    private const val TAG = "AppRepository"

    /**
     * @param includeSystem 是否包含系统预装应用（仅 Root 模式建议开启）
     */
    fun listInstalled(context: Context, includeSystem: Boolean): List<InstalledApp> {
        val pm = context.packageManager
        val out = ArrayList<InstalledApp>()
        try {
            val pkgs = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            for (pi in pkgs) {
                val ai = pi.applicationInfo ?: continue
                val isSystem = (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                if (isSystem && !includeSystem) continue
                out.add(
                    InstalledApp(
                        packageName = pi.packageName,
                        label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pi.packageName),
                        versionName = pi.versionName ?: "",
                        versionCode = runCatching {
                            if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
                        }.getOrDefault(0L),
                        isSystem = isSystem,
                        apkPath = ai.sourceDir,
                        uid = ai.uid,
                        icon = runCatching { ai.loadIcon(pm) }.getOrNull()
                    )
                )
            }
        } catch (t: Throwable) {
            WxLogger.e(TAG, "枚举已安装应用失败", t)
        }
        return out.sortedWith(compareBy({ it.isSystem }, { it.label }))
    }

    fun findWeChat(context: Context): InstalledApp? =
        listInstalled(context, includeSystem = true).firstOrNull { it.packageName == "com.tencent.mm" }
}
