package com.johnny.wxdexinspector.core.apk

import android.content.Context
import android.content.pm.PackageManager
import com.johnny.wxdexinspector.core.log.WxLogger
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** 读取 APK 静态元信息（包名/版本/SDK/签名证书） */
object ApkInfoReader {

    private const val TAG = "ApkInfoReader"

    private const val FLAGS =
        PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            PackageManager.GET_PERMISSIONS or
            PackageManager.GET_INTENT_FILTERS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNING_CERTIFICATES

    fun read(context: Context, apkPath: String, source: String = "static"): ApkMeta {
        val file = File(apkPath)
        val meta = ApkMeta(
            filePath = apkPath,
            fileName = file.name,
            sizeBytes = file.length(),
            source = source
        )
        val pm = context.packageManager
        try {
            val pi = pm.getPackageArchiveInfo(apkPath, FLAGS)
            if (pi == null) {
                WxLogger.w(TAG, "无法解析 APK：$apkPath")
                return meta.copy(md5 = digest(file, "MD5"), sha1 = digest(file, "SHA1"), sha256 = digest(file, "SHA256"))
            }
            val ai = pi.applicationInfo
            var label = ""
            if (ai != null) {
                ai.sourceDir = apkPath
                ai.publicSourceDir = apkPath
                label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault("")
            }
            var signMd5 = ""; var signSha1 = ""; var signSha256 = ""
            var subject = ""; var issuer = ""
            val signer = runCatching {
                pi.signingInfo?.let { si ->
                    if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
                }?.firstOrNull()
            }.getOrNull()
            if (signer != null) {
                val bytes = signer.toByteArray()
                signMd5 = hex(MessageDigest.getInstance("MD5").digest(bytes))
                signSha1 = hex(MessageDigest.getInstance("SHA1").digest(bytes))
                signSha256 = hex(MessageDigest.getInstance("SHA256").digest(bytes))
                runCatching {
                    val cert = CertificateFactory.getInstance("X.509")
                        .generateCertificate(bytes.inputStream()) as X509Certificate
                    subject = cert.subjectX500Principal.name
                    issuer = cert.issuerX500Principal.name
                }
            }
            return meta.copy(
                packageName = pi.packageName ?: "",
                versionName = pi.versionName ?: "",
                versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else (pi.versionCode.toLong()),
                appLabel = label,
                minSdk = ai?.minSdkVersion ?: 0,
                targetSdk = ai?.targetSdkVersion ?: 0,
                md5 = digest(file, "MD5"),
                sha1 = digest(file, "SHA1"),
                sha256 = digest(file, "SHA256"),
                signMd5 = signMd5,
                signSha1 = signSha1,
                signSha256 = signSha256,
                signSubject = subject,
                signIssuer = issuer
            )
        } catch (t: Throwable) {
            WxLogger.e(TAG, "读取 APK 信息失败：$apkPath", t)
            return meta
        }
    }

    fun digest(file: File, algo: String): String = try {
        val md = MessageDigest.getInstance(algo)
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        hex(md.digest())
    } catch (t: Throwable) {
        ""
    }

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
