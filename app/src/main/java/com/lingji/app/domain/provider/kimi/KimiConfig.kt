package com.lingji.app.domain.provider.kimi

import com.lingji.app.R
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object KimiConfig : ProviderConfig {
    const val MOONSHOT_URL = "https://api.moonshot.cn/v1"

    override val id = "KIMI"
    override val displayNameRes = R.string.provider_kimi
    override val defaultBaseUrl = MOONSHOT_URL
    override val defaultModelId = "kimi-k3.1-pro"
    override val models = listOf(
        ProviderModel(
            id = "kimi-k3.1-pro",
            name = "Kimi K3.1 Pro",
            description = "最新旗舰，1M 上下文，原生视觉，推理与 Agent 能力再升级",
            supportsVision = true
        ),
        ProviderModel(
            id = "kimi-k3.1-pro-highspeed",
            name = "Kimi K3.1 Pro Highspeed",
            description = "K3.1 Pro 高速版，约 2～3 倍速度，1M 上下文，原生视觉",
            supportsVision = true
        ),
        ProviderModel(
            id = "kimi-k3",
            name = "Kimi K3",
            description = "上一代旗舰，1M 上下文，原生视觉，编程与 Agent 能力",
            supportsVision = true
        )
    )
    /** K3 系列始终思考且不接受 thinking 字段，只通过 reasoning_effort 调节（low/high/max）。 */
    override val defaultThinkingLevels = listOf(
        ThinkingLevel.LOW,
        ThinkingLevel.HIGH,
        ThinkingLevel.MAX
    )
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}
