package com.lingji.app.domain.provider.hunyuan

import com.lingji.app.R
import com.lingji.app.domain.model.ThinkingLevel
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object HunyuanConfig : ProviderConfig {
    /** TokenHub（原混元平台已迁移至此），OpenAI 兼容接口。 */
    const val TOKENHUB_URL = "https://tokenhub.tencentmaas.com/v1"

    override val id = "HUNYUAN"
    override val displayNameRes = R.string.provider_hunyuan
    override val defaultBaseUrl = TOKENHUB_URL
    override val defaultModelId = "hy4"
    override val models = listOf(
        ProviderModel(
            id = "hy4",
            name = "Hy4",
            description = "最新旗舰，1M 上下文，Agent 与长程任务能力最强，支持图片理解",
            supportsVision = true
        ),
        ProviderModel(
            id = "hy4-flash",
            name = "Hy4 Flash",
            description = "轻量高效，1M 上下文，适合日常高频调用，支持图片理解",
            supportsVision = true
        ),
        ProviderModel(
            id = "hy3",
            name = "Hy3",
            description = "上一代主力模型，256K 上下文，效果与成本均衡",
            // Hy3 支持 low/high 两档强度
            thinkingLevels = listOf(ThinkingLevel.OFF, ThinkingLevel.LOW, ThinkingLevel.HIGH)
        )
    )
    /** Hy4 系列仅支持开启/关闭思考。 */
    override val defaultThinkingLevels = listOf(ThinkingLevel.OFF, ThinkingLevel.ON)
    override val authHeaderName = "Authorization"
    override val authHeaderPrefix = "Bearer "
}
