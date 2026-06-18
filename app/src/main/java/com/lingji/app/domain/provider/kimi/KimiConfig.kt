package com.lingji.app.domain.provider.kimi

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object KimiConfig : ProviderConfig {
    const val MOONSHOT_URL = "https://api.moonshot.cn/v1"

    override val id = "KIMI"
    override val displayNameRes = R.string.provider_kimi
    override val defaultBaseUrl = MOONSHOT_URL
    override val defaultModelId = "kimi-k2.6"
    override val models = listOf(
        ProviderModel(
            id = "kimi-k2.6",
            name = "Kimi K2.6",
            description = "最新旗舰，256K 上下文，多模态与 Tool Calling",
            supportsVision = true
        ),
        ProviderModel(
            id = "kimi-k2.5",
            name = "Kimi K2.5",
            description = "上一代旗舰，长上下文"
        ),
        ProviderModel(
            id = "kimi-k2.7-code",
            name = "Kimi K2.7-Code",
            description = "代码专用模型"
        ),
        ProviderModel(
            id = "kimi-k2.7-code-highspeed",
            name = "Kimi K2.7-Code-HighSpeed",
            description = "代码专用高速版"
        ),
        ProviderModel(
            id = "moonshot-v1-8k",
            name = "Moonshot V1 8K",
            description = "短文本，上下文 8K"
        ),
        ProviderModel(
            id = "moonshot-v1-32k",
            name = "Moonshot V1 32K",
            description = "长文本，上下文 32K"
        ),
        ProviderModel(
            id = "moonshot-v1-128k",
            name = "Moonshot V1 128K",
            description = "超长文本，上下文 128K"
        ),
        ProviderModel(
            id = "moonshot-v1-8k-vision-preview",
            name = "Moonshot V1 8K Vision",
            description = "视觉模型，上下文 8K",
            supportsVision = true
        ),
        ProviderModel(
            id = "moonshot-v1-32k-vision-preview",
            name = "Moonshot V1 32K Vision",
            description = "视觉模型，上下文 32K",
            supportsVision = true
        ),
        ProviderModel(
            id = "moonshot-v1-128k-vision-preview",
            name = "Moonshot V1 128K Vision",
            description = "视觉模型，上下文 128K",
            supportsVision = true
        )
    )
    override val supportsThinking = true
    override val supportsThinkingField = true
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}
