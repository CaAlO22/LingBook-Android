package com.lingji.app.data.remote.strategy

import com.google.gson.JsonArray
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatRequest
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderRegistry
import okhttp3.Request

class ZhipuRequestStrategy : RequestStrategy {

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
        // GLM-5.3 起始终开启思考，不接受 thinking.type="disabled"；
        // 为用户提供的强度档位是 low/high/max。
        val alwaysThinking = ALWAYS_THINKING_MODELS.any { model.startsWith(it) }
        val level = settings.thinkingLevel
        val thinkingEnabled = alwaysThinking || level != ThinkingLevel.OFF

        return ChatRequest(
            model = model,
            messages = messages,
            temperature = temperature ?: 0.7,
            stream = stream,
            thinking = mapOf("type" to if (thinkingEnabled) "enabled" else "disabled"),
            reasoningEffort = level.toEffort(alwaysThinking),
            tools = tools
        )
    }

    private fun ThinkingLevel.toEffort(alwaysThinking: Boolean): String? = when (this) {
        ThinkingLevel.OFF -> if (alwaysThinking) "low" else null
        ThinkingLevel.ON -> null
        ThinkingLevel.LOW -> "low"
        // GLM-5.3 没有 medium 档，回落到 high
        ThinkingLevel.MEDIUM -> if (alwaysThinking) "high" else "medium"
        ThinkingLevel.HIGH -> "high"
        ThinkingLevel.MAX -> "max"
    }

    companion object {
        /** 始终开启思考、不允许关闭的模型前缀。 */
        private val ALWAYS_THINKING_MODELS = listOf("glm-5.3", "glm-5v")
    }
}
