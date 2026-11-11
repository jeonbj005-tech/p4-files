package com.polestar4.drivefilemanager

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minimumSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val signingCertificateSha256: String,
    val notes: String
)

class AppUpdater(private val context: Context) {
    companion object {
        const val DEFAULT_MANIFEST_URL = "https://github.com/jeonbj005-tech/p4-files/releases/latest/download/update.json"
        private const val REPOSITORY = "jeonbj005-tech/p4-files"
        private const val APK_NAME = "P4-Files-v"
        private const val MAX_APK_BYTES = 100L * 1024 * 1024
        private val SHA256 = Regex("[0-9a-fA-F]{64}")
    }

    fun check(manifestUrl: String = DEFAULT_MANIFEST_URL): UpdateInfo {
        require(manifestUrl == DEFAULT_MANIFEST_URL) { "업데이트 주소가 허용된 GitHub manifest와 다릅니다." }
        val json = JSONObject(request(manifestUrl, 16 * 1024))
        return UpdateInfo(
            packageName = json.getString("packageName"),
            versionCode = json.getLong("versionCode"),
            versionName = json.getString("versionName"),
            minimumSdk = json.getInt("minimumSdk"),
            apkUrl = json.getString("apkUrl"),
            sha256 = json.getString("sha256").uppercase(),
            size = json.getLong("size"),
            signingCertificateSha256 = json.getString("signingCertificateSha256").uppercase(),
            notes = json.optString("notes")
        ).also(::validateRelease)
    }

    fun installedVersionCode(): Long = versionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    fun installedSignerSha256(): String = signerSha256(context.packageManager.getPackageInfo(context.packageName, signingFlags()))

    fun download(info: UpdateInfo, progress: (Long, Long) -> Unit): File {
        validateRelease(info)
        require(info.versionCode > installedVersionCode()) { "새 버전이 아니므로 설치를 시작하지 않습니다." }

        val directory = File(context.getExternalFilesDir(null) ?: throw IOException("업데이트 저장소를 사용할 수 없습니다."), "Updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("업데이트 저장 폴더를 만들지 못했습니다.")
        val target = File(directory, "$APK_NAME${info.versionName}.apk")
        val temp = File(directory, "${target.name}.part")
        if (target.isFile && runCatching { verifyApk(target, info) }.isSuccess) return target
        if (target.exists() && !target.delete()) throw IOException("검증되지 않은 이전 APK를 교체하지 못했습니다.")
        if (temp.length() > info.size && !temp.delete()) throw IOException("손상된 다운로드 조각을 정리하지 못했습니다.")
        if (temp.isFile && temp.length() == info.size) {
            try {
                verifyApk(temp, info)
                if (!temp.renameTo(target)) throw IOException("검증된 APK를 저장하지 못했습니다.")
                return target
            } catch (e: Exception) {
                temp.delete()
                throw e
            }
        }

        var offset = temp.length()
        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val code = connection.responseCode
            requireTrustedHttps(connection.url)
            if (code !in 200..299) throw IOException("APK 다운로드 HTTP $code")
            if (code == HttpURLConnection.HTTP_OK) offset = 0L
            else if (code == HttpURLConnection.HTTP_PARTIAL) {
                val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(connection.getHeaderField("Content-Range").orEmpty())
                val rangeStart = range?.groupValues?.get(1)?.toLongOrNull()
                val rangeTotal = range?.groupValues?.get(3)?.toLongOrNull()
                if (rangeStart != offset || rangeTotal != info.size) {
                    temp.delete()
                    throw IOException("APK 다운로드 재개 범위가 올바르지 않습니다.")
                }
            } else throw IOException("APK 다운로드 HTTP $code")

            val advertised = connection.contentLengthLong
            if (advertised >= 0 && advertised != info.size - offset) throw IOException("APK 다운로드 크기 정보가 다릅니다.")
            connection.inputStream.use { input ->
                FileOutputStream(temp, offset > 0).use { output ->
                    var total = offset
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > info.size) {
                            temp.delete()
                            throw IOException("APK 크기가 업데이트 정보보다 큽니다.")
                        }
                        output.write(buffer, 0, count)
                        progress(total, info.size)
                    }
                    output.fd.sync()
                    if (total != info.size) throw IOException("APK 다운로드가 완료되지 않았습니다.")
                }
            }
        } finally {
            connection.disconnect()
        }

        try {
            verifyApk(temp, info)
            if (!temp.renameTo(target)) throw IOException("검증된 APK를 저장하지 못했습니다.")
            return target
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun validateRelease(info: UpdateInfo) {
        require(info.packageName == context.packageName) { "업데이트 패키지가 P4 Files와 다릅니다." }
        require(info.versionCode > 0 && info.versionName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))) { "업데이트 버전 정보가 올바르지 않습니다." }
        require(info.minimumSdk in 1..Build.VERSION.SDK_INT) { "이 업데이트는 현재 Android 버전과 호환되지 않습니다." }
        require(info.apkUrl == "https://github.com/$REPOSITORY/releases/download/v${info.versionName}/$APK_NAME${info.versionName}.apk") { "APK 주소가 이 저장소의 버전별 Release와 일치하지 않습니다." }
        require(SHA256.matches(info.sha256) && SHA256.matches(info.signingCertificateSha256)) { "업데이트 해시 형식이 올바르지 않습니다." }
        require(info.size in 1..MAX_APK_BYTES) { "APK 크기가 허용 범위를 벗어났습니다." }
        require(info.signingCertificateSha256.equals(installedSignerSha256(), true)) { "업데이트 서명이 현재 설치본과 다릅니다." }
    }

    private fun verifyApk(file: File, info: UpdateInfo) {
        if (!file.isFile || file.length() != info.size) throw IOException("APK 파일 크기가 업데이트 정보와 다릅니다.")
        if (!sha256(file).equals(info.sha256, true)) throw IOException("APK SHA-256이 일치하지 않습니다.")
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
            ?: throw IOException("APK 정보를 읽을 수 없습니다.")
        if (archive.packageName != info.packageName || versionCode(archive) != info.versionCode || archive.versionName != info.versionName) {
            throw IOException("APK 패키지 또는 버전이 업데이트 정보와 다릅니다.")
        }
        if (archive.applicationInfo?.minSdkVersion != info.minimumSdk) throw IOException("APK 최소 Android 버전이 업데이트 정보와 다릅니다.")
        if (!signerSha256(archive).equals(info.signingCertificateSha256, true)) throw IOException("APK 서명이 업데이트 정보와 다릅니다.")
        if (!signerSha256(context.packageManager.getPackageInfo(context.packageName, signingFlags())).equals(info.signingCertificateSha256, true)) {
            throw IOException("APK 서명이 현재 설치본과 다릅니다.")
        }
        if (info.versionCode <= installedVersionCode()) throw IOException("설치된 버전보다 새 버전이 아닙니다.")
    }

    private fun request(url: String, maxBytes: Int): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            requireTrustedHttps(connection.url)
            if (code !in 200..299) throw IOException("업데이트 확인 HTTP $code")
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maxBytes) throw IOException("업데이트 정보가 허용 크기를 넘습니다.")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun requireTrustedHttps(url: URL) {
        val host = url.host.lowercase()
        require(url.protocol == "https" && (host == "github.com" || host.endsWith(".githubusercontent.com"))) {
            "허용되지 않은 업데이트 서버입니다."
        }
    }

    private fun signingFlags() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signerSha256(info: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        require(signatures?.size == 1) { "단일 서명 APK만 업데이트할 수 있습니다." }
        return MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }
}
