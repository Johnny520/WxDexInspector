package com.johnny.wxdexinspector.core.ext

import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile

/**
 * 二进制资源解析：AndroidManifest.xml(AXML) 与 resources.arsc 字符串池。
 * 纯自研解析，不依赖任何隐藏 API，仅做静态读取。
 */
object BinaryRes {

    private fun u8(d: ByteArray, o: Int): Int = d[o].toInt() and 0xFF
    private fun u16(d: ByteArray, o: Int): Int =
        (d[o].toInt() and 0xFF) or ((d[o + 1].toInt() and 0xFF) shl 8)

    private fun u32(d: ByteArray, o: Int): Int =
        (d[o].toInt() and 0xFF) or ((d[o + 1].toInt() and 0xFF) shl 8) or
            ((d[o + 2].toInt() and 0xFF) shl 16) or ((d[o + 3].toInt() and 0xFF) shl 24)

    data class XmlNode(
        val name: String,
        val attrs: Map<String, String>,
        val children: MutableList<XmlNode> = mutableListOf()
    )

    // ---------------- String Pool ----------------
    fun readStringPool(d: ByteArray, chunkStart: Int): List<String> {
        if (chunkStart + 8 > d.size) return emptyList()
        val type = u16(d, chunkStart)
        if (type != 0x0001) return emptyList()
        val stringCount = u32(d, chunkStart + 8)
        val flags = u32(d, chunkStart + 16)
        val stringsStart = u32(d, chunkStart + 20)
        val isUtf8 = (flags and (1 shl 8)) != 0
        val offsetsBase = chunkStart + 28
        val stringsBase = chunkStart + stringsStart
        val out = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val off = u32(d, offsetsBase + i * 4)
            out.add(readPoolString(d, stringsBase + off, isUtf8))
        }
        return out
    }

    private fun readPoolString(d: ByteArray, off: Int, isUtf8: Boolean): String {
        var o = off
        if (o < 0 || o >= d.size) return ""
        return try {
            if (isUtf8) {
                var len = u8(d, o); o++
                if (len and 0x80 != 0) { len = ((len and 0x7F) shl 8) or u8(d, o); o++ }
                var len2 = u8(d, o); o++
                if (len2 and 0x80 != 0) { len2 = ((len2 and 0x7F) shl 8) or u8(d, o); o++ }
                val end = minOf(o + len2, d.size)
                String(d, o, end - o, Charsets.UTF_8)
            } else {
                // UTF-16 字符串：长度是 2 字节（length16），高位为 1 时再取一次 2 字节扩展
                var len = u16(d, o); o += 2
                if (len and 0x8000 != 0) { len = ((len and 0x7FFF) shl 16) or u16(d, o); o += 2 }
                val bytes = minOf(len * 2, d.size - o)
                String(d, o, bytes, Charsets.UTF_16LE)
            }
        } catch (t: Throwable) {
            ""
        }
    }

    // ---------------- AXML ----------------
    fun parseManifest(data: ByteArray): XmlNode? {
        var pos = 8
        var strings: List<String> = emptyList()
        var root: XmlNode? = null
        val stack = ArrayDeque<XmlNode>()

        while (pos + 8 <= data.size) {
            val type = u16(data, pos)
            val csize = u32(data, pos + 4)
            if (csize < 8 || pos + csize > data.size) break
            when (type) {
                0x0001 -> strings = readStringPool(data, pos)
                0x0102 -> {
                    val nameIdx = u32(data, pos + 20)
                    val attrStart = u16(data, pos + 24)
                    val attrSize = u16(data, pos + 26)
                    val attrCount = u16(data, pos + 28)
                    val name = strings.getOrElse(nameIdx) { "" }
                    val attrs = LinkedHashMap<String, String>()
                    // 属性数组起点 = 本 chunk 起点 + headerSize(16) + attributeStart
                    val headerSize = u16(data, pos + 2)
                    val attrBase = pos + headerSize + attrStart
                    val size = if (attrSize <= 0) 20 else attrSize
                    for (i in 0 until attrCount) {
                        val a = attrBase + i * size
                        if (a + 20 > data.size) break
                        val aNameIdx = u32(data, a + 4)
                        val rawIdx = u32(data, a + 8)
                        val dataType = u8(data, a + 15)
                        val dataVal = u32(data, a + 16)
                        val aName = strings.getOrElse(aNameIdx) { "" }
                        val value = when {
                            rawIdx != -1 && rawIdx < strings.size -> strings[rawIdx]
                            dataType == 0x03 && dataVal in strings.indices -> strings[dataVal]
                            dataType == 0x12 -> (dataVal != 0).toString()
                            dataType == 0x10 -> dataVal.toString()
                            dataType == 0x01 -> "@0x%08x".format(dataVal)
                            else -> dataVal.toString()
                        }
                        attrs[aName] = value
                    }
                    val node = XmlNode(name, attrs)
                    val parent = stack.lastOrNull()
                    if (parent != null) parent.children.add(node) else if (root == null) root = node
                    stack.addLast(node)
                }
                0x0103 -> {
                    if (stack.isNotEmpty()) stack.removeLast()
                }
            }
            pos += csize
        }
        return root
    }

    // ---------------- resources.arsc ----------------
    /** 提取资源表全局字符串池（去重，限长） */
    fun readArscStrings(apkPath: String, limit: Int = 20000): List<String> {
        val bytes = readZipEntry(apkPath, "resources.arsc") ?: return emptyList()
        val out = LinkedHashSet<String>()
        try {
            var pos = 0
            // 资源表头 chunk
            if (u16(bytes, 0) == 0x0002) {
                pos = u16(bytes, 2).let { if (it >= 12) it else 12 }
            }
            while (pos + 8 <= bytes.size && out.size < limit) {
                val type = u16(bytes, pos)
                val csize = u32(bytes, pos + 4)
                if (csize < 8 || pos + csize > bytes.size) break
                if (type == 0x0001) {
                    readStringPool(bytes, pos).forEach { if (it.isNotBlank()) out.add(it) }
                }
                pos += csize
            }
        } catch (t: Throwable) {
            // 资源表解析失败降级为空
        }
        return out.toList()
    }

    fun readZipEntry(apkPath: String, entryName: String): ByteArray? = try {
        ZipFile(apkPath).use { zf ->
            val e = zf.getEntry(entryName) ?: return null
            zf.getInputStream(e).use { input ->
                val bos = ByteArrayOutputStream(minOf(e.size.toInt().coerceAtLeast(1024), 1 shl 20))
                input.copyTo(bos)
                bos.toByteArray()
            }
        }
    } catch (t: Throwable) {
        null
    }

    fun listZipEntries(apkPath: String, limit: Int = 20000): List<String> = try {
        ZipFile(apkPath).use { zf ->
            val list = ArrayList<String>()
            val en = zf.entries()
            while (en.hasMoreElements() && list.size < limit) {
                list.add(en.nextElement().name)
            }
            list
        }
    } catch (t: Throwable) {
        emptyList()
    }
}
