package com.johnny.wxdexinspector.core.apk

import android.graphics.drawable.Drawable

/** APK 基础元信息 */
data class ApkMeta(
    val filePath: String = "",
    val fileName: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val appLabel: String = "",
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val sizeBytes: Long = 0L,
    val md5: String = "",
    val sha1: String = "",
    val sha256: String = "",
    val signMd5: String = "",
    val signSha1: String = "",
    val signSha256: String = "",
    val signSubject: String = "",
    val signIssuer: String = "",
    val isSplit: Boolean = false,
    val source: String = "static"
)

/** 已安装应用条目 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val apkPath: String?,
    val uid: Int,
    val icon: Drawable? = null
) {
    override fun equals(other: Any?): Boolean =
        other is InstalledApp && other.packageName == packageName

    override fun hashCode(): Int = packageName.hashCode()
}

/** 提取结果 */
sealed class ExtractResult {
    data class Success(val files: List<String>, val packageName: String) : ExtractResult()
    data class Failure(val reason: String) : ExtractResult()
}
