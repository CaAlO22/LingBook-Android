package com.lingji.app.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val hasUpdate: Boolean
)

sealed class UpdateCheckResult {
    data class Success(val info: UpdateInfo) : UpdateCheckResult()
    data class Failed(val reason: String) : UpdateCheckResult()
}

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * 在 VPN/代理环境下，系统 DNS 经常同时返回 IPv4 与 IPv6 地址，
     * 但部分 VPN 只承载 IPv4，连接 IPv6 时会 connect timeout。
     * 优先排序 IPv4 地址，可显著降低这种"看似无响应"的失败。
     */
    private val ipv4PreferredDns = object : Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val (v4, others) = all.partition { it is Inet4Address }
            return if (v4.isEmpty()) all else v4 + others
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .dns(ipv4PreferredDns)
        .build()

    private val gson = Gson()

    private companion object {
        const val GITHUB_API = "https://api.github.com/repos/CaAlO22/LingBook-Android/releases/latest"
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "LingBook-Android")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Failed(
                        "HTTP ${response.code} ${response.message.ifBlank { "" }}".trim()
                    )
                }
                val json = response.body?.string()
                    ?: return@withContext UpdateCheckResult.Failed("响应内容为空")
                val release = try {
                    gson.fromJson(json, GitHubRelease::class.java)
                } catch (e: Exception) {
                    return@withContext UpdateCheckResult.Failed(
                        "解析响应失败：${e.message ?: e.javaClass.simpleName}"
                    )
                } ?: return@withContext UpdateCheckResult.Failed("响应格式异常")

                val tagName = release.tagName?.removePrefix("v")
                    ?: return@withContext UpdateCheckResult.Failed("Release 缺少 tag_name 字段")

                val currentVersion = getCurrentVersionName()
                val hasUpdate = isNewerVersion(tagName, currentVersion)

                val apkUrl = release.assets?.firstOrNull { asset ->
                    asset.name?.endsWith(".apk", ignoreCase = true) == true
                }?.browserDownloadUrl ?: release.htmlUrl
                    ?: return@withContext UpdateCheckResult.Failed("Release 缺少下载链接")

                UpdateCheckResult.Success(
                    UpdateInfo(
                        versionName = tagName,
                        downloadUrl = apkUrl,
                        releaseNotes = release.body ?: "",
                        hasUpdate = hasUpdate
                    )
                )
            }
        } catch (e: UnknownHostException) {
            UpdateCheckResult.Failed("DNS 解析失败：${e.message ?: "api.github.com"}")
        } catch (e: SocketTimeoutException) {
            UpdateCheckResult.Failed("连接超时：${e.message ?: "网络无响应"}")
        } catch (e: SSLException) {
            UpdateCheckResult.Failed("TLS 握手失败：${e.message ?: e.javaClass.simpleName}")
        } catch (e: java.io.IOException) {
            UpdateCheckResult.Failed("网络错误：${e.message ?: e.javaClass.simpleName}")
        } catch (e: Exception) {
            UpdateCheckResult.Failed("${e.javaClass.simpleName}: ${e.message ?: ""}".trim().trimEnd(':'))
        }
    }

    suspend fun downloadApk(url: String, onProgress: (Int) -> Unit): java.io.File? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LingBook-Android")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body ?: return@runCatching null
                val totalBytes = body.contentLength()
                val apkFile = java.io.File(context.cacheDir, "lingji-update.apk")
                if (apkFile.exists()) apkFile.delete()

                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var downloadedBytes = 0L
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            }
                        }
                    }
                }
                apkFile
            }
        }.getOrNull()
    }

    private fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    // GitHub API response models
    private data class GitHubRelease(
        @SerializedName("tag_name") val tagName: String?,
        @SerializedName("body") val body: String?,
        @SerializedName("html_url") val htmlUrl: String?,
        @SerializedName("assets") val assets: List<GitHubAsset>?
    )

    private data class GitHubAsset(
        @SerializedName("name") val name: String?,
        @SerializedName("browser_download_url") val browserDownloadUrl: String?
    )
}
