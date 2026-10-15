package com.johnny.wxdexinspector.core.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.johnny.wxdexinspector.core.dex.ScanStats
import java.io.File
import java.io.FileOutputStream

/** 可视化图片报告：统计条形图 + 类包分布图 */
object VisualReport {

    fun generateStatsChart(file: File, stats: ScanStats, hookCount: Int, pkg: String): Boolean = try {
        val w = 900
        val h = 620
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1C1E"); textSize = 40f; typeface = Typeface.DEFAULT_BOLD
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#333333"); textSize = 28f }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1C1E"); textSize = 28f; typeface = Typeface.DEFAULT_BOLD }
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3B6EF6") }

        c.drawText("WxDexInspector 扫描统计", 40f, 60f, title)
        c.drawText(pkg.ifBlank { "-" }, 40f, 100f, Paint(label).apply { textSize = 24f })

        val items = listOf(
            "类数量" to stats.classCount.toLong(),
            "方法数量" to stats.methodCount,
            "字段数量" to stats.fieldCount,
            "字符串数量" to stats.stringCount.toLong(),
            "Hook候选" to hookCount.toLong(),
            "DEX数量" to stats.dexCount.toLong()
        )
        val max = (items.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
        var y = 160f
        val labelW = 190f
        val barMaxW = w - labelW - 220f
        items.forEach { (name, v) ->
            c.drawText(name, 40f, y + 24f, label)
            val bw = (v.toDouble() / max * barMaxW).toFloat().coerceAtLeast(4f)
            c.drawRoundRect(labelW, y, labelW + bw, y + 34f, 10f, 10f, bar)
            c.drawText(v.toString(), labelW + bw + 16f, y + 26f, valuePaint)
            y += 62f
        }
        c.drawText("生成时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}", 40f, h - 40f,
            Paint(label).apply { textSize = 22f; color = Color.GRAY })

        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        true
    } catch (t: Throwable) {
        false
    }

    fun generatePackageChart(file: File, classNames: List<String>): Boolean = try {
        val w = 900
        val h = 640
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1C1E"); textSize = 40f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("类包分布 Top15", 40f, 60f, title)

        val counts = HashMap<String, Int>()
        classNames.forEach { cn ->
            val pkg = when {
                cn.contains('.') -> cn.substringBeforeLast('.')
                else -> "(default)"
            }
            counts[pkg] = (counts[pkg] ?: 0) + 1
        }
        val top = counts.entries.sortedByDescending { it.value }.take(15)
        val max = (top.maxOfOrNull { it.value } ?: 1).coerceAtLeast(1)

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#333333"); textSize = 24f }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A1C1E"); textSize = 24f; typeface = Typeface.DEFAULT_BOLD }
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#12B5A6") }

        var y = 110f
        val labelW = 430f
        val barMaxW = w - labelW - 160f
        top.forEach { (name, cnt) ->
            val shown = if (name.length > 34) name.take(32) + "…" else name
            c.drawText(shown, 40f, y + 22f, label)
            val bw = (cnt.toDouble() / max * barMaxW).toFloat().coerceAtLeast(4f)
            c.drawRoundRect(labelW, y, labelW + bw, y + 30f, 8f, 8f, bar)
            c.drawText(cnt.toString(), labelW + bw + 12f, y + 24f, valuePaint)
            y += 35f
        }
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        true
    } catch (t: Throwable) {
        false
    }
}
