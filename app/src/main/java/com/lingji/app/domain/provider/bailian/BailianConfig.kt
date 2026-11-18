package com.lingji.app.domain.provider.bailian

import com.lingji.app.R
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object BailianConfig : ProviderConfig {
    const val DASHSCOPE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    override val id = "BAILIAN"
    override val displayNameRes = R.string.provider_bailian
    override val defaultBaseUrl = DASHSCOPE_URL
    override val defaultModelId = "qwen4-max"
    override val models = listOf(
        ProviderModel(
            id = "qwen4-max",
            name = "Qwen 4 Max",
            description = "最新旗舰，1M 上下文，推理、视觉与 Agent 能力全面升级",
            supportsVision = true,
            thinkingLevels = listOf(
                ThinkingLevel.OFF,
                ThinkingLevel.LOW,
                ThinkingLevel.MEDIUM,
                ThinkingLevel.HIGH,
                ThinkingLevel.MAX
            )
        ),
        ProviderModel(
            id = "qwen3.7-plus",
            name = "Qwen 3.7 Plus",
            description = "均衡之选，1M 上下文，文本、图像与视频理解，成本更优",
            supportsVision = true
        ),
        ProviderModel(
            id = "qwen3.8-flash",
            name = "Qwen 3.8 Flash",
            description = "成本优先，1M 上下文，适合高频规模化调用",
            supportsVision = true
        ),
        ProviderModel(
            id = "qwen3.8-max",
            name = "Qwen 3.8 Max",
            description = "上一代旗舰，1M 上下文，推理与视觉理解",
            supportsVision = true,
            thinkingLevels = listOf(
                ThinkingLevel.OFF,
                ThinkingLevel.LOW,
                ThinkingLevel.MEDIUM,
                ThinkingLevel.MAX
            )
        ),
        ProviderModel(
            id = "qwen-long",
            name = "Qwen Long",
            description = "1000 万上下文，适合长文档分析与总结",
            thinkingLevels = emptyList()
        )
    )
    /** 其余 Qwen 模型只支持开启/关闭思考。 */
    override val defaultThinkingLevels = listOf(ThinkingLevel.OFF, ThinkingLevel.ON)
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}
