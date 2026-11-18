package com.lingji.app.domain.provider.openai

import com.lingji.app.R
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object OpenAIConfig : ProviderConfig {
    override val id = "OPENAI"
    override val displayNameRes = R.string.provider_openai
    override val defaultBaseUrl = "https://api.openai.com/v1"
    override val defaultModelId = "gpt-6-astra"
    override val models = listOf(
        ProviderModel(
            id = "gpt-6-astra",
            name = "GPT-6 Astra",
            description = "最强旗舰，1.05M 上下文，复杂推理、编程、研究与文档创作",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-6.1-sol",
            name = "GPT-6.1 Sol",
            description = "接近 Astra 的智能，价格约为其五分之一，适合日常高价值工作",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-6.1-terra",
            name = "GPT-6.1 Terra",
            description = "快速高效，视觉推理与精细计算机操作，1.05M 上下文",
            supportsVision = true
        ),
        ProviderModel(
            id = "gpt-6-luna",
            name = "GPT-6 Luna",
            description = "成本最低，适合高并发、大批量的日常任务",
            supportsVision = true
        )
    )
    /** GPT-6 系列统一通过 reasoning_effort 调节思考强度，支持 none 关闭。 */
    override val defaultThinkingLevels = listOf(
        ThinkingLevel.OFF,
        ThinkingLevel.LOW,
        ThinkingLevel.MEDIUM,
        ThinkingLevel.HIGH,
        ThinkingLevel.MAX
    )
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}
