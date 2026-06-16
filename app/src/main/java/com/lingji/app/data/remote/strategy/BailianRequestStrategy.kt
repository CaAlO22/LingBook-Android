package com.lingji.app.data.remote.strategy

import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatRequest
import com.lingji.app.domain.model.AISettings
import okhttp3.Request

/**
 * 阿里云百炼（DashScope）OpenAI 兼容接口策略。
 *
 * 百炼使用标准 Bearer Token 鉴权，并通过 `enable_thinking` 参数控制思考模式。
 */
class BailianRequestStrategy : RequestStrategy {

    override fun applyHeaders(builder: Request.Builder, apiKey: String) {
        builder.header("Authorization", "Bearer $apiKey")
    }

    override fun buildChatRequestBody(
        settings: AISettings,
        messages: List<ChatMessage>,
        stream: Boolean
    ): ChatRequest = ChatRequest(
        model = settings.modelName.ifBlank { BailianDefaultModel },
        messages = messages,
        temperature = 0.7,
        stream = stream,
        enableThinking = settings.enableThinking
    )

    companion object {
        private const val BailianDefaultModel = "qwen3.7-plus"
    }
}
