package com.johnny.wxdexinspector.core.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
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
            doRootProbe && canExecuteRoot() -> PermState.GRANTED
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
}
