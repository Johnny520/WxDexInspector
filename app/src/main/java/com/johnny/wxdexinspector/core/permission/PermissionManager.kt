package com.johnny.wxdexinspector.core.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.johnny.wxdexinspector.core.log.WxLogger
import com.johnny.wxdexinspector.data.AppPrefs
import com.johnny.wxdexinspector.data.model.PermState
import com.johnny.wxdexinspector.data.model.RunMode
import com.johnny.wxdexinspector.data.model.WorkMode
import rikka.shizuku.Shizuku
import java.io.File

/**
 * 权限检测与运行模式决议。
 * 三层：纯静态 / Shizuku免Root / Root最高权限。
 */
object PermissionManager {

    private const val TAG = "Permission"
    private const val REQ_SHIZUKU = 1001

    data class Snapshot(
        val root: PermState,
        val shizuku: PermState,
        val storage: PermState,
        val effective: RunMode
    )

    // ---------- Root ----------
    fun isRootAvailable(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sbin/su",
            "/vendor/bin/su", "/su/bin/su", "/magisk/.core/bin/su", "/system/bin/failsafe/su",
            "/data/adb/magisk/su", "/debug_ramdisk/su"
        )
        if (paths.any { File(it).exists() }) return true
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out.isNotEmpty()
        } catch (t: Throwable) {
            false
        }
    }

    /** 实际尝试执行 su -c id 判定是否已授权 */
    fun canExecuteRoot(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = p.inputStream.bufferedReader().readText()
        p.errorStream.close()
        p.waitFor()
        out.contains("uid=0")
    } catch (t: Throwable) {
        false
    }

    /**
     * 主动申请 Root 授权：执行 `su -c id`。
     * 首次调用会触发授权管理器（Magisk / KernelSU / SuperSU）的授权弹窗，等待用户点击。
     * 必须在子线程调用（会阻塞等待用户操作）。
     * @param timeoutMs 等待用户授权的超时时间
     * @return true 表示已获得 uid=0（授权成功）
     */
    fun requestRoot(timeoutMs: Long = 120_000L): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                WxLogger.w(TAG, "Root 授权等待超时（${timeoutMs / 1000}s），已终止本次请求")
                process.destroy()
                return false
            }
            val out = process.inputStream.bufferedReader().readText()
            runCatching { process.errorStream.close() }
            WxLogger.i(TAG, "su -c id 返回: ${out.trim()}")
            val granted = out.contains("uid=0")
            if (granted) AppPrefs.rootGranted = true
            granted
        } catch (t: Throwable) {
            WxLogger.e(TAG, "申请 Root 权限失败", t)
            false
        } finally {
            runCatching { process?.destroy() }
        }
    }

    /** 按系统版本裁剪出需要动态申请的运行时权限 */
    fun runtimePermissions(): Array<String> {
        val list = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            list.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            list.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return list.toTypedArray()
    }

    /** 跳转系统「所有文件访问权限」设置页（Android 11+） */
    fun openAllFilesSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { t ->
            WxLogger.e(TAG, "打开应用级所有文件访问页失败，尝试通用页", t)
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { t2 ->
                WxLogger.e(TAG, "打开所有文件访问设置页失败", t2)
            }
        }
    }

    // ---------- Shizuku ----------
    fun isShizukuInstalled(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.getVersion() > 0
    } catch (t: Throwable) {
        false
    }

    fun isShizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun isShizukuAuthorized(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun requestShizukuPermission(onResult: (Boolean) -> Unit) {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                onResult(false); return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                onResult(true); return
            }
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    onResult(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQ_SHIZUKU)
        } catch (t: Throwable) {
            WxLogger.e(TAG, "请求 Shizuku 授权失败", t)
            onResult(false)
        }
    }

    // ---------- 存储 ----------
    fun isStorageGranted(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        } catch (t: Throwable) {
            false
        }
    }

    // ---------- 决议 ----------
    fun snapshot(context: Context, doRootProbe: Boolean = false): Snapshot {
        val root = when {
            doRootProbe && canExecuteRoot() -> {
                AppPrefs.rootGranted = true
                PermState.GRANTED
            }
            AppPrefs.rootGranted -> PermState.GRANTED
            isRootAvailable() -> PermState.DENIED
            else -> PermState.UNAVAILABLE
        }
        val shizuku = when {
            !isShizukuInstalled() -> PermState.UNAVAILABLE
            isShizukuAuthorized() -> PermState.GRANTED
            else -> PermState.DENIED
        }
        val storage = if (isStorageGranted(context)) PermState.GRANTED else PermState.DENIED
        return Snapshot(root, shizuku, storage, resolveMode(root, shizuku))
    }

    fun resolveMode(context: Context): RunMode = snapshot(context).effective

    private fun resolveMode(root: PermState, shizuku: PermState): RunMode {
        return when (AppPrefs.workMode) {
            WorkMode.STATIC -> RunMode.STATIC
            WorkMode.SHIZUKU -> if (shizuku.ok) RunMode.SHIZUKU else RunMode.STATIC
            WorkMode.ROOT -> if (root.ok) RunMode.ROOT else if (shizuku.ok) RunMode.SHIZUKU else RunMode.STATIC
            WorkMode.AUTO -> when {
                root.ok -> RunMode.ROOT
                shizuku.ok -> RunMode.SHIZUKU
                else -> RunMode.STATIC
            }
        }
    }

    // ---------- 通知 ----------
    fun isNotificationGranted(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // 13 以下安装即授予
        }
    } catch (t: Throwable) {
        true
    }

    // ---------- 权限总览 ----------
    data class PermItem(
        val title: String,
        val hint: String,
        val state: PermState,
        val required: Boolean
    )

    /** 该类型工具真正需要的权限清单及其实时状态 */
    fun checklist(context: Context, doRootProbe: Boolean = false): List<PermItem> {
        val snap = snapshot(context, doRootProbe)
        return listOf(
            PermItem(
                "文件存储（所有文件访问）",
                "导出报告 / 快照 / 提取 APK 到公共目录",
                snap.storage, true
            ),
            PermItem(
                "通知",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    "扫描完成等提示（Android 13+）"
                else "系统版本低于 13，安装即授予",
                if (isNotificationGranted(context)) PermState.GRANTED else PermState.DENIED,
                false
            ),
            PermItem("查询已安装应用", "列出手机内已安装 APK（普通权限，安装即授予）", PermState.GRANTED, true),
            PermItem("网络访问", "检查更新 / 在线插件市场", PermState.GRANTED, true),
            PermItem("Root 权限", "免复制、直读任意目录下的 APK", snap.root, false),
            PermItem("Shizuku 权限", "免 Root 提权，提取已安装 APK", snap.shizuku, false)
        )
    }
}
