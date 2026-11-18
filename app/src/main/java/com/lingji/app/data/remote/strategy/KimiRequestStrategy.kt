package com.lingji.app.data.remote.strategy

import com.google.gson.JsonArray
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatRequest
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderRegistry
import okhttp3.Request

/**
 * 月之暗面 Kimi（Moonshot）请求策略。
 *
 * 思考模式的差异：
 * - `kimi-k3` 始终推理，不接受 `thinking` 对象，只能用顶层 `reasoning_effort`（low/high/max）控制强度；
 * - `kimi-k2.7-code*` 思考固定为 enabled，不可关闭；
 * - 更早的 `kimi-k2.x` 支持 `thinking.type` 在 enabled / disabled 之间切换。
 */
class KimiRequestStrategy : RequestStrategy {

    override fun applyHeaders(builder: Request.Builder, apiKey: String) {
        builder.header("Authorization", "Bearer $apiKey")
    }

    override fun buildChatRequestBody(
        settings: AISettings,
        messages: List<ChatMessage>,
        stream: Boolean,
        tools: JsonArray?,
        temperature: Double?
    ): ChatRequest {
        val model = settings.modelName.ifBlank {
            ProviderRegistry.config(settings.provider).defaultModelId
        }
        val temp = temperature ?: 0.7

        return when {
            model.startsWith(K3_PREFIX) -> ChatRequest(
                model = model,
                messages = messages,
                temperature = temp,
                stream = stream,
                reasoningEffort = settings.thinkingLevel.toK3Effort(),
                tools = tools
            )

            model.startsWith(K27_PREFIX) -> ChatRequest(
                model = model,
                messages = messages,
                temperature = temp,
                stream = stream,
                thinking = mapOf("type" to "enabled"),
                tools = tools
            )

            else -> ChatRequest(
                model = model,
                messages = messages,
                temperature = temp,
                stream = stream,
                thinking = mapOf(
                    "type" to if (settings.thinkingLevel == ThinkingLevel.OFF) "disabled" else "enabled"
                ),
                tools = tools
            )
        }
    }

    private fun ThinkingLevel.toK3Effort(): String = when (this) {
        ThinkingLevel.OFF, ThinkingLevel.LOW -> "low"
        ThinkingLevel.ON, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH -> "high"
        ThinkingLevel.MAX -> "max"
    }

    companion object {
        private const val K3_PREFIX = "kimi-k3"
        private const val K27_PREFIX = "kimi-k2.7"
    }
}
