package com.johnny.wxdexinspector.core.permission

import com.johnny.wxdexinspector.core.log.WxLogger
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 命令执行器：Root(su) / Shizuku / 本地进程三种通道。
 * 仅用于读取静息信息（如 pm path / list packages），不做任何修改操作。
 */
object ShellRunner {

    private const val TAG = "ShellRunner"

    data class Result(val code: Int, val stdout: String, val stderr: String)

    fun runRoot(command: String): Result {
        return execLocal(arrayOf("su", "-c", command))
    }

    fun runShizuku(command: Array<String>): Result? = try {
        if (!PermissionManager.isShizukuAuthorized()) return null
        val process = Shizuku.newProcess(command, null, null)
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()
        Result(code, out, err)
    } catch (t: Throwable) {
        WxLogger.e(TAG, "Shizuku 执行失败: ${command.joinToString(" ")}", t)
        null
    }

    fun execLocal(command: Array<String>): Result = try {
        val p = Runtime.getRuntime().exec(command)
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        Result(code, out, err)
    } catch (t: Throwable) {
        WxLogger.e(TAG, "本地执行失败: ${command.joinToString(" ")}", t)
        Result(-1, "", t.message ?: "执行失败")
    }

    private fun java.io.InputStream.bufferedReader(): BufferedReader =
        BufferedReader(InputStreamReader(this, Charsets.UTF_8))
}
