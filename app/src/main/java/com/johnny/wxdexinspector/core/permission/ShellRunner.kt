package com.johnny.wxdexinspector.core.permission

import com.johnny.wxdexinspector.core.log.WxLogger

/**
 * 命令执行器。
 * 说明：Shizuku 13.x 已移除公开的进程执行 API，故本工具仅通过 Root(su) 执行只读命令；
 * 免 Root 提取走文件直接读取（见 ApkExtractor），不执行任何修改类命令。
 */
object ShellRunner {

    private const val TAG = "ShellRunner"

    data class Result(val code: Int, val stdout: String, val stderr: String)

    fun runRoot(command: String): Result = exec(arrayOf("su", "-c", command))

    fun exec(command: Array<String>): Result = try {
        val p = Runtime.getRuntime().exec(command)
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        Result(code, out, err)
    } catch (t: Throwable) {
        WxLogger.e(TAG, "执行失败: ${command.joinToString(" ")}", t)
        Result(-1, "", t.message ?: "执行失败")
    }

    private fun java.io.InputStream.bufferedReader() =
        java.io.BufferedReader(java.io.InputStreamReader(this, Charsets.UTF_8))
}
