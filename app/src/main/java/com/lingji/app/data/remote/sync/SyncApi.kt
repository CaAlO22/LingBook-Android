package com.lingji.app.data.remote.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
    fun ping(baseUrl: String, token: String): Long {
        val request = buildRequest("$baseUrl/api/ping", token).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SyncApiException(response.code, "HTTP ${response.code}")
            }
            val json = JsonParser.parseString(body).asJsonObject
            return json.get("serverTime")?.asLong ?: 0L
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
                val detail = runCatching {
                    JsonParser.parseString(body).asJsonObject.get("error")?.asString
                }.getOrNull()
                throw SyncApiException(response.code, detail ?: "HTTP ${response.code}")
            }
            return JsonParser.parseString(body).asJsonObject
        }
    }

    companion object {
        /** 局域网扫描的并发上限：一整段 /24 一次性发出去，1~2 秒即可扫完。 */
        private const val SCAN_MAX_REQUESTS = 128
    }
}

class SyncApiException(val statusCode: Int, message: String) : Exception(message)
