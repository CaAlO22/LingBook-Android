package com.lingji.app.domain.provider.openai

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object OpenAIConfig : ProviderConfig {
    override val id = "OPENAI"
    override val displayNameRes = R.string.provider_openai
    override val defaultBaseUrl = "https://api.openai.com/v1"
    override val defaultModelId = "gpt-4o"
    override val models = listOf(
        ProviderModel(
            id = "gpt-4o",
            name = "GPT-4o",
            description = "多模态旗舰模型"
        ),
        ProviderModel(
            id = "gpt-4o-mini",
            name = "GPT-4o Mini",
            description = "更快、更便宜"
        ),
        ProviderModel(
            id = "gpt-4-turbo",
            name = "GPT-4 Turbo",
            description = "长上下文、复杂任务"
        )
    )
    override val supportsThinking = false
    override val supportsThinkingField = false
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}
