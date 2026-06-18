package com.lingji.app.domain.provider.openai

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object OpenAIConfig : ProviderConfig {
    override val id = "OPENAI"
    override val displayNameRes = R.string.provider_openai
    override val defaultBaseUrl = "https://api.openai.com/v1"
    override val defaultModelId = "gpt-5.4"
    override val models = listOf(
        ProviderModel(
            id = "gpt-5.4",
            name = "GPT-5.4",
            description = "多模态旗舰模型",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-5.5",
            name = "GPT-5.5",
            description = "更强多模态推理",
            supportsVision = true
        )
    )
    override val supportsThinking = false
    override val supportsThinkingField = false
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}
