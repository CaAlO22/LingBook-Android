package com.lingji.app.data.remote.strategy

import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatRequest
import com.lingji.app.domain.model.AISettings
import okhttp3.Request

interface RequestStrategy {
    /** 在请求 Builder 上追加该供应商所需的认证头。 */
    fun applyHeaders(builder: Request.Builder, apiKey: String)

    /** 构造符合该供应商要求的 ChatRequest 请求体。 */
    fun buildChatRequestBody(
        settings: AISettings,
        messages: List<ChatMessage>,
        stream: Boolean
    ): ChatRequest
}
