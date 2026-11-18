package com.lingji.app.data.remote.strategy

import com.google.gson.JsonArray
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatRequest
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderRegistry
import okhttp3.Request

class DeepSeekRequestStrategy : RequestStrategy {

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
        ThinkingLevel.OFF -> null
        ThinkingLevel.LOW -> "low"
        ThinkingLevel.ON, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH -> "high"
        ThinkingLevel.MAX -> "max"
    }
}
