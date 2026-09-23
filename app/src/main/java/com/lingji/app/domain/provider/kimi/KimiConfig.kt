package com.lingji.app.domain.provider.kimi

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object KimiConfig : ProviderConfig {
    const val MOONSHOT_URL = "https://api.moonshot.cn/v1"

    override val id = "KIMI"
    override val displayNameRes = R.string.provider_kimi
    override val defaultBaseUrl = MOONSHOT_URL
    override val defaultModelId = "kimi-k3"
    override val models = listOf(
        ProviderModel(
            id = "kimi-k3",
            name = "Kimi K3",
            description = "最新旗舰，1M 上下文，原生视觉，编程与 Agent 能力最强",
            supportsVision = true
        ),
        ProviderModel(
            id = "kimi-k2.7-code",
            name = "Kimi K2.7-Code",
            description = "代码专用模型，256K 上下文，长上下文指令遵循稳定"
        ),
        ProviderModel(
            id = "kimi-k2.7-code-highspeed",
            name = "Kimi K2.7-Code-HighSpeed",
            description = "代码专用高速版，约 180 tokens/s"
        ),
        ProviderModel(
            id = "kimi-k2.6",
            name = "Kimi K2.6",
            description = "通用多模态，256K 上下文，支持思考与非思考模式",
            supportsVision = true
        )
    )
    override val supportsThinking = true
    override val supportsThinkingField = true
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}
