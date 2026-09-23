package com.lingji.app.domain.provider.mimo

import com.lingji.app.R
import com.lingji.app.domain.provider.ProviderConfig
import com.lingji.app.domain.provider.ProviderModel

object MimoConfig : ProviderConfig {
    const val PAY_AS_YOU_GO_URL = "https://api.xiaomimimo.com/v1"
    const val TOKEN_PLAN_URL = "https://token-plan-cn.xiaomimimo.com/v1"

    override val id = "XIAOMI"
    override val displayNameRes = R.string.provider_mimo
    override val defaultBaseUrl = PAY_AS_YOU_GO_URL
    override val defaultModelId = "mimo-v2.6-pro"
    override val models = listOf(
        ProviderModel(
            id = "mimo-v2.6-pro",
            name = "MiMo-V2.6-Pro",
            description = "最新旗舰推理模型，全模态，面向长程任务与高价值工作",
            supportsVision = true
        ),
        ProviderModel(
            id = "mimo-v2.6-flash",
            name = "MiMo-V2.6-Flash",
            description = "全模态高效推理，高智能低成本，适合规模化调用",
            supportsVision = true
        ),
        ProviderModel(
            id = "mimo-v2.6-pro-ultraspeed",
            name = "MiMo-V2.6-Pro-UltraSpeed",
            description = "V2.6-Pro 旗舰性能，最高 20 倍推理速度，面向强实时场景",
            supportsVision = true
        )
    )
    override val supportsThinking = true
    override val supportsThinkingField = true
    override val authHeaderName = "api-key"
    override val authHeaderPrefix = ""
}
