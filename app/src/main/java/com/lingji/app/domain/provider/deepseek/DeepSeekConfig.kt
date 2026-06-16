package com.lingji.app.domain.provider.deepseek

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object DeepSeekConfig : ProviderConfig {
    const val DEEPSEEK_URL = "https://api.deepseek.com"

    override val id = "DEEPSEEK"
    override val displayNameRes = R.string.provider_deepseek
    override val defaultBaseUrl = DEEPSEEK_URL
    override val defaultModelId = "deepseek-v4-pro"
    override val models = listOf(
        ProviderModel(
            id = "deepseek-v4-pro",
            name = "DeepSeek-V4-Pro",
            description = "最新旗舰，1M 上下文，复杂推理与编程"
        ),
        ProviderModel(
            id = "deepseek-v4-flash",
            name = "DeepSeek-V4-Flash",
            description = "轻量快速，适合高频调用"
        )
    )
    override val supportsThinking = true
    override val supportsThinkingField = true
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}
