package com.lingji.app.data.remote.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

    /** 执行一轮增量同步，返回服务端响应 JSON（含 rev/changes/tombstones）。 */
    fun sync(baseUrl: String, token: String, payload: JsonObject): JsonObject {
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
}

class SyncApiException(val statusCode: Int, message: String) : Exception(message)
