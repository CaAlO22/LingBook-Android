package com.lingji.app.data.remote.strategy

import com.google.gson.JsonArray
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatRequest
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderRegistry
import okhttp3.Request

/**
 * 腾讯混元（TokenHub）请求策略。
 *
 * Hy4 系列只支持 `thinking.type` 开关；Hy3 额外支持 `reasoning_effort` 的 low/high 两档。
 */
class HunyuanRequestStrategy : RequestStrategy {

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
        thinking = mapOf(
            "type" to if (settings.thinkingLevel == ThinkingLevel.OFF) "disabled" else "enabled"
        ),
        reasoningEffort = settings.thinkingLevel.toEffort(),
        tools = tools
    )

    private fun ThinkingLevel.toEffort(): String? = when (this) {
        ThinkingLevel.OFF, ThinkingLevel.ON -> null
        ThinkingLevel.LOW -> "low"
        ThinkingLevel.MEDIUM, ThinkingLevel.HIGH, ThinkingLevel.MAX -> "high"
    }
}
