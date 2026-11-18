package com.lingji.app.data.remote.strategy

import com.google.gson.JsonArray
import com.lingji.app.data.remote.models.ChatMessage
import com.lingji.app.data.remote.models.ChatRequest
import com.lingji.app.domain.model.AISettings
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderRegistry
import okhttp3.Request

/**
 * 阿里云百炼（DashScope）OpenAI 兼容接口策略。
 *
 * 百炼用 `enable_thinking` 控制开关，旗舰模型再用 `reasoning_effort` 调节强度
 * （low/medium/high/xhigh）；两者不能与 `thinking_budget` 同时使用，这里不使用 budget。
 */
class BailianRequestStrategy : RequestStrategy {

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
        enableThinking = settings.thinkingLevel != ThinkingLevel.OFF,
        reasoningEffort = settings.thinkingLevel.toEffort(),
        tools = tools
    )

    private fun ThinkingLevel.toEffort(): String? = when (this) {
        ThinkingLevel.OFF, ThinkingLevel.ON -> null
        ThinkingLevel.LOW -> "low"
        ThinkingLevel.MEDIUM -> "medium"
        ThinkingLevel.HIGH -> "high"
        ThinkingLevel.MAX -> "xhigh"
    }
}
