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
    override val defaultModelId = "mimo-v2.5-pro"
    override val models = listOf(
        ProviderModel(
            id = "mimo-v2.5-pro",
            name = "MiMo-V2.5-Pro",
            description = "复杂推理、长文档、深度分析"
        ),
        ProviderModel(
            id = "mimo-v2.5",
            name = "MiMo-V2.5",
            description = "全模态理解、图文音视频",
            supportsVision = true
        ),
        ProviderModel(
            id = "mimo-v2-flash",
            name = "MiMo-V2-Flash",
            description = "低成本、快速响应"
        )
    )
    override val supportsThinking = true
    override val supportsThinkingField = true
    override val authHeaderName = "api-key"
    override val authHeaderPrefix = ""
}
