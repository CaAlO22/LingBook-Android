package com.lingji.app.data.remote.strategy

import com.google.gson.JsonArray
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatRequest
import com.lingji.app.domain.model.AISettings
import okhttp3.Request

class ZhipuRequestStrategy : RequestStrategy {

    override fun applyHeaders(builder: Request.Builder, apiKey: String) {
        builder.header("Authorization", "Bearer $apiKey")
    }

    override fun buildChatRequestBody(
        settings: AISettings,
        messages: List<ChatMessage>,
        stream: Boolean,
        tools: JsonArray?
    ): ChatRequest {
        val model = settings.modelName.ifBlank { ZhipuDefaultModel }
        // GLM-5.3 起始终开启思考，不接受 thinking.type="disabled"。
        // 用户关闭思考时改为发送 enabled + reasoning_effort="low"，以最低推理强度换取最快响应。
        val alwaysThinking = ALWAYS_THINKING_MODELS.any { model.startsWith(it) }
        val thinkingEnabled = alwaysThinking || settings.enableThinking

        return ChatRequest(
            model = model,
            messages = messages,
            temperature = 0.7,
            stream = stream,
            thinking = mapOf("type" to if (thinkingEnabled) "enabled" else "disabled"),
            reasoningEffort = if (alwaysThinking && !settings.enableThinking) "low" else null,
            tools = tools
        )
    }

    companion object {
        private const val ZhipuDefaultModel = "glm-5.3"

        /** 始终开启思考、不允许关闭的模型前缀。 */
        private val ALWAYS_THINKING_MODELS = listOf("glm-5.3")
    }
}
