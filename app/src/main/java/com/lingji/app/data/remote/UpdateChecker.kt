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

enum class UpdateSource(val displayName: String) { GITHUB("GitHub"), GITEE("Gitee") }

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

    /**
     * 不跟随重定向的 client：用于 GitHub 回退路径从
     * ``github.com/.../releases/latest`` 的 302 Location 头解析最新 tag，
     * 绕开 ``api.github.com`` 在某些 VPN 出口 IP 被 403/429 拦截的情况。
     */
    private val noRedirectClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val gson = Gson()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS_NAME = "update_prefs"
        const val KEY_SOURCE = "update_source"

        // GitHub
        const val GITHUB_API = "https://api.github.com/repos/CaAlO22/LingBook-Android/releases/latest"
        const val GITHUB_LATEST_HTML = "https://github.com/CaAlO22/LingBook-Android/releases/latest"
        const val GITHUB_REPO_RELEASES = "https://github.com/CaAlO22/LingBook-Android/releases"

        // Gitee
        // 使用 /releases 列表而非 /releases/latest：后者在 Gitee 上可能返回非最新 Release，
        // 遍历列表按版本号取最大值才能可靠拿到最新版本。
        const val GITEE_API_RELEASES =
            "https://gitee.com/api/v5/repos/caalo22/ling-book-android/releases"
        const val GITEE_API_TAGS =
            "https://gitee.com/api/v5/repos/caalo22/ling-book-android/tags"
        const val GITEE_RELEASES_PAGE =
            "https://gitee.com/caalo22/ling-book-android/releases"
    }

    fun getUpdateSource(): UpdateSource {
        val name = prefs.getString(KEY_SOURCE, UpdateSource.GITEE.name) ?: UpdateSource.GITEE.name
        return runCatching { UpdateSource.valueOf(name) }.getOrDefault(UpdateSource.GITEE)
    }

    fun setUpdateSource(source: UpdateSource) {
        prefs.edit().putString(KEY_SOURCE, source.name).apply()
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        when (getUpdateSource()) {
            UpdateSource.GITHUB -> checkGithub()
            UpdateSource.GITEE -> checkGitee()
        }
    }

    // ── GitHub ─────────────────────────────────────────────────────────────

    private suspend fun checkGithub(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "LingBook-Android")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 403/429/451 通常是 GitHub API 对该出口 IP 限速或封禁；
                    // 退化到不需要认证的 github.com 重定向抓取，仍可拿到最新 tag。
                    if (response.code == 403 || response.code == 429 || response.code == 451) {
                        return@withContext githubFallbackViaHtmlRedirect(response.code)
                    }
                    return@withContext UpdateCheckResult.Failed(
                        "HTTP ${response.code} ${response.message.ifBlank { "" }}".trim()
                    )
                }
                val json = response.body?.string()
                    ?: return@withContext UpdateCheckResult.Failed("响应内容为空")
                val release = try {
                    gson.fromJson(json, GithubRelease::class.java)
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

    /**
     * 当 ``api.github.com`` 返回 403/429/451 时的回退路径：
     * 访问 ``https://github.com/<repo>/releases/latest``，GitHub 会用 302 重定向到
     * ``/releases/tag/v<x.y.z>``。从 Location 头里解析出 tag，再构造下载链接。
     */
    private fun githubFallbackViaHtmlRedirect(originalCode: Int): UpdateCheckResult {
        return try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_HTML)
                .header("User-Agent", "LingBook-Android")
                .header("Accept", "text/html")
                .get()
                .build()
            noRedirectClient.newCall(request).execute().use { response ->
                val location = response.header("Location")
                    ?: response.header("location")
                if (response.code !in 300..399 || location.isNullOrBlank()) {
                    return UpdateCheckResult.Failed(
                        "HTTP $originalCode（GitHub API 限流），回退也失败：HTTP ${response.code}"
                    )
                }
                val tagMatch = Regex("/releases/tag/(v?[^/?#]+)").find(location)
                val rawTag = tagMatch?.groupValues?.getOrNull(1)
                    ?: return UpdateCheckResult.Failed(
                        "HTTP $originalCode（GitHub API 限流），回退响应无法解析 tag：$location"
                    )
                val tagName = rawTag.removePrefix("v")
                val currentVersion = getCurrentVersionName()
                val hasUpdate = isNewerVersion(tagName, currentVersion)
                UpdateCheckResult.Success(
                    UpdateInfo(
                        versionName = tagName,
                        downloadUrl = "$GITHUB_REPO_RELEASES/tag/$rawTag",
                        releaseNotes = "（GitHub API 暂不可用，已通过回退路径获取版本信息。详细更新说明请到发布页查看。）",
                        hasUpdate = hasUpdate
                    )
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult.Failed(
                "HTTP $originalCode（GitHub API 限流），回退也失败：${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    // ── Gitee ──────────────────────────────────────────────────────────────

    private suspend fun checkGitee(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$GITEE_API_RELEASES?page=1&per_page=20")
                .header("Accept", "application/json")
                .header("User-Agent", "LingBook-Android")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 404 表示 Gitee 上还没有创建 Release，退化到 Tags API 获取最新标签
                    if (response.code == 404) {
                        return@withContext giteeFallbackViaTags()
                    }
                    return@withContext UpdateCheckResult.Failed(
                        "HTTP ${response.code} ${response.message.ifBlank { "" }}".trim()
                    )
                }
                val json = response.body?.string()
                    ?: return@withContext UpdateCheckResult.Failed("响应内容为空")
                val releases = try {
                    gson.fromJson(json, Array<GiteeRelease>::class.java).orEmpty()
                } catch (e: Exception) {
                    return@withContext UpdateCheckResult.Failed(
                        "解析响应失败：${e.message ?: e.javaClass.simpleName}"
                    )
                }
                if (releases.isEmpty()) {
                    return@withContext giteeFallbackViaTags()
                }

                // Gitee /releases/latest 可能返回非最新 Release，
                // 遍历全部 Release 按版本号取最大值。
                val release = releases.maxWithOrNull { a, b ->
                    val aVer = a.tagName?.removePrefix("v") ?: "0.0.0"
                    val bVer = b.tagName?.removePrefix("v") ?: "0.0.0"
                    when {
                        isNewerVersion(aVer, bVer) -> 1
                        isNewerVersion(bVer, aVer) -> -1
                        else -> 0
                    }
                } ?: return@withContext giteeFallbackViaTags()

                val tagName = release.tagName?.removePrefix("v")
                    ?: return@withContext UpdateCheckResult.Failed("Release 缺少 tag_name 字段")

                val currentVersion = getCurrentVersionName()
                val hasUpdate = isNewerVersion(tagName, currentVersion)

                val apkUrl = release.assets?.firstOrNull { asset ->
                    asset.name?.endsWith(".apk", ignoreCase = true) == true
                }?.browserDownloadUrl ?: GITEE_RELEASES_PAGE

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
            UpdateCheckResult.Failed("DNS 解析失败：${e.message ?: "gitee.com"}")
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

    /**
     * 当 Gitee Releases API 返回 404（尚未创建 Release）时的回退路径：
     * 通过 Tags API 获取最新标签名，推导版本号并指向 Gitee 发布页。
     */
    private fun giteeFallbackViaTags(): UpdateCheckResult {
        return try {
            val request = Request.Builder()
                .url(GITEE_API_TAGS)
                .header("User-Agent", "LingBook-Android")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return UpdateCheckResult.Failed(
                        "Gitee 上暂无 Release，Tags API 也失败：HTTP ${response.code}"
                    )
                }
                val json = response.body?.string()
                    ?: return UpdateCheckResult.Failed("Tags 响应内容为空")
                val tags = try {
                    gson.fromJson(json, Array<GiteeTag>::class.java).orEmpty()
                } catch (e: Exception) {
                    return UpdateCheckResult.Failed(
                        "解析 Tags 响应失败：${e.message ?: e.javaClass.simpleName}"
                    )
                }
                val latestTag = tags.firstOrNull()?.name
                    ?: return UpdateCheckResult.Failed("Gitee 上暂无标签")
                val tagName = latestTag.removePrefix("v")
                val currentVersion = getCurrentVersionName()
                val hasUpdate = isNewerVersion(tagName, currentVersion)
                UpdateCheckResult.Success(
                    UpdateInfo(
                        versionName = tagName,
                        downloadUrl = GITEE_RELEASES_PAGE,
                        releaseNotes = "（Gitee 上暂无 Release 详情，请到发布页查看。）",
                        hasUpdate = hasUpdate
                    )
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult.Failed(
                "Gitee 上暂无 Release，Tags 回退也失败：${e.message ?: e.javaClass.simpleName}"
            )
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
    private data class GithubRelease(
        @SerializedName("tag_name") val tagName: String?,
        @SerializedName("body") val body: String?,
        @SerializedName("html_url") val htmlUrl: String?,
        @SerializedName("assets") val assets: List<GithubAsset>?
    )

    private data class GithubAsset(
        @SerializedName("name") val name: String?,
        @SerializedName("browser_download_url") val browserDownloadUrl: String?
    )

    // Gitee API response models
    private data class GiteeRelease(
        @SerializedName("tag_name") val tagName: String?,
        @SerializedName("body") val body: String?,
        @SerializedName("assets") val assets: List<GiteeAsset>?
    )

    private data class GiteeAsset(
        @SerializedName("name") val name: String?,
        @SerializedName("browser_download_url") val browserDownloadUrl: String?
    )

    private data class GiteeTag(
        @SerializedName("name") val name: String?
    )
}
