package com.lingji.app.data.remote.sync

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lingji.app.data.sync.SyncProtocol
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 云同步服务器 HTTP 客户端（手写 OkHttp + Gson，与 LLMService 风格一致）。
 * 所有方法均为阻塞式，调用方需在 IO 线程使用；网络/协议错误一律抛异常，由上层兜底。
 */
@Singleton
class SyncApi @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    /**
     * 局域网扫描专用客户端：局域网内不存在的主机要等 connectTimeout，
     * 因此超时必须短；同时放开并发上限，让整段 /24 能在 1~2 秒内扫完。
     */
    private val scanClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .callTimeout(1500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .dispatcher(Dispatcher().apply {
            maxRequests = SCAN_MAX_REQUESTS
            maxRequestsPerHost = SCAN_MAX_REQUESTS
        })
        .build()

    private fun buildRequest(url: String, token: String): Request.Builder {
        val builder = Request.Builder().url(url)
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    /** 健康检查，返回服务器时间（毫秒）。 */
    fun ping(baseUrl: String, token: String): Long = serverInfo(baseUrl, token).serverTime

    /**
     * 读取服务器信息：时间 + 同步协议版本。
     *
     * 同步前必须先比对 [SyncServerInfo.schemaVersion]，服务端版本低于本端时拒绝同步，
     * 避免旧服务器静默丢弃它不认识的新表 / 新字段。
     */
    fun serverInfo(baseUrl: String, token: String): SyncServerInfo {
        val request = buildRequest("$baseUrl/api/ping", token).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SyncApiException(response.code, "HTTP ${response.code}")
            }
            val json = JsonParser.parseString(body).asJsonObject
            val serverTime = json.get("serverTime")?.asLong ?: 0L
            // 旧服务器没有 schemaVersion 字段 -> 按 1（加版本号之前的协议）处理
            val rawVersion = json.get("schemaVersion")
                ?.takeIf { it.isJsonPrimitive }
                ?.let { runCatching { it.asInt }.getOrNull() }
            return SyncServerInfo(
                serverTime = serverTime,
                schemaVersion = SyncProtocol.normalizeServerVersion(rawVersion)
            )
        }
    }

    /**
     * 局域网扫描用的探测：短超时、不重试。
     *
     * 只认 `serverTime` 字段，避免把局域网里其他恰好开着 HTTP 服务的设备当成灵记服务器。
     * 服务器开了访问令牌时 ping 也需要授权，故沿用同一个 token。
     *
     * @return 服务器时间（毫秒）；不是灵记服务器时返回 null。
     */
    fun pingFast(baseUrl: String, token: String): Long? {
        val request = buildRequest("$baseUrl/api/ping", token).get().build()
        return runCatching {
            scanClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                JsonParser.parseString(body).asJsonObject.get("serverTime")?.asLong
            }
        }.getOrNull()
    }

    /** 执行一轮增量同步，返回服务端响应 JSON（含 rev/changes/tombstones）。 */    fun sync(baseUrl: String, token: String, payload: JsonObject): JsonObject {
        val request = buildRequest("$baseUrl/api/sync", token)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errorJson = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
                val detail = errorJson?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                // 409 = 服务端判定本端协议版本更高（server_outdated），带上服务端版本号供上层展示
                val serverVersion = errorJson?.get("serverSchemaVersion")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.let { runCatching { it.asInt }.getOrNull() }
                throw SyncApiException(response.code, detail ?: "HTTP ${response.code}", serverVersion)
            }
            return JsonParser.parseString(body).asJsonObject
        }
    }

    /**
     * 拉取云端模型目录。
     *
     * @param knownVersion 本机已缓存的目录版本；与服务端一致时服务端只回 `unchanged`，
     *   一轮轮询的流量只有几十字节。
     */
    fun fetchModelCatalog(
        baseUrl: String,
        token: String,
        knownVersion: Int?
    ): ModelCatalogResponse {
        val url = if (knownVersion != null && knownVersion > 0) {
            "$baseUrl/api/model_catalog?version=$knownVersion"
        } else {
            "$baseUrl/api/model_catalog"
        }
        val request = buildRequest(url, token).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SyncApiException(response.code, "HTTP ${response.code}")
            }
            return gson.fromJson(body, ModelCatalogResponse::class.java) ?: ModelCatalogResponse()
        }
    }

    companion object {
        /** 局域网扫描的并发上限：一整段 /24 一次性发出去，1~2 秒即可扫完。 */
        private const val SCAN_MAX_REQUESTS = 128
    }
}

/** 服务器信息：`/api/ping` 的响应。 */
data class SyncServerInfo(
    val serverTime: Long,
    /** 服务端同步协议版本；旧服务器没有该字段时按 [SyncProtocol.LEGACY_SCHEMA_VERSION] 处理。 */
    val schemaVersion: Int = SyncProtocol.LEGACY_SCHEMA_VERSION
)

class SyncApiException(
    val statusCode: Int,
    message: String,
    /** 服务端在 409 响应里回带的协议版本（仅 `server_outdated` 时有值）。 */
    val serverSchemaVersion: Int? = null
) : Exception(message)
