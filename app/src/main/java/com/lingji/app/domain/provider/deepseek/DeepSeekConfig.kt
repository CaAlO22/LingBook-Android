package com.lingji.app.domain.provider.deepseek

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object DeepSeekConfig : ProviderConfig {
    const val DEEPSEEK_URL = "https://api.deepseek.com"

    override val id = "DEEPSEEK"
    override val displayNameRes = R.string.provider_deepseek
    override val defaultBaseUrl = DEEPSEEK_URL
    override val defaultModelId = "deepseek-flash"
    override val models = listOf(
        ProviderModel(
            id = "deepseek-flash",
            name = "DeepSeek-V4.1-Flash",
            description = "最新旗舰，1M 上下文，原生多模态，性能与成本全面超越 V4-Pro",
            supportsVision = true
        )
    )
    override val supportsThinking = true
    override val supportsThinkingField = true
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}
