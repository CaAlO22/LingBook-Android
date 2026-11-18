package com.lingji.app.domain.provider.volcano

import com.lingji.app.R
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object VolcanoConfig : ProviderConfig {
    const val ARK_URL = "https://ark.cn-beijing.volces.com/api/v3"

    override val id = "DOUBAO"
    override val displayNameRes = R.string.provider_volcano
    override val defaultBaseUrl = ARK_URL
    override val defaultModelId = "doubao-seed-2-1-pro-260915"
    override val models = listOf(
        ProviderModel(
            id = "doubao-seed-2-1-pro-260915",
            name = "Doubao Seed 2.1 Pro",
            description = "官方推荐旗舰，1M 上下文，Coding/Agent/多模态全面升级",
            supportsVision = true
        ),
        ProviderModel(
            id = "doubao-seed-2-1-lite-260915",
            name = "Doubao Seed 2.1 Lite",
            description = "全模态理解，1M 上下文，性能与成本均衡",
            supportsVision = true
        ),
        ProviderModel(
            id = "doubao-seed-evolving",
            name = "Doubao Seed Evolving",
            description = "周级迭代的 Coding & Agent 模型，1M 上下文，能力持续进化"
        ),
        ProviderModel(
            id = "doubao-seed-2-1-pro-260628",
            name = "Doubao Seed 2.1 Pro (0628)",
            description = "面向生产级任务，升级编程、智能体与多模态能力",
            supportsVision = true
        ),
        ProviderModel(
            id = "doubao-seed-2-1-turbo-260628",
            name = "Doubao Seed 2.1 Turbo",
            description = "效果与成本均衡，适合高频调用",
            supportsVision = true
        )
    )
    /** Seed 2.x 支持 thinking.type + reasoning_effort（minimal/low/medium/high）。 */
    override val defaultThinkingLevels = listOf(
        ThinkingLevel.OFF,
        ThinkingLevel.LOW,
        ThinkingLevel.MEDIUM,
        ThinkingLevel.HIGH
    )
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}
