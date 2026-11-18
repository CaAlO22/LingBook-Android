package com.lingji.app.data.remote.strategy

import com.google.gson.JsonArray
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatRequest
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderRegistry
import okhttp3.Request

/**
 * OpenAI 请求策略。
 *
 * GPT-6 系列统一用顶层 `reasoning_effort` 表达思考强度，支持 `none` 关闭思考；
 * 强度档位见 [OpenAIConfig.defaultThinkingLevels]。
 */
class OpenAIRequestStrategy : RequestStrategy {

    override fun applyHeaders(builder: Request.Builder, apiKey: String) {
        builder.header("Authorization", "Bearer $apiKey")
    }

    override fun buildChatRequestBody(
        settings: AISettings,
        messages: List<ChatMessage>,
        stream: Boolean,
        tools: JsonArray?,
        temperature: Double?
    ): ChatRequest = ChatRequest(
        model = settings.modelName.ifBlank {
            ProviderRegistry.config(settings.provider).defaultModelId
        },
        messages = messages,
        temperature = temperature ?: 0.7,
        stream = stream,
        reasoningEffort = settings.thinkingLevel.toEffort(),
        tools = tools
    )

    private fun ThinkingLevel.toEffort(): String = when (this) {
        ThinkingLevel.OFF -> "none"
        ThinkingLevel.LOW -> "low"
        ThinkingLevel.ON, ThinkingLevel.MEDIUM -> "medium"
        ThinkingLevel.HIGH -> "high"
        ThinkingLevel.MAX -> "xhigh"
    }
}
