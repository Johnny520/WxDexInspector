package com.johnny.wxdexinspector.core.report

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 报告 ZIP 打包 */
object ZipExporter {

    fun zip(srcDir: File, destZip: File): Boolean = try {
        destZip.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(destZip)).use { zos ->
            val files = srcDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
            val buf = ByteArray(1 shl 16)
            files.forEach { f ->
                zos.putNextEntry(ZipEntry(f.name))
                FileInputStream(f).use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        zos.write(buf, 0, n)
                    }
                }
                zos.closeEntry()
            }
        }
        destZip.length() > 0
    } catch (t: Throwable) {
        false
    }
}
