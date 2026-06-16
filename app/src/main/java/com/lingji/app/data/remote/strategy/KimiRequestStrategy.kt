package com.lingji.app.data.remote.strategy

import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatRequest
import com.lingji.app.domain.model.AISettings
import okhttp3.Request

class KimiRequestStrategy : RequestStrategy {

    override fun applyHeaders(builder: Request.Builder, apiKey: String) {
        builder.header("Authorization", "Bearer $apiKey")
    }

    override fun buildChatRequestBody(
        settings: AISettings,
        messages: List<ChatMessage>,
        stream: Boolean
    ): ChatRequest = ChatRequest(
        model = settings.modelName.ifBlank { "kimi-k2.6" },
        messages = messages,
        temperature = 0.7,
        stream = stream,
        thinking = mapOf(
            "type" to if (settings.enableThinking) "enabled" else "disabled"
        )
    )
}
